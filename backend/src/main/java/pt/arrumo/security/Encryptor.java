package pt.arrumo.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM to store secrets (2FA secrets, webhook URLs) encrypted in the database.
 * Someone who steals a copy of the database cannot use them without the key,
 * which only lives in the server's .env file.
 */
public final class Encryptor {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKey key;

    public Encryptor(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY is missing (32 bytes in Base64). Generate one with: openssl rand -base64 32");
        }
        byte[] k = Base64.getDecoder().decode(keyBase64.trim());
        if (k.length != 32) throw new IllegalStateException("APP_ENCRYPTION_KEY must be 32 bytes (256 bits)");
        this.key = new SecretKeySpec(k, "AES");
    }

    public String encrypt(String text) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String saved) {
        if (saved == null || !saved.startsWith("v1:")) throw new IllegalArgumentException("Unknown format");
        try {
            byte[] everything = Base64.getDecoder().decode(saved.substring(3));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, everything, 0, 12));
            return new String(c.doFinal(everything, 12, everything.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt (wrong key?)", e);
        }
    }
}
