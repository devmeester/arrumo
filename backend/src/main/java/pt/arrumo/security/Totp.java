package pt.arrumo.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 6-digit codes that change every 30 seconds (RFC 6238), compatible with
 * Google Authenticator, Microsoft Authenticator, Aegis, 1Password, etc.
 */
public final class Totp {

    private static final int PERIOD = 30;
    private static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** New 160-bit secret in Base32. */
    public static String newSecret() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    public static String uri(String issuer, String account, String secret) {
        String e = enc(issuer);
        return "otpauth://totp/" + e + ":" + enc(account) + "?secret=" + secret + "&issuer=" + e
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD;
    }

    public static long currentStep(long epochSeconds) {
        return Math.floorDiv(epochSeconds, PERIOD);
    }

    /**
     * Checks the code allowing one step of drift (clocks that are slightly off).
     * Returns the step that matched, or -1 if invalid or already used (step <= lastUsedStep).
     */
    public static long verify(String secret, String code, long epochSeconds, long lastUsedStep) {
        if (code == null) return -1;
        String c = code.replace(" ", "");
        if (!c.matches("\\d{" + DIGITS + "}")) return -1;
        byte[] key = base32Decode(secret);
        long now = currentStep(epochSeconds);
        for (long step = now - 1; step <= now + 1; step++) {
            if (step <= lastUsedStep) continue;
            String expected = generate(key, step);
            if (MessageDigest.isEqual(expected.getBytes(), c.getBytes())) return step;
        }
        return -1;
    }

    static String generate(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", bin % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] payload) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : payload) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String t = s.toUpperCase().replace("=", "").replace(" ", "");
        ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char ch : t.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("Invalid secret");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
