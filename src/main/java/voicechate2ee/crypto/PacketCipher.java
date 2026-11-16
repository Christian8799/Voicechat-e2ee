package voicechate2ee.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Encrypts individual Opus packets with AES-256-GCM.
 *
 * <p>Format of the sealed packet: {@code [12 byte nonce][ciphertext][16 byte GCM tag]}.
 * The nonce consists of a random 4-byte session ID and an 8-byte counter, so it never repeats.
 * The GCM tag makes sure wrong keys and tampered packets are detected.
 */
public final class PacketCipher {

    private static final int NONCE_LEN = 12;
    private static final int TAG_BYTES = 16;
    private static final byte[] AAD = {'V', 'C', 'E', '1'};

    private final byte[] sessionId = new byte[4];
    private final AtomicLong counter = new AtomicLong();

    public PacketCipher() {
        new SecureRandom().nextBytes(sessionId);
    }

    public byte[] seal(byte[] plain, VoiceKey key) throws GeneralSecurityException {
        byte[] nonce = nextNonce();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key.aesKey(), new GCMParameterSpec(TAG_BYTES * 8, nonce));
        cipher.updateAAD(AAD);
        byte[] out = new byte[NONCE_LEN + plain.length + TAG_BYTES];
        System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
        cipher.doFinal(plain, 0, plain.length, out, NONCE_LEN);
        return out;
    }

    /** Decrypts a packet; returns {@code null} if the key is wrong or the packet is invalid. */
    public static byte[] open(byte[] sealed, VoiceKey key) {
        if (sealed == null || sealed.length < NONCE_LEN + TAG_BYTES + 1) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key.aesKey(), new GCMParameterSpec(TAG_BYTES * 8, sealed, 0, NONCE_LEN));
            cipher.updateAAD(AAD);
            return cipher.doFinal(sealed, NONCE_LEN, sealed.length - NONCE_LEN);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private byte[] nextNonce() {
        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(sessionId, 0, nonce, 0, sessionId.length);
        long ctr = counter.getAndIncrement();
        for (int i = 0; i < 8; i++) {
            nonce[4 + i] = (byte) (ctr >>> (56 - 8 * i));
        }
        return nonce;
    }
}
