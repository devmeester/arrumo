package pt.arrumo.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.arrumo.domain.Role;
import pt.arrumo.domain.AppUser;
import pt.arrumo.domain.AppUserRepo;
import pt.arrumo.errors.ApiError;
import pt.arrumo.security.Audit;
import pt.arrumo.security.PasswordPolicy;

/** ADMIN only. There is no delete: users are deactivated, so the history still says who did what. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    public record UserDto(long id, String username, String name, String role, boolean active, boolean mfaEnabled,
                                boolean mustChangePassword, boolean locked, Instant lastLogin) {
        static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getName(), u.getRole().name(), u.isActive(), u.isMfaEnabled(),
                    u.isMustChangePassword(), u.locked(Instant.now()), u.getLastLogin());
        }
    }
    public record CreateRequest(@NotBlank @Pattern(regexp = "[a-z0-9._-]{3,40}", message = "3 a 40 letras minúsculas, números, . _ -") String username,
                             @NotBlank @Size(max = 100) String name, @NotNull Role role) {}
    public record UpdateRequest(@NotBlank @Size(max = 100) String name, @NotNull Role role, boolean active) {}
    /** The temporary password only appears in this response; it is never stored as plain text. */
    public record WithTemporaryPassword(UserDto user, String temporaryPassword) {}

    private final AppUserRepo repo;
    private final PasswordEncoder encoder;
    private final Audit audit;

    public UserController(AppUserRepo repo, PasswordEncoder encoder, Audit audit) {
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
    }

    @GetMapping
    public List<UserDto> list() {
        return repo.findAllByOrderByUsername().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @Transactional
    public WithTemporaryPassword create(@Valid @RequestBody CreateRequest p) {
        if (repo.findByUsername(p.username()).isPresent()) throw ApiError.conflict("Esse nome de utilizador já existe.");
        String temp = PasswordPolicy.temporary();
        AppUser u = repo.save(new AppUser(p.username(), p.name().strip(), encoder.encode(temp), p.role()));
        audit.write("USER_CREATED", u.getUsername() + " (" + u.getRole() + ")");
        return new WithTemporaryPassword(UserDto.from(u), temp);
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@PathVariable long id, @Valid @RequestBody UpdateRequest p, Principal me) {
        AppUser u = get(id);
        boolean myself = u.getUsername().equals(me.getName());
        if (myself && (!p.active() || p.role() != Role.ADMIN))
            throw ApiError.invalid("Não podes tirar a ti próprio o papel de admin nem desativar a tua conta.");
        if (u.getRole() == Role.ADMIN && (p.role() != Role.ADMIN || !p.active()) && repo.countByRoleAndActiveTrue(Role.ADMIN) <= 1)
            throw ApiError.invalid("Tem de ficar pelo menos um admin ativo.");
        String before = u.getRole() + (u.isActive() ? "" : " inactive");
        u.setName(p.name().strip());
        u.setRole(p.role());
        u.setActive(p.active());
        audit.write("USER_UPDATED", u.getUsername() + ": " + before + " -> " + u.getRole() + (u.isActive() ? "" : " inactive"));
        return UserDto.from(u);
    }

    /** Creates a new temporary password and ends that person's open sessions. */
    @PostMapping("/{id}/reset-password")
    @Transactional
    public WithTemporaryPassword resetPassword(@PathVariable long id) {
        AppUser u = get(id);
        String temp = PasswordPolicy.temporary();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setLockedUntil(null);
        u.setFailedAttempts(0);
        audit.write("PASSWORD_RESET", u.getUsername());
        return new WithTemporaryPassword(UserDto.from(u), temp);
    }

    /** For someone who lost or changed phone: they set up 2FA again at the next login. */
    @PostMapping("/{id}/reset-mfa")
    @Transactional
    public UserDto resetMfa(@PathVariable long id) {
        AppUser u = get(id);
        u.setMfaEnabled(false);
        u.setMfaSecret(null);
        u.setMfaLastStep(0);
        audit.write("MFA_RESET", u.getUsername());
        return UserDto.from(u);
    }

    @PostMapping("/{id}/unlock")
    @Transactional
    public UserDto unlock(@PathVariable long id) {
        AppUser u = get(id);
        u.setLockedUntil(null);
        u.setFailedAttempts(0);
        audit.write("USER_UNLOCKED", u.getUsername());
        return UserDto.from(u);
    }

    private AppUser get(long id) {
        return repo.findById(id).orElseThrow(() -> ApiError.notFound("Utilizador não encontrado."));
    }
}
