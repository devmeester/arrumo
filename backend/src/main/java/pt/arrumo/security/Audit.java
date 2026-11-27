package pt.arrumo.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pt.arrumo.domain.AuditEntryRepo;
import pt.arrumo.domain.AuditEntry;

@Service
public class Audit {

    private final AuditEntryRepo repo;

    public Audit(AuditEntryRepo repo) { this.repo = repo; }

    /** Writes an entry for the user of the current session. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String action, String detail) {
        writeAs(currentUser(), action, detail);
    }

    /** In its own transaction: a failed login is recorded even when the request ends in an error. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAs(String username, String action, String detail) {
        repo.save(new AuditEntry(username, currentIp(), action, detail));
    }

    public static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken) return null;
        return a.getName();
    }

    public static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s) {
            HttpServletRequest r = s.getRequest();
            return r.getRemoteAddr(); // with server.forward-headers-strategy=native this is already the real IP, passed on by Caddy
        }
        return null;
    }
}
