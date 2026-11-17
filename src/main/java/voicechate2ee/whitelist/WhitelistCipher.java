package voicechate2ee.whitelist;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * Encrypts individual Opus packets in whitelist mode (AES-256-GCM).
 *
 * <p>Same layout as {@code PacketCipher}: {@code [12 byte nonce][ciphertext][16 byte tag]}. The nonce is
 * {@code epoch(4) | counter(8)}: the epoch tells the receiver which of the sender's keys was used, the counter
 * never repeats within an epoch and is checked against replays by the receiver. The speaker's UUID is part of the
 * authenticated data, so a server cannot pass off one player's packets as another player's.
 */
public final class WhitelistCipher {

    public static final int NONCE_LEN = 12;
    public static final int TAG_BYTES = 16;
    /** Shortest valid sealed packet: nonce, tag and at least one byte of payload. */
    public static final int MIN_LENGTH = NONCE_LEN + TAG_BYTES + 1;

    private static final byte[] AAD_PREFIX = "VCW2".getBytes(StandardCharsets.US_ASCII);

    private WhitelistCipher() {
    }

    public static byte[] seal(byte[] plain, byte[] key, int epoch, long counter, UUID speaker) throws GeneralSecurityException {
        byte[] nonce = ByteBuffer.allocate(NONCE_LEN).putInt(epoch).putLong(counter).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BYTES * 8, nonce));
        cipher.updateAAD(aad(speaker));
        byte[] out = new byte[NONCE_LEN + plain.length + TAG_BYTES];
        System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
        cipher.doFinal(plain, 0, plain.length, out, NONCE_LEN);
        return out;
    }

    /** Decrypts a packet; returns {@code null} if the key is wrong or the packet was tampered with. */
    public static byte[] open(byte[] sealed, byte[] key, UUID speaker) {
        if (sealed == null || sealed.length < MIN_LENGTH) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BYTES * 8, sealed, 0, NONCE_LEN));
            cipher.updateAAD(aad(speaker));
            return cipher.doFinal(sealed, NONCE_LEN, sealed.length - NONCE_LEN);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /** The epoch announced in the nonce (not authenticated until {@link #open} succeeded). */
    public static int epochOf(byte[] sealed) {
        return ByteBuffer.wrap(sealed, 0, 4).getInt();
    }

    public static long counterOf(byte[] sealed) {
        return ByteBuffer.wrap(sealed, 4, 8).getLong();
    }

    private static byte[] aad(UUID speaker) {
        return ByteBuffer.allocate(AAD_PREFIX.length + 16).put(AAD_PREFIX).put(VoiceKeyGrant.uuidBytes(speaker)).array();
    }
}
