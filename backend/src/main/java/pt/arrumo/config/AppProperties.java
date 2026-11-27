package pt.arrumo.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pt.arrumo.domain.Role;

/**
 * Settings from application.yml (the "app" section) or APP_* environment variables.
 *
 * @param encryptionKey      32 bytes in Base64 used to encrypt 2FA secrets and settings (APP_ENCRYPTION_KEY)
 * @param totpIssuer         name shown in the authenticator app
 * @param mfaRequired        roles that must turn on 2FA at their first login
 * @param adminUsername      admin created on the first start, if there are no users yet
 * @param adminPassword      temporary password for that admin (must be changed at first login)
 * @param maxAttempts        failed logins in a row before the account is locked
 * @param lockout            how long the account stays locked
 * @param maxSessionDuration after this time the user always has to sign in again, even if active
 * @param secureCookie       cookies only over HTTPS (turn off only for local development)
 */
@ConfigurationProperties("app")
public record AppProperties(
        String encryptionKey,
        String totpIssuer,
        List<Role> mfaRequired,
        String adminUsername,
        String adminPassword,
        int maxAttempts,
        Duration lockout,
        Duration maxSessionDuration,
        boolean secureCookie) {
}
