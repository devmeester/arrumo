package pt.arrumo.security;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/** Password rules in line with NIST SP 800-63B: length matters more than symbols. */
public final class PasswordPolicy {

    public static final int MIN = 12;
    public static final int MAX = 64; // BCrypt only uses the first 72 bytes

    private static final Set<String> COMMON = Set.of(
            "123456789012", "password1234", "qwertyuiopas", "palavrapasse", "palavra-passe",
            "bacalhau1234", "caxamar12345", "arrumo123456", "123456789abc", "passwordpassword",
            "aaaaaaaaaaaa", "111111111111", "000000000000", "portugal1234", "benfica12345",
            "sporting1234", "porto1234567", "iloveyou1234", "admin1234567", "administrador");

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {}

    /** Returns null if the password is acceptable, otherwise the reason (in Portuguese, shown to the user). */
    public static String problem(String password, String username) {
        if (password == null || password.length() < MIN) return "A palavra-passe precisa de pelo menos " + MIN + " caracteres.";
        if (password.length() > MAX) return "A palavra-passe pode ter no máximo " + MAX + " caracteres.";
        String p = password.toLowerCase(Locale.ROOT);
        if (username != null && !username.isBlank() && p.contains(username.toLowerCase(Locale.ROOT)))
            return "A palavra-passe não pode conter o nome de utilizador.";
        if (COMMON.contains(p)) return "Essa palavra-passe é demasiado comum.";
        if (p.chars().distinct().count() < 5) return "A palavra-passe tem caracteres repetidos a mais.";
        return null;
    }

    /** Readable temporary password (no 0/O, 1/l/I) for the admin to hand over in person. */
    public static String temporary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
