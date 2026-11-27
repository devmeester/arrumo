package pt.arrumo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.arrumo.domain.AppUserRepo;

/**
 * On every API request, checks that the account is still active and unchanged since login,
 * and that the session has not passed its maximum duration. Otherwise it ends the session (401).
 * Deliberately not a @Component: it must only run inside the Spring Security chain.
 */
public class SessionCheckFilter extends OncePerRequestFilter {

    private final AppUserRepo users;
    private final Duration maxDuration;

    public SessionCheckFilter(AppUserRepo users, Duration maxDuration) {
        this.users = users;
        this.maxDuration = maxDuration;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) {
            HttpSession s = req.getSession(false);
            Object stamp = s == null ? null : s.getAttribute(SessionKeys.STAMP);
            Object start = s == null ? null : s.getAttribute(SessionKeys.START);
            boolean valid = stamp != null
                    && start instanceof Instant i && i.plus(maxDuration).isAfter(Instant.now())
                    && users.findByUsername(a.getName())
                            .filter(u -> u.isActive() && SessionKeys.stamp(u).equals(stamp))
                            .isPresent();
            if (!valid) {
                if (s != null) s.invalidate();
                SecurityContextHolder.clearContext();
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"A sessão terminou. Entra de novo.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
