package pt.arrumo.packing;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.arrumo.domain.Setting;
import pt.arrumo.domain.SettingRepo;
import pt.arrumo.errors.ApiError;
import pt.arrumo.security.Audit;
import pt.arrumo.security.Encryptor;

/**
 * Power Automate flow URLs. The admin pastes them in the app and they are stored encrypted in the database.
 * If nothing is set in the app, the environment variables (APP_PACKING_WEBHOOK_*) are used.
 */
@Service
public class Webhooks {
    public static final String DAILY = "packing.webhook.daily";
    public static final String FINISHED = "packing.webhook.finished";

    /** Status for the screen: never returns the whole URL, only its tail, so the admin can recognise it. */
    public record Info(boolean defined, String origin, String tail, Instant updatedAt, String updatedBy) {}
    public record Status(Info daily, Info finished) {}

    private final SettingRepo repo;
    private final Encryptor encryptor;
    private final PackingProperties props;
    private final Audit audit;

    public Webhooks(SettingRepo repo, Encryptor encryptor, PackingProperties props, Audit audit) {
        this.repo = repo;
        this.encryptor = encryptor;
        this.props = props;
        this.audit = audit;
    }

    public String daily() { return value(DAILY, props.webhookDaily()); }

    public String finished() { return value(FINISHED, props.webhookFinished()); }

    private String value(String key, String fromServer) {
        return repo.findById(key).map(c -> encryptor.decrypt(c.getEncryptedValue())).orElse(blank(fromServer) ? null : fromServer);
    }

    @Transactional(readOnly = true)
    public Status status() {
        return new Status(info(DAILY, props.webhookDaily()), info(FINISHED, props.webhookFinished()));
    }

    private Info info(String key, String fromServer) {
        var c = repo.findById(key).orElse(null);
        if (c != null) return new Info(true, "APP", tail(encryptor.decrypt(c.getEncryptedValue())), c.getUpdatedAt(), c.getUpdatedBy());
        if (!blank(fromServer)) return new Info(true, "SERVER", tail(fromServer), null, null);
        return new Info(false, null, null, null, null);
    }

    /** Blank deletes it (falling back to the server variable, if any). */
    @Transactional
    public void set(String key, String url, String who) {
        if (url == null || url.isBlank()) {
            repo.deleteById(key);
            audit.write("WEBHOOK_CONFIGURED", key + " deleted");
            return;
        }
        String u = url.strip();
        validate(u);
        Setting c = repo.findById(key).orElseGet(() -> new Setting(key));
        c.set(encryptor.encrypt(u), who);
        repo.save(c);
        audit.write("WEBHOOK_CONFIGURED", key + " set (…" + tail(u) + ")");
    }

    /** HTTPS only and only to Microsoft (Power Automate / Logic Apps): the app never sends data anywhere else. */
    static void validate(String u) {
        if (u.length() > 2000) throw ApiError.invalid("O URL é demasiado comprido.");
        URI uri;
        try { uri = URI.create(u); } catch (IllegalArgumentException e) { throw ApiError.invalid("Isto não é um URL válido."); }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !(host.endsWith(".powerplatform.com") || host.endsWith(".logic.azure.com")))
            throw ApiError.invalid("Tem de ser o URL HTTPS de um fluxo do Power Automate (…powerplatform.com ou …logic.azure.com).");
    }

    private static String tail(String u) { return u == null || u.length() < 6 ? "" : u.substring(u.length() - 6); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
