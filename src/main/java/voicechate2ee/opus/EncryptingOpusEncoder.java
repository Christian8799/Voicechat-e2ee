package voicechate2ee.opus;

import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.crypto.PacketCipher;

import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps the Opus encoder of Simple Voice Chat: every encoded packet is encrypted
 * before it goes to the server. The server only sees the ciphertext.
 */
public final class EncryptingOpusEncoder implements OpusEncoder {

    /** Maximum Opus payload that Simple Voice Chat allows per packet. */
    private static final int MAX_PAYLOAD = 1275;
    private static final byte[] EMPTY = new byte[0];
    private static final System.Logger LOG = System.getLogger("voicechat-e2ee");

    private final OpusEncoder delegate;
    private final PacketCipher cipher = new PacketCipher();
    private final AtomicBoolean warnedNoKey = new AtomicBoolean();
    private final AtomicBoolean warnedTooBig = new AtomicBoolean();

    public EncryptingOpusEncoder(OpusEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte[] encode(short[] rawAudio) {
        byte[] opus = delegate.encode(rawAudio);
        E2EEState.Snapshot state = E2EEState.snapshot();
        if (!state.enabled() || opus == null || opus.length == 0) {
            return opus;
        }
        if (state.key() == null) {
            // Fail-closed: better to send nothing than to send unencrypted
            if (warnedNoKey.compareAndSet(false, true)) {
                LOG.log(System.Logger.Level.WARNING,
                        "Encryption is enabled, but no key is set. The microphone is not being transmitted.");
            }
            return EMPTY;
        }
        try {
            byte[] packet = OpusEnvelope.wrap(cipher.seal(opus, state.key()));
            if (packet.length > MAX_PAYLOAD) {
                if (warnedTooBig.compareAndSet(false, true)) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Encrypted packet is too large (" + packet.length + " bytes), packet dropped.");
                }
                return EMPTY;
            }
            return packet;
        } catch (GeneralSecurityException e) {
            LOG.log(System.Logger.Level.ERROR, "Encryption failed, packet dropped", e);
            return EMPTY;
        }
    }

    @Override
    public void resetState() {
        delegate.resetState();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
