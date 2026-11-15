package voicechate2ee.opus;

import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.crypto.PacketCipher;

import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Umhuellt den Opus-Encoder von Simple Voice Chat: Jedes kodierte Paket wird verschluesselt,
 * bevor es zum Server geht. Der Server sieht nur den Chiffretext.
 */
public final class EncryptingOpusEncoder implements OpusEncoder {

    /** Maximale Opus-Nutzlast, die Simple Voice Chat pro Paket erlaubt. */
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
            // Fail-closed: lieber nichts senden als unverschluesselt
            if (warnedNoKey.compareAndSet(false, true)) {
                LOG.log(System.Logger.Level.WARNING,
                        "Verschluesselung ist aktiv, aber es ist kein key gesetzt. Mikrofon wird nicht uebertragen.");
            }
            return EMPTY;
        }
        try {
            byte[] packet = OpusEnvelope.wrap(cipher.seal(opus, state.key()));
            if (packet.length > MAX_PAYLOAD) {
                if (warnedTooBig.compareAndSet(false, true)) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Verschluesseltes Paket ist zu gross (" + packet.length + " Byte), Paket verworfen.");
                }
                return EMPTY;
            }
            return packet;
        } catch (GeneralSecurityException e) {
            LOG.log(System.Logger.Level.ERROR, "Verschluesselung fehlgeschlagen, Paket verworfen", e);
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
