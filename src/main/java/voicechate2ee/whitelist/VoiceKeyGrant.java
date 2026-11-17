package voicechate2ee.whitelist;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.UUID;

/**
 * The messages that hand your voice key to a whitelisted player, and the cryptography behind them.
 *
 * <p>Both players have a Minecraft <b>profile key</b> (RSA 2048), certified by Mojang and tied to their UUID. The
 * server announces the public part to everybody, but it cannot forge it. So:
 * <ul>
 *   <li>The sender encrypts the voice key (RSA-OAEP, SHA-256) with the <b>recipient's</b> profile key: only the
 *       recipient can open it.</li>
 *   <li>The sender signs the grant with their own profile key: the recipient knows it really comes from that
 *       player and that it is meant for them.</li>
 * </ul>
 * Because Mojang vouches for the keys, there is no handshake and nothing to compare by hand.
 *
 * <pre>
 * GRANT   = 1 | timestamp(4) | epoch(4) | wrappedKey(256) | signature(256)
 * REQUEST = 2 | timestamp(4)
 * wrappedKey plaintext = voiceKey(32) | epoch(4) | firstCounter(8)
 * </pre>
 *
 * <p>This class only depends on the JDK, so it is covered by the self-test.
 */
public final class VoiceKeyGrant {

    public static final int TYPE_GRANT = 1;
    public static final int TYPE_REQUEST = 2;
    public static final int KEY_SIZE = 32;
    public static final int RSA_SIZE = 256;
    public static final long MAX_CLOCK_SKEW_SECONDS = 300;

    private static final int GRANT_LENGTH = 1 + 4 + 4 + RSA_SIZE + RSA_SIZE;
    private static final byte[] LABEL_CONTEXT = "voicechat-e2ee/v2/grant-key".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGN_CONTEXT = "voicechat-e2ee/v2/grant-signature".getBytes(StandardCharsets.US_ASCII);

    private VoiceKeyGrant() {
    }

    public record Grant(long timestampSeconds, int epoch, byte[] wrappedKey, byte[] signature) {
    }

    /** What a recipient learns from a grant. */
    public record Opened(byte[] key, int epoch, long firstCounter) {
    }

    // ---------------------------------------------------------------- wire format

    public static byte[] encodeGrant(Grant grant) {
        ByteBuffer buf = ByteBuffer.allocate(GRANT_LENGTH);
        buf.put((byte) TYPE_GRANT).putInt((int) grant.timestampSeconds()).putInt(grant.epoch());
        buf.put(grant.wrappedKey()).put(grant.signature());
        return buf.array();
    }

    public static Grant decodeGrant(byte[] blob) {
        if (blob == null || blob.length != GRANT_LENGTH || blob[0] != TYPE_GRANT) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(blob, 1, blob.length - 1);
        long timestamp = Integer.toUnsignedLong(buf.getInt());
        int epoch = buf.getInt();
        byte[] wrapped = new byte[RSA_SIZE];
        buf.get(wrapped);
        byte[] signature = new byte[RSA_SIZE];
        buf.get(signature);
        return new Grant(timestamp, epoch, wrapped, signature);
    }

    public static byte[] encodeRequest(long timestampSeconds) {
        return ByteBuffer.allocate(5).put((byte) TYPE_REQUEST).putInt((int) timestampSeconds).array();
    }

    /** @return the timestamp, or -1 if this is not a request */
    public static long decodeRequest(byte[] blob) {
        if (blob == null || blob.length != 5 || blob[0] != TYPE_REQUEST) {
            return -1;
        }
        return Integer.toUnsignedLong(ByteBuffer.wrap(blob, 1, 4).getInt());
    }

    public static boolean isFresh(long timestampSeconds, long nowSeconds) {
        return Math.abs(nowSeconds - timestampSeconds) <= MAX_CLOCK_SKEW_SECONDS;
    }

    // ---------------------------------------------------------------- crypto

    /** Builds a complete, signed grant of {@code voiceKey} for {@code recipient}. */
    public static Grant create(PrivateKey senderProfileKey, PublicKey recipientProfileKey, UUID sender, UUID recipient,
                               byte[] voiceKey, int epoch, long firstCounter, long nowSeconds) throws GeneralSecurityException {
        byte[] plain = ByteBuffer.allocate(KEY_SIZE + 4 + 8).put(voiceKey).putInt(epoch).putLong(firstCounter).array();
        byte[] wrapped = oaep(Cipher.ENCRYPT_MODE, recipientProfileKey, label(sender, recipient, nowSeconds, epoch)).doFinal(plain);
        Arrays.fill(plain, (byte) 0);
        if (wrapped.length != RSA_SIZE) {
            throw new GeneralSecurityException("Unsupported profile key size");
        }
        byte[] data = signedData(sender, recipient, nowSeconds, epoch, wrapped);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(senderProfileKey);
        signature.update(data);
        return new Grant(nowSeconds, epoch, wrapped, signature.sign());
    }

    /** Is this grant signed by {@code senderProfileKey} for exactly this sender and recipient? */
    public static boolean verify(PublicKey senderProfileKey, UUID sender, UUID recipient, Grant grant) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(senderProfileKey);
            signature.update(signedData(sender, recipient, grant.timestampSeconds(), grant.epoch(), grant.wrappedKey()));
            return signature.verify(grant.signature());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** Opens a grant with our own profile key. Only call this after {@link #verify}. */
    public static Opened open(PrivateKey ownProfileKey, UUID sender, UUID recipient, Grant grant) throws GeneralSecurityException {
        byte[] plain = oaep(Cipher.DECRYPT_MODE, ownProfileKey, label(sender, recipient, grant.timestampSeconds(), grant.epoch()))
                .doFinal(grant.wrappedKey());
        if (plain.length != KEY_SIZE + 4 + 8) {
            throw new GeneralSecurityException("Bad grant");
        }
        ByteBuffer buf = ByteBuffer.wrap(plain);
        byte[] key = new byte[KEY_SIZE];
        buf.get(key);
        int epoch = buf.getInt();
        long firstCounter = buf.getLong();
        Arrays.fill(plain, (byte) 0);
        if (epoch != grant.epoch() || firstCounter < 0) {
            throw new GeneralSecurityException("Inconsistent grant");
        }
        return new Opened(key, epoch, firstCounter);
    }

    private static Cipher oaep(int mode, java.security.Key key, byte[] label) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(mode, key, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, new PSource.PSpecified(label)));
        return cipher;
    }

    /** OAEP label: binds the encrypted key to sender, recipient and this very grant. */
    private static byte[] label(UUID sender, UUID recipient, long timestampSeconds, int epoch) {
        return ByteBuffer.allocate(LABEL_CONTEXT.length + 16 + 16 + 4 + 4)
                .put(LABEL_CONTEXT).put(uuidBytes(sender)).put(uuidBytes(recipient))
                .putInt((int) timestampSeconds).putInt(epoch).array();
    }

    private static byte[] signedData(UUID sender, UUID recipient, long timestampSeconds, int epoch, byte[] wrapped) {
        return ByteBuffer.allocate(SIGN_CONTEXT.length + 16 + 16 + 4 + 4 + wrapped.length)
                .put(SIGN_CONTEXT).put(uuidBytes(sender)).put(uuidBytes(recipient))
                .putInt((int) timestampSeconds).putInt(epoch).put(wrapped).array();
    }

    static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }
}
