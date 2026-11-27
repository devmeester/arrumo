package pt.arrumo.map;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Gs1Test {
    private static final String GS = "\u001D";

    @Test
    void rawPalletLabel() {
        Gs1 e = Gs1.parse("]C1" + "00356012345000000017" + GS + "0205601234567892" + "1527063" + "0" + "3101012000" + "37" + "60" + GS + "10L261121000009");
        assertEquals("356012345000000017", e.sscc());
        assertEquals("05601234567892", e.gtin());
        assertEquals(LocalDate.of(2027, 6, 30), e.expiryDate());
        assertEquals(0, new BigDecimal("1200").compareTo(e.weightKg()));
        assertEquals(Integer.valueOf(60), e.boxes());
        assertEquals("L261121000009", e.lot());
    }

    @Test
    void humanReadableLabelInParentheses() {
        Gs1 e = Gs1.parse("(00)356012345000000017(02)05601234567892(11)261121(17)270531(37)60(10)L261121000009");
        assertEquals(LocalDate.of(2026, 11, 21), e.production());
        assertEquals(LocalDate.of(2027, 5, 31), e.expiryDate());
        assertEquals(Integer.valueOf(60), e.boxes());
    }

    @Test
    void dayZeroIsLastDayOfMonth() {
        assertEquals(LocalDate.of(2027, 2, 28), Gs1.date("270200"));
    }

    @Test
    void recognisesGs1() {
        assertTrue(Gs1.looksLikeGs1("00356012345000000017"));
        assertTrue(Gs1.looksLikeGs1("(00)356012345000000017"));
        assertFalse(Gs1.looksLikeGs1("L261121000009"));
        assertFalse(Gs1.looksLikeGs1("Casa do Bacalhau"));
        assertThrows(IllegalArgumentException.class, () -> Gs1.parse("(01)05601234567892(15)271231"));
    }
}
