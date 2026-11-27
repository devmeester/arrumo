package pt.arrumo.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptorAndPasswordTest {

    @Test
    void encryptsAndDecrypts() {
        Encryptor c = new Encryptor(Base64.getEncoder().encodeToString(new byte[32]));
        String saved = c.encrypt("SEGREDO");
        assertNotEquals("SEGREDO", saved);
        assertEquals("SEGREDO", c.decrypt(saved));
    }

    @Test
    void wrongKeyFails() {
        String saved = new Encryptor(Base64.getEncoder().encodeToString(new byte[32])).encrypt("x");
        byte[] other = new byte[32];
        other[0] = 1;
        assertThrows(IllegalStateException.class, () -> new Encryptor(Base64.getEncoder().encodeToString(other)).decrypt(saved));
    }

    @Test
    void passwordRules() {
        assertNotNull(PasswordPolicy.problem("curta", "kaina"));
        assertNotNull(PasswordPolicy.problem("kaina-bacalhau-2026", "kaina"));
        assertNotNull(PasswordPolicy.problem("aaaaaaaaaaab", "x"));
        assertNull(PasswordPolicy.problem("cavalo-azul-frigorifico", "kaina"));
        assertNull(PasswordPolicy.problem(PasswordPolicy.temporary(), "kaina"));
    }
}
