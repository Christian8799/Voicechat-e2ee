package voicechate2ee.whitelist;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The key your own voice is encrypted with in whitelist mode. It exists only in memory and is replaced
 * ({@link #rotate()}) whenever someone is removed from the whitelist, so a removed player cannot keep listening.
 */
public final class OwnVoiceKey {

    /** One key. The counter gives every packet its own nonce. */
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

        /** The counter value the next packet will use. */
        public long nextCounter() {
            return counter.get();
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile Key current;

    private OwnVoiceKey() {
    }

    public static Key current() {
        Key key = current;
        if (key == null) {
            synchronized (OwnVoiceKey.class) {
                if (current == null) {
                    current = generate(0);
                }
                key = current;
            }
        }
        return key;
    }

    /** Switches to a fresh key. Everyone who should still hear you needs a new grant. */
    public static synchronized Key rotate() {
        Key old = current;
        current = generate(old == null ? 0 : old.epoch);
        return current;
    }

    /** Encrypts one packet with the current key. */
    public static byte[] seal(byte[] plain, UUID speaker) throws GeneralSecurityException {
        Key key = current();
        return WhitelistCipher.seal(plain, key.bytes, key.epoch, key.counter.getAndIncrement(), speaker);
    }

    private static Key generate(int notThisEpoch) {
        byte[] bytes = new byte[VoiceKeyGrant.KEY_SIZE];
        RANDOM.nextBytes(bytes);
        int epoch;
        do {
            epoch = RANDOM.nextInt();
        } while (epoch == notThisEpoch);
        return new Key(epoch, bytes);
    }
}
