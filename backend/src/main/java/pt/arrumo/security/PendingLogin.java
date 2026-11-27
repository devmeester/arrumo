package pt.arrumo.security;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/** Half-finished login: the password was accepted, but the 2FA code, a password change or the 2FA setup is still missing. */
public class PendingLogin implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Duration VALIDITY = Duration.ofMinutes(10);

    private final long userId;
    private final Instant start = Instant.now();
    private boolean mfaDone;
    private boolean remember;

    public PendingLogin(long userId) { this.userId = userId; }

    public long userId() { return userId; }
    public boolean expired() { return start.plus(VALIDITY).isBefore(Instant.now()); }
    public boolean mfaDone() { return mfaDone; }
    public void markMfaDone() { this.mfaDone = true; }
    public boolean remember() { return remember; }
    public void rememberDevice() { this.remember = true; }
}
