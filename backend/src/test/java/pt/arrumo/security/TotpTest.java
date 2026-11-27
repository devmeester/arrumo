package pt.arrumo.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TotpTest {
    // RFC 6238 test vectors (ASCII secret "12345678901234567890")
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void acceptsRfcCode() {
        assertEquals(1, Totp.verify(SECRET, "287082", 59, 0));
        assertEquals(37037036, Totp.verify(SECRET, "081804", 1111111109, 0));
    }

    @Test
    void rejectsReusedOrWrongCode() {
        assertEquals(-1, Totp.verify(SECRET, "081804", 1111111109, 37037036));
        assertEquals(-1, Totp.verify(SECRET, "123456", 59, 0));
        assertEquals(-1, Totp.verify(SECRET, "abc", 59, 0));
    }

    @Test
    void newSecretHas160Bits() {
        assertEquals(32, Totp.newSecret().length());
    }
}
