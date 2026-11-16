package voicechate2ee.crypto;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * An AES-256 key derived from a shared passphrase (the "key" you send to
 * your friends).
 */
public final class VoiceKey {

    private static final byte[] SALT = "voicechat-e2ee/v1/key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FINGERPRINT_TAG = "voicechat-e2ee/v1/fingerprint".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 150_000;
    // 32 characters without easily confused ones (0/O, 1/I) -> 5 bits per character
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecretKey aesKey;
    private final String fingerprint;

    private VoiceKey(byte[] raw) throws GeneralSecurityException {
        this.aesKey = new SecretKeySpec(raw, "AES");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(FINGERPRINT_TAG);
        sha.update(raw);
        byte[] d = sha.digest();
        this.fingerprint = String.format("%02X%02X-%02X%02X", d[0], d[1], d[2], d[3]);
    }

    /** Derives the key from the passphrase (PBKDF2-HMAC-SHA256, deliberately slow). */
    public static VoiceKey fromPassphrase(String passphrase) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), SALT, ITERATIONS, 256);
        try {
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new VoiceKey(raw);
        } finally {
            spec.clearPassword();
        }
    }

    /** Generates a new random key, e.g. K7QW-M3HD-9XTA-PB2F-ZR4N-C8VE (120 bits). */
    public static String generatePassphrase() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public SecretKey aesKey() {
        return aesKey;
    }

    /**
     * Short comparison code (e.g. A1B2-C3D4). Two people with the same key see the same code,
     * but it does not reveal the key.
     */
    public String fingerprint() {
        return fingerprint;
    }
}
