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
 * Ein AES-256-Schluessel, der aus einer gemeinsamen Passphrase (dem "Key", den du
 * deinen Freunden schickst) abgeleitet wird.
 */
public final class VoiceKey {

    private static final byte[] SALT = "voicechat-e2ee/v1/key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FINGERPRINT_TAG = "voicechat-e2ee/v1/fingerprint".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 150_000;
    // 32 Zeichen ohne verwechselbare (0/O, 1/I) -> 5 Bit pro Zeichen
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

    /** Leitet den Schluessel aus der Passphrase ab (PBKDF2-HMAC-SHA256, bewusst langsam). */
    public static VoiceKey fromPassphrase(String passphrase) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), SALT, ITERATIONS, 256);
        try {
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new VoiceKey(raw);
        } finally {
            spec.clearPassword();
        }
    }

    /** Erzeugt einen neuen zufaelligen Key, z. B. K7QW-M3HD-9XTA-PB2F-ZR4N-C8VE (120 Bit). */
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
     * Kurzer Abgleich-Code (z. B. A1B2-C3D4). Zwei Leute mit demselben Key sehen denselben Code,
     * verraten damit aber den Key nicht.
     */
    public String fingerprint() {
        return fingerprint;
    }
}
