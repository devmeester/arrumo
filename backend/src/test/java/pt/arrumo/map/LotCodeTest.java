package pt.arrumo.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LotCodeTest {

    @Test
    void plainLot() {
        assertEquals("L261121000009", LotCode.parse(" l261121000009\n").lot());
    }

    @Test
    void textIsNotALot() {
        assertThrows(IllegalArgumentException.class, () -> LotCode.parse("Casa do Bacalhau"));
        assertThrows(IllegalArgumentException.class, () -> LotCode.parse("Partidas"));
        assertThrows(IllegalArgumentException.class, () -> LotCode.parse(""));
    }
}
