package voicechate2ee.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verschluesselt einzelne Opus-Pakete mit AES-256-GCM.
 *
 * <p>Format des versiegelten Pakets: {@code [12 Byte Nonce][Chiffretext][16 Byte GCM-Tag]}.
 * Die Nonce besteht aus einer zufaelligen 4-Byte-Sitzungs-ID und einem 8-Byte-Zaehler und wiederholt
 * sich dadurch nie. Der GCM-Tag sorgt dafuer, dass falsche Keys und manipulierte Pakete erkannt werden.
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

    /** Entschluesselt ein Paket; gibt {@code null} zurueck, wenn der Key falsch oder das Paket ungueltig ist. */
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
