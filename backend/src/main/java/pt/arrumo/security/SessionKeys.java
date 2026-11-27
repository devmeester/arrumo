package pt.arrumo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import pt.arrumo.domain.AppUser;

/** Names of the HTTP session attributes, and the "stamp" that invalidates a session when the account changes. */
public final class SessionKeys {
    public static final String PENDING = "arrumo.pendingLogin";
    public static final String STAMP = "arrumo.stamp";
    public static final String START = "arrumo.start";
    public static final String NEW_MFA = "arrumo.newMfa";

    private SessionKeys() {}

    /**
     * Changes when the password, role, 2FA or account status change. SessionCheckFilter compares it on every
     * request: deactivating someone or resetting their password ends that person's sessions straight away.
     */
    public static String stamp(AppUser u) {
        String base = u.getPasswordHash() + "|" + u.getRole() + "|" + u.getMfaSecret() + "|" + u.isActive();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
