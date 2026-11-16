package voicechate2ee.opus;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.crypto.PacketCipher;

import java.util.UUID;

/**
 * Wraps the Opus decoder of Simple Voice Chat: incoming packets are decrypted before they are decoded.
 *
 * <p>Decryption <b>always</b> happens as soon as a key is set, even if you have switched your own encryption
 * off: anyone who speaks encrypted with your key stays audible. The {@code enabled} switch only controls
 * whether you send encrypted yourself.
 *
 * <p>Packets that are not readable with your key (no key, different key, unencrypted) are dropped (silence)
 * while encryption is active and strict, and are otherwise played unchanged.
 *
 * <p>On the side, this remembers which player speaks encrypted ({@link EncryptedPlayers}), so the UI can show
 * the incognito icon.
 */
public final class DecryptingOpusDecoder implements OpusDecoder {

    private static final short[] NO_SAMPLES = new short[0];
    private static final short[][] NO_FRAMES = new short[0][];

    private final OpusDecoder delegate;
    /** The speaker this decoder belongs to (audio channel ID). May be {@code null}. */
    private final UUID speaker;

    public DecryptingOpusDecoder(OpusDecoder delegate) {
        this(delegate, null);
    }

    public DecryptingOpusDecoder(OpusDecoder delegate, UUID speaker) {
        this.delegate = delegate;
        this.speaker = speaker;
    }

    @Override
    public short[] decode(byte[] data) {
        if (data == null || data.length == 0) {
            return delegate.decode(data);
        }
        E2EEState.Snapshot state = E2EEState.snapshot();
        byte[] inner = unseal(data, state);
        if (inner != null) {
            return delegate.decode(inner);
        }
        return dropUnreadable(state) ? NO_SAMPLES : delegate.decode(data);
    }

    @Override
    public short[][] decode(byte[] input, int frames) {
        if (input == null || input.length == 0) {
            return delegate.decode(input, frames);
        }
        E2EEState.Snapshot state = E2EEState.snapshot();
        byte[] inner = unseal(input, state);
        if (inner != null) {
            return delegate.decode(inner, frames);
        }
        return dropUnreadable(state) ? NO_FRAMES : delegate.decode(input, frames);
    }

    /** Only with your own strict encryption is everything dropped that is not readable with the key. */
    private static boolean dropUnreadable(E2EEState.Snapshot state) {
        return state.enabled() && state.requireEncryption();
    }

    /**
     * Returns the decrypted Opus packet, or {@code null} if it cannot be decrypted.
     * Also records whether the speaker is currently speaking encrypted.
     */
    private byte[] unseal(byte[] data, E2EEState.Snapshot state) {
        byte[] inner = null;
        if (state.key() != null) {
            byte[] sealed = OpusEnvelope.unwrap(data);
            if (sealed != null) {
                inner = PacketCipher.open(sealed, state.key());
            }
        }
        if (inner != null) {
            EncryptedPlayers.markEncrypted(speaker, state.key().fingerprint());
        } else {
            EncryptedPlayers.markUnverified(speaker);
        }
        return inner;
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
