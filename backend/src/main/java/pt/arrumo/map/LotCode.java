package pt.arrumo.map;

import java.util.Locale;
import java.util.regex.Pattern;

/** What came from the keyboard or the scanner: a lot code (L261121000009). */
public record LotCode(String lot) {

    /** Letters, digits and . _ / -, with at least one digit. "Partidas" or "Casa do Bacalhau" are plain text. */
    private static final Pattern LOT = Pattern.compile("^(?=.*\\d)[A-Za-z0-9._/-]{3,30}$");

    /** Returns the lot, or throws IllegalArgumentException if the text is not a lot code. */
    public static LotCode parse(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (!LOT.matcher(s).matches()) throw new IllegalArgumentException("Isto não parece um código de lote.");
        return new LotCode(s.toUpperCase(Locale.ROOT));
    }
}
