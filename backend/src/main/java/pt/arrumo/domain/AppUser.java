package pt.arrumo.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String username;
    private String name;
    @Column(name = "password_hash") private String passwordHash;
    @Enumerated(EnumType.STRING) private Role role;
    private boolean active = true;
    @Column(name = "must_change_password") private boolean mustChangePassword = true;
    @Column(name = "mfa_enabled") private boolean mfaEnabled;
    @Column(name = "mfa_secret") private String mfaSecret;
    @Column(name = "mfa_last_step") private long mfaLastStep;
    @Column(name = "failed_attempts") private int failedAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "last_login") private Instant lastLogin;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Version private long version;

    protected AppUser() {}

    public AppUser(String username, String name, String passwordHash, Role role) {
        this.username = username;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public boolean locked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean v) { this.mfaEnabled = v; }
    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String s) { this.mfaSecret = s; }
    public long getMfaLastStep() { return mfaLastStep; }
    public void setMfaLastStep(long p) { this.mfaLastStep = p; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int n) { this.failedAttempts = n; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant t) { this.lockedUntil = t; }
    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant t) { this.lastLogin = t; }
    public Instant getCreatedAt() { return createdAt; }
}
