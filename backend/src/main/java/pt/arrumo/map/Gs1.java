package pt.arrumo.map;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads GS1 pallet labels (GS1-128 or DataMatrix), both the "raw" scanner text and the human-readable
 * "(00)...(01)..." form. Keeps what matters for finished goods:
 * (00) SSCC, (01)/(02) GTIN, (10) lot, (11) production, (15)/(17) best-before/expiry, (310n) net kg, (37) boxes.
 */
public record Gs1(String sscc, String gtin, String lot, LocalDate production, LocalDate expiryDate,
                  BigDecimal weightKg, Integer boxes) {

    private static final char GS = '\u001D';
    private static final Pattern IN_PARENTHESES = Pattern.compile("\\((\\d{2,4})\\)([^(]*)");

    /** true if the text looks like a GS1 label (and not a lot code or a note). */
    public static boolean looksLikeGs1(String raw) {
        if (raw == null) return false;
        String s = withoutPrefix(raw.strip());
        return s.indexOf(GS) >= 0 || s.matches("^\\((00|01|02)\\).*") || s.matches("^(00\\d{18}|0[12]\\d{14}).*");
    }

    /** Parses the label. Throws IllegalArgumentException with the reason (in Portuguese, shown to users) if it cannot. */
    public static Gs1 parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Leitura vazia.");
        String s = withoutPrefix(raw.strip());
        Fields c = new Fields();
        if (s.startsWith("(")) {
            Matcher m = IN_PARENTHESES.matcher(s);
            while (m.find()) c.put(m.group(1), m.group(2).strip());
        } else {
            parseRaw(s, c);
        }
        if (c.sscc == null && c.lot == null)
            throw new IllegalArgumentException("A etiqueta não tem SSCC (00) nem lote (10).");
        if (c.sscc != null && !c.sscc.matches("\\d{18}")) throw new IllegalArgumentException("SSCC inválido na etiqueta.");
        if (c.gtin != null && !validGtin(c.gtin)) c.gtin = null;
        return new Gs1(c.sscc, c.gtin, c.lot == null ? null : c.lot.toUpperCase(), c.production, c.expiryDate, c.weight, c.boxes);
    }

    private static void parseRaw(String s, Fields c) {
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == GS) { i++; continue; }
            if (i + 2 > s.length()) break;
            String ai = s.substring(i, i + 2);
            int fixed = switch (ai) {
                case "00" -> 18;
                case "01", "02" -> 14;
                case "11", "12", "13", "15", "16", "17" -> 6;
                case "20" -> 2;
                default -> 0;
            };
            if (fixed > 0) {
                int end = Math.min(s.length(), i + 2 + fixed);
                c.put(ai, s.substring(i + 2, end));
                i = end;
            } else if (ai.equals("10") || ai.equals("21") || ai.equals("30") || ai.equals("37")) {
                int end = s.indexOf(GS, i + 2);
                if (end < 0) end = s.length();
                c.put(ai, s.substring(i + 2, end));
                i = end;
            } else if (ai.matches("3[1-6]") && i + 10 <= s.length()) {
                c.put(s.substring(i, i + 4), s.substring(i + 4, i + 10));
                i += 10;
            } else {
                break; // an AI we do not need and of unknown length: keep what was read so far
            }
        }
    }

    private static final class Fields {
        String sscc, gtin, lot;
        LocalDate production, expiryDate;
        BigDecimal weight;
        Integer boxes;

        void put(String ai, String v) {
            switch (ai) {
                case "00" -> sscc = v;
                case "01", "02" -> gtin = v;
                case "10" -> lot = v;
                case "11" -> production = date(v);
                case "15", "17" -> { LocalDate d = date(v); if (d != null && (expiryDate == null || ai.equals("17"))) expiryDate = d; }
                case "37" -> boxes = v.matches("\\d{1,8}") ? Integer.valueOf(v) : null;
                default -> {
                    if (ai.matches("310\\d") && v.matches("\\d{6}")) weight = new BigDecimal(v).movePointLeft(ai.charAt(3) - '0');
                }
            }
        }
    }

    /** YYMMDD; day 00 = last day of the month (GS1 rule). */
    static LocalDate date(String v) {
        if (v == null || !v.matches("\\d{6}")) return null;
        int y = 2000 + Integer.parseInt(v.substring(0, 2)), m = Integer.parseInt(v.substring(2, 4)), d = Integer.parseInt(v.substring(4, 6));
        if (m < 1 || m > 12) return null;
        YearMonth ym = YearMonth.of(y, m);
        if (d == 0) return ym.atEndOfMonth();
        return d <= ym.lengthOfMonth() ? ym.atDay(d) : null;
    }

    static boolean validGtin(String g) {
        if (!g.matches("\\d{8}|\\d{12,14}")) return false;
        int sum = 0;
        for (int k = g.length() - 2, weight = 3; k >= 0; k--, weight = 4 - weight) sum += (g.charAt(k) - '0') * weight;
        return (10 - sum % 10) % 10 == g.charAt(g.length() - 1) - '0';
    }

    private static String withoutPrefix(String s) {
        // symbology prefix some scanners add: ]C1 (GS1-128), ]d2 (DataMatrix), ]Q3 (QR)
        return s.length() > 3 && s.charAt(0) == ']' ? s.substring(3) : s;
    }
}
