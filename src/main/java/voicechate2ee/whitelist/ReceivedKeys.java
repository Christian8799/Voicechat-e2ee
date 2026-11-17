package voicechate2ee.whitelist;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The voice keys that whitelisted players have handed to you, one per speaker (the last two epochs are kept,
 * so packets that are still on their way keep working after a key change).
 *
 * <p>Written from the client thread (grants) and read from the audio threads (decryption), hence thread-safe.
 * Keys live only in memory.
 */
public final class ReceivedKeys {

    private static final int KEEP_EPOCHS = 2;

    /** A key plus the replay protection for its packets. */
    public static final class Entry {
        private final int epoch;
        private final byte[] key;
        private final ReplayWindow window;

        Entry(int epoch, byte[] key, long firstCounter) {
            this.epoch = epoch;
            this.key = key;
            this.window = new ReplayWindow(firstCounter);
        }

        public int epoch() {
            return epoch;
        }
    }

    private static final Map<UUID, List<Entry>> KEYS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MISSING = new ConcurrentHashMap<>();

    private ReceivedKeys() {
    }

    /**
     * Stores a key from a verified grant. {@code firstCounter}: packets with a lower counter are old replays.
     *
     * <p>A key of an epoch we already have is kept as it is, so that a grant that is sent again (by the speaker or
     * by a malicious server) cannot reset the replay protection.
     *
     * @return true if the key was new
     */
    public static boolean put(UUID speaker, int epoch, byte[] key, long firstCounter) {
        boolean[] added = {false};
        KEYS.compute(speaker, (id, list) -> {
            if (list != null) {
                for (Entry e : list) {
                    if (e.epoch == epoch) {
                        return list;
                    }
                }
            }
            List<Entry> next = new CopyOnWriteArrayList<>();
            if (list != null) {
                next.addAll(list);
            }
            next.add(new Entry(epoch, key.clone(), firstCounter));
            while (next.size() > KEEP_EPOCHS) {
                next.remove(0);
            }
            added[0] = true;
            return next;
        });
        MISSING.remove(speaker);
        return added[0];
    }

    public static boolean has(UUID speaker) {
        List<Entry> list = KEYS.get(speaker);
        return list != null && !list.isEmpty();
    }

    public static void forget(UUID speaker) {
        KEYS.remove(speaker);
        MISSING.remove(speaker);
    }

    public static void clear() {
        KEYS.clear();
        MISSING.clear();
    }

    private static Entry find(UUID speaker, int epoch) {
        List<Entry> list = KEYS.get(speaker);
        if (list == null) {
            return null;
        }
        for (Entry e : list) {
            if (e.epoch == epoch) {
                return e;
            }
        }
        return null;
    }

    /**
     * Decrypts a sealed packet from {@code speaker} with the key they granted you.
     * Returns {@code null} if there is no such key, the packet is not authentic, or it is a replay.
     */
    public static byte[] open(UUID speaker, byte[] sealed) {
        if (speaker == null || sealed == null || sealed.length < WhitelistCipher.MIN_LENGTH) {
            return null;
        }
        Entry entry = find(speaker, WhitelistCipher.epochOf(sealed));
        if (entry == null) {
            return null;
        }
        byte[] plain = WhitelistCipher.open(sealed, entry.key, speaker);
        if (plain == null) {
            return null;
        }
        return entry.window.accept(WhitelistCipher.counterOf(sealed)) ? plain : null;
    }

    /**
     * Called by the receive path for a packet that nothing could decrypt. If the speaker is on your whitelist and
     * you simply lack the key of that epoch, the client thread will ask them for it.
     */
    public static void noteUnreadable(UUID speaker, byte[] sealed) {
        if (speaker == null || sealed == null || sealed.length < WhitelistCipher.MIN_LENGTH) {
            return;
        }
        if (WhitelistState.contains(speaker) && find(speaker, WhitelistCipher.epochOf(sealed)) == null) {
            MISSING.putIfAbsent(speaker, System.currentTimeMillis());
        }
    }

    /** Whitelisted speakers we heard but could not read, since the last call. */
    public static List<UUID> drainMissing() {
        List<UUID> result = List.copyOf(MISSING.keySet());
        result.forEach(MISSING::remove);
        return result;
    }

    /** Accepts each counter value once, tolerating reordering of up to 64 packets (about 1.3 seconds). */
    static final class ReplayWindow {
        private static final int SIZE = 64;

        private final long first;
        private long highest;
        private long seen;

        ReplayWindow(long firstCounter) {
            this.first = firstCounter;
            this.highest = firstCounter - 1;
        }

        synchronized boolean accept(long counter) {
            if (counter < first || counter < 0) {
                return false;
            }
            if (counter > highest) {
                long shift = counter - highest;
                seen = shift >= SIZE ? 0 : seen << shift;
                seen |= 1L;
                highest = counter;
                return true;
            }
            long age = highest - counter;
            if (age >= SIZE || (seen >>> age & 1L) != 0) {
                return false;
            }
            seen |= 1L << age;
            return true;
        }
    }
}
