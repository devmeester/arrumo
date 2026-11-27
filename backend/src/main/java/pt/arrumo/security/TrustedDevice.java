package pt.arrumo.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import pt.arrumo.config.AppProperties;
import pt.arrumo.domain.AppUser;

/**
 * "Don't ask for the code on this computer for 30 days".
 * A signed cookie (HMAC-SHA256) with the user id and the expiry. The signature includes the account stamp,
 * so it stops working by itself if the password, 2FA, role or account status change.
 * The password is always asked for.
 */
@Component
public class TrustedDevice {

    public static final Duration VALIDITY = Duration.ofDays(30);
    private static final String PREFIX = "arrumo_trusted_";

    private final byte[] key;
    private final boolean secure;

    public TrustedDevice(AppProperties props) {
        // its own key, derived from APP_ENCRYPTION_KEY (never use the same key for two things)
        this.key = hmac(Base64.getDecoder().decode(props.encryptionKey().trim()), "arrumo-trusted-device");
        this.secure = props.secureCookie();
    }

    public void remember(AppUser u, HttpServletResponse res) {
        String payload = u.getId() + "." + Instant.now().plus(VALIDITY).getEpochSecond();
        ResponseCookie c = ResponseCookie.from(PREFIX + u.getId(), payload + "." + sign(payload, u))
                .httpOnly(true).secure(secure).sameSite("Strict").path("/api/auth").maxAge(VALIDITY).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    public boolean trusted(AppUser u, HttpServletRequest req) {
        if (req.getCookies() == null) return false;
        for (Cookie c : req.getCookies()) {
            if (!c.getName().equals(PREFIX + u.getId())) continue;
            String[] p = c.getValue().split("\\.");
            if (p.length != 3 || !p[0].equals(String.valueOf(u.getId()))) return false;
            long expires;
            try {
                expires = Long.parseLong(p[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (expires < Instant.now().getEpochSecond()) return false;
            String expected = sign(p[0] + "." + p[1], u);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), p[2].getBytes(StandardCharsets.US_ASCII));
        }
        return false;
    }

    private String sign(String payload, AppUser u) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, payload + "|" + SessionKeys.stamp(u)));
    }

    private static byte[] hmac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
