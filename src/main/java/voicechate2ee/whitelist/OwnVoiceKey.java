package voicechate2ee.whitelist;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The key your own voice is encrypted with in whitelist mode. It exists only in memory and is replaced
 * ({@link #rotate()}) whenever someone is removed from the whitelist.
 *
 * <p>Epochs are strictly increasing during this client process. Receivers can therefore reject an unseen
 * but older epoch instead of treating every random epoch as equally new. A disconnect clears received
 * epoch state; a new process starts a new in-memory key lifecycle.
 */
public final class OwnVoiceKey {

    public static final class Key {
        private final int epoch;
        private final byte[] bytes;
        private final AtomicLong counter = new AtomicLong();

        private Key(int epoch, byte[] bytes) {
            this.epoch = epoch;
            this.bytes = bytes;
        }

        public int epoch() {
            return epoch;
        }

        public byte[] bytes() {
            return bytes.clone();
        }

        public long nextCounter() {
            return counter.get();
        }
    }

    private static volatile Key current;

    private OwnVoiceKey() {
    }

    public static Key current() {
        Key key = current;
        if (key == null) {
            synchronized (OwnVoiceKey.class) {
                if (current == null) {
                    current = generate((int) Math.max(1L, System.currentTimeMillis() / 1000L));
                }
                key = current;
            }
        }
        return key;
    }

    /** Switches to a fresh key with a strictly newer epoch. */
    public static synchronized Key rotate() {
        Key old = current;
        if (old != null && old.epoch == Integer.MAX_VALUE) {
            throw new IllegalStateException("Voice key epoch exhausted; restart the client before rotating again");
        }
        current = generate(old == null ? 1 : old.epoch + 1);
        return current;
    }

    public static byte[] seal(byte[] plain, UUID speaker) throws java.security.GeneralSecurityException {
        Key key = current();
        long counter = key.counter.getAndIncrement();
        if (counter < 0) {
            throw new java.security.GeneralSecurityException("Voice packet counter exhausted");
        }
        return WhitelistCipher.seal(plain, key.bytes, key.epoch, counter, speaker);
    }

    private static Key generate(int epoch) {
        byte[] bytes = new byte[VoiceKeyGrant.KEY_SIZE];
        new java.security.SecureRandom().nextBytes(bytes);
        return new Key(epoch, bytes);
    }
}
