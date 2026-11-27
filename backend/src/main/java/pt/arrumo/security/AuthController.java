package pt.arrumo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.arrumo.config.AppProperties;
import pt.arrumo.domain.AppUser;
import pt.arrumo.domain.AppUserRepo;
import pt.arrumo.errors.ApiError;

/**
 * Login in steps. Every response says what comes next:
 *   MFA             -> asks for the 6-digit code
 *   CHANGE_PASSWORD -> temporary password, a new one must be chosen
 *   SETUP_MFA       -> the role requires 2FA and it is not on yet
 *   OK              -> session created
 * Until it reaches OK, the session only holds a PendingLogin and gives access to nothing.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank @Size(max = 40) String username, @NotBlank @Size(max = 200) String password) {}
    public record CodeRequest(@NotBlank @Size(max = 10) String code, boolean remember) {}
    public record PasswordRequest(@Size(max = 200) String current, @NotBlank @Size(max = 200) String newPassword) {}
    public record Me(String username, String name, String role, boolean mfaEnabled) {
        static Me from(AppUser u) { return new Me(u.getUsername(), u.getName(), u.getRole().name(), u.isMfaEnabled()); }
    }
    public record Step(String step, Me me) {}
    public record NewMfa(String secret, String uri) {}

    private static final String LOGIN_MESSAGE = "Utilizador ou palavra-passe errados, ou conta bloqueada temporariamente.";
    /** Hash of a random password: checked when the user does not exist, so the response takes the same time. */
    private final String dummyHash;

    private final AppUserRepo users;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository context;
    private final LoginThrottle limit;
    private final Audit audit;
    private final Encryptor encryptor;
    private final AppProperties props;
    private final TrustedDevice device;

    public AuthController(AppUserRepo users, PasswordEncoder encoder, SecurityContextRepository context,
                          LoginThrottle limit, Audit audit, Encryptor encryptor, AppProperties props,
                          TrustedDevice device) {
        this.device = device;
        this.users = users;
        this.encoder = encoder;
        this.context = context;
        this.limit = limit;
        this.audit = audit;
        this.encryptor = encryptor;
        this.props = props;
        this.dummyHash = encoder.encode(PasswordPolicy.temporary());
    }

    /** Makes sure the browser has the XSRF-TOKEN cookie before its first POST. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Me me(Authentication a) {
        return Me.from(users.findByUsername(a.getName()).orElseThrow(() -> ApiError.unauthenticated("Entra de novo.")));
    }

    @PostMapping("/login")
    @Transactional(noRollbackFor = ApiError.class)
    public Step login(@Valid @RequestBody LoginRequest p, HttpServletRequest req, HttpServletResponse res) {
        String ip = req.getRemoteAddr();
        if (limit.locked(ip))
            throw new ApiError(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas tentativas a partir deste computador. Espera 15 minutos.");

        String username = p.username().strip().toLowerCase(Locale.ROOT);
        AppUser u = users.findByUsername(username).orElse(null);
        Instant now = Instant.now();

        if (u == null || !u.isActive()) {
            encoder.matches(p.password(), dummyHash);
            throw failed(ip, username, null, "unknown or inactive user");
        }
        if (u.locked(now)) {
            encoder.matches(p.password(), dummyHash);
            throw failed(ip, username, null, "account locked");
        }
        if (!encoder.matches(p.password(), u.getPasswordHash())) {
            throw failed(ip, username, u, "wrong password");
        }

        u.setFailedAttempts(0);
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate(); // new session: nothing from the old one (or another user) carries over
        PendingLogin pending = new PendingLogin(u.getId());
        req.getSession().setAttribute(SessionKeys.PENDING, pending);
        return next(u, pending, req, res);
    }

    @PostMapping("/mfa")
    @Transactional(noRollbackFor = ApiError.class)
    public Step mfa(@Valid @RequestBody CodeRequest p, HttpServletRequest req, HttpServletResponse res) {
        PendingLogin pending = pending(req);
        AppUser u = users.findById(pending.userId()).orElseThrow(() -> ApiError.unauthenticated("Entra de novo."));
        if (!u.isMfaEnabled()) throw ApiError.invalid("Esta conta não tem 2FA ativo.");
        if (u.locked(Instant.now())) throw failed(req.getRemoteAddr(), u.getUsername(), null, "account locked (2FA)");
        long step = Totp.verify(encryptor.decrypt(u.getMfaSecret()), p.code(), Instant.now().getEpochSecond(), u.getMfaLastStep());
        if (step < 0) throw failed(req.getRemoteAddr(), u.getUsername(), u, "wrong 2FA code");
        u.setMfaLastStep(step);
        u.setFailedAttempts(0);
        pending.markMfaDone();
        if (p.remember()) pending.rememberDevice();
        req.getSession().setAttribute(SessionKeys.PENDING, pending);
        return next(u, pending, req, res);
    }

    /** Mid-login (2FA required) or with a session already (an operator who decides to turn it on). */
    @PostMapping("/mfa/start")
    public NewMfa startMfa(HttpServletRequest req) {
        AppUser u = whoIsLoggingIn(req);
        if (u.isMfaEnabled()) throw ApiError.conflict("O 2FA já está ativo. Pede a um admin para o repor se trocaste de telemóvel.");
        String secret = Totp.newSecret();
        req.getSession().setAttribute(SessionKeys.NEW_MFA, secret);
        return new NewMfa(secret, Totp.uri(props.totpIssuer(), u.getUsername(), secret));
    }

    @PostMapping("/mfa/confirm")
    @Transactional
    public Step confirmMfa(@Valid @RequestBody CodeRequest p, HttpServletRequest req, HttpServletResponse res) {
        AppUser u = whoIsLoggingIn(req);
        HttpSession s = req.getSession();
        String secret = (String) s.getAttribute(SessionKeys.NEW_MFA);
        if (secret == null) throw ApiError.invalid("Começa de novo a configuração do 2FA.");
        long step = Totp.verify(secret, p.code(), Instant.now().getEpochSecond(), 0);
        if (step < 0) throw ApiError.invalid("Código errado. Confirma que a hora do telemóvel está certa e tenta o código seguinte.");
        u.setMfaSecret(encryptor.encrypt(secret));
        u.setMfaEnabled(true);
        u.setMfaLastStep(step);
        s.removeAttribute(SessionKeys.NEW_MFA);
        audit.writeAs(u.getUsername(), "MFA_ENABLED", "");
        PendingLogin pending = (PendingLogin) s.getAttribute(SessionKeys.PENDING);
        if (pending != null) {
            pending.markMfaDone();
            if (p.remember()) pending.rememberDevice();
            return next(u, pending, req, res);
        }
        s.setAttribute(SessionKeys.STAMP, SessionKeys.stamp(u)); // the secret changed: refresh this session's stamp
        return new Step("OK", Me.from(u));
    }

    @PostMapping("/password")
    @Transactional
    public Step changePassword(@Valid @RequestBody PasswordRequest p, HttpServletRequest req, HttpServletResponse res) {
        AppUser u = whoIsLoggingIn(req);
        PendingLogin pending = (PendingLogin) req.getSession().getAttribute(SessionKeys.PENDING);
        boolean midLogin = pending != null;
        if (!midLogin && (p.current() == null || !encoder.matches(p.current(), u.getPasswordHash())))
            throw ApiError.invalid("A palavra-passe atual está errada.");
        if (midLogin && (!u.isMustChangePassword() || (u.isMfaEnabled() && !pending.mfaDone())))
            throw ApiError.invalid("Termina primeiro o login.");
        String problem = PasswordPolicy.problem(p.newPassword(), u.getUsername());
        if (problem != null) throw ApiError.invalid(problem);
        if (encoder.matches(p.newPassword(), u.getPasswordHash())) throw ApiError.invalid("Escolhe uma palavra-passe diferente da anterior.");
        u.setPasswordHash(encoder.encode(p.newPassword()));
        u.setMustChangePassword(false);
        audit.writeAs(u.getUsername(), "PASSWORD_CHANGED", "");
        if (midLogin) return next(u, pending, req, res);
        req.getSession().setAttribute(SessionKeys.STAMP, SessionKeys.stamp(u));
        return new Step("OK", Me.from(u));
    }

    // ----------------------------------------------------------------------------------------------

    private Step next(AppUser u, PendingLogin pending, HttpServletRequest req, HttpServletResponse res) {
        if (u.isMfaEnabled() && !pending.mfaDone()) {
            if (!device.trusted(u, req)) return new Step("MFA", null);
            pending.markMfaDone(); // computer marked as trusted less than 30 days ago
            audit.writeAs(u.getUsername(), "MFA_TRUSTED_DEVICE", "");
        }
        if (u.isMustChangePassword()) return new Step("CHANGE_PASSWORD", null);
        if (!u.isMfaEnabled() && props.mfaRequired().contains(u.getRole())) return new Step("SETUP_MFA", null);
        finish(u, req, res);
        // after everything else (the password may have changed during this login): the cookie is signed with the final state
        if (pending.remember()) device.remember(u, res);
        return new Step("OK", Me.from(u));
    }

    private void finish(AppUser u, HttpServletRequest req, HttpServletResponse res) {
        req.changeSessionId();
        HttpSession s = req.getSession();
        s.removeAttribute(SessionKeys.PENDING);
        s.setAttribute(SessionKeys.STAMP, SessionKeys.stamp(u));
        s.setAttribute(SessionKeys.START, Instant.now());
        var authorities = u.getRole().authorities().stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContext ctx = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getUsername(), null, authorities));
        SecurityContextHolder.getContextHolderStrategy().setContext(ctx);
        context.saveContext(ctx, req, res);
        u.setLastLogin(Instant.now());
        audit.writeAs(u.getUsername(), "LOGIN", "");
    }

    /** Counts a failure (for the IP and, if it exists, the account) and always answers with the same generic message. */
    private ApiError failed(String ip, String username, AppUser u, String reason) {
        limit.failed(ip);
        if (u != null) {
            int n = u.getFailedAttempts() + 1;
            if (n >= props.maxAttempts()) {
                u.setLockedUntil(Instant.now().plus(props.lockout()));
                u.setFailedAttempts(0);
                audit.writeAs(username, "ACCOUNT_LOCKED", reason);
            } else {
                u.setFailedAttempts(n);
            }
        }
        audit.writeAs(username, "LOGIN_FAILED", reason);
        return ApiError.unauthenticated(LOGIN_MESSAGE);
    }

    private PendingLogin pending(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        PendingLogin p = s == null ? null : (PendingLogin) s.getAttribute(SessionKeys.PENDING);
        if (p == null || p.expired()) {
            if (s != null) s.removeAttribute(SessionKeys.PENDING);
            throw ApiError.unauthenticated("O login expirou. Entra de novo.");
        }
        return p;
    }

    /** The user of the half-finished login or, once signed in, the session's user. */
    private AppUser whoIsLoggingIn(HttpServletRequest req) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        HttpSession s = req.getSession(false);
        if (s != null && s.getAttribute(SessionKeys.PENDING) != null) {
            return users.findById(pending(req).userId()).orElseThrow(() -> ApiError.unauthenticated("Entra de novo."));
        }
        if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) {
            return users.findByUsername(a.getName()).orElseThrow(() -> ApiError.unauthenticated("Entra de novo."));
        }
        throw ApiError.unauthenticated("Entra de novo.");
    }
}
