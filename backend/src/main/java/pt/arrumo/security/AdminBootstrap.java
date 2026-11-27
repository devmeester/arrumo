package pt.arrumo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.arrumo.config.AppProperties;
import pt.arrumo.domain.Role;
import pt.arrumo.domain.AppUser;
import pt.arrumo.domain.AppUserRepo;

/** The first time the app starts (no users in the database) it creates the initial administrator. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepo repo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminBootstrap(AppUserRepo repo, PasswordEncoder encoder, AppProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) return;
        String u = props.adminUsername(), p = props.adminPassword();
        if (u == null || u.isBlank() || p == null || p.isBlank()) {
            log.warn("There are no users. Set APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD and restart to create the admin.");
            return;
        }
        String problem = PasswordPolicy.problem(p, u);
        if (problem != null) {
            log.error("APP_ADMIN_PASSWORD rejected: {}", problem);
            return;
        }
        repo.save(new AppUser(u.trim().toLowerCase(), "Administrador", encoder.encode(p), Role.ADMIN));
        log.info("Initial admin '{}' created. At the first login they must change the password and turn on 2FA. "
                + "After that, remove APP_ADMIN_PASSWORD from .env.", u);
    }
}
