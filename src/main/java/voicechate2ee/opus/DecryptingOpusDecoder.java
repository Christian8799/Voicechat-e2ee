package voicechate2ee.opus;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.crypto.PacketCipher;

/**
 * Umhuellt den Opus-Decoder von Simple Voice Chat: Eingehende Pakete werden entschluesselt, bevor sie
 * dekodiert werden. Pakete ohne gueltigen Key werden verworfen (Stille).
 */
public final class DecryptingOpusDecoder implements OpusDecoder {

    private static final short[] NO_SAMPLES = new short[0];
    private static final short[][] NO_FRAMES = new short[0][];

    private final OpusDecoder delegate;

    public DecryptingOpusDecoder(OpusDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public short[] decode(byte[] data) {
        E2EEState.Snapshot state = E2EEState.snapshot();
        if (!state.enabled() || data == null || data.length == 0) {
            return delegate.decode(data);
        }
        byte[] inner = unseal(data, state);
        if (inner != null) {
            return delegate.decode(inner);
        }
        return state.requireEncryption() ? NO_SAMPLES : delegate.decode(data);
    }

    @Override
    public short[][] decode(byte[] input, int frames) {
        E2EEState.Snapshot state = E2EEState.snapshot();
        if (!state.enabled() || input == null || input.length == 0) {
            return delegate.decode(input, frames);
        }
        byte[] inner = unseal(input, state);
        if (inner != null) {
            return delegate.decode(inner, frames);
        }
        return state.requireEncryption() ? NO_FRAMES : delegate.decode(input, frames);
    }

    /** Gibt das entschluesselte Opus-Paket zurueck oder {@code null}, wenn es nicht entschluesselt werden kann. */
    private static byte[] unseal(byte[] data, E2EEState.Snapshot state) {
        if (state.key() == null) {
            return null;
        }
        byte[] sealed = OpusEnvelope.unwrap(data);
        if (sealed == null) {
            return null;
        }
        return PacketCipher.open(sealed, state.key());
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
