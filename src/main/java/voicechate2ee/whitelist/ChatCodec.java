package voicechate2ee.whitelist;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carries binary messages (voice key grants and requests) through the Minecraft chat as ordinary whispers.
 *
 * <p>Binary data is written as CJK ideographs, 14 bits per character (U+4E00 to U+8DFF). Every such character is
 * a single UTF-16 unit and allowed in chat, so a whisper carries about 400 bytes. A message is split into at most
 * {@value #MAX_FRAGMENTS} fragments, each of which is a separate whisper:
 *
 * <pre>
 * fragment = messageId(1) | index(4 bit) total(4 bit) | length(2) | chunk
 * whisper  = "w &lt;name&gt; " HEADER text(fragment)
 * </pre>
 *
 * <p>This class knows nothing about Minecraft, so it is covered by the self-test.
 */
public final class ChatCodec {

    /** Marks a whisper as one of ours. The Greek capital omega makes an accidental match practically impossible. */
    public static final String HEADER = "VCE\u03A9:";
    /** One less than the vanilla chat length limit of 256. */
    public static final int MAX_COMMAND_LENGTH = 255;
    public static final int MAX_FRAGMENTS = 4;
    public static final int FRAGMENT_HEADER_SIZE = 4;

    private static final int BITS = 14;
    private static final int BASE = 0x4E00;
    private static final int ALPHABET_SIZE = 1 << BITS;
    private static final int MAX_TEXT_CHARS = 300;

    private ChatCodec() {
    }

    public record Fragment(int messageId, int index, int total, byte[] data) {
    }

    // ---------------------------------------------------------------- bytes <-> text

    public static String toText(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 8 / BITS + 1);
        int acc = 0;
        int bits = 0;
        for (byte b : data) {
            acc = (acc << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= BITS) {
                sb.append((char) (BASE + ((acc >>> (bits - BITS)) & (ALPHABET_SIZE - 1))));
                bits -= BITS;
                acc &= (1 << bits) - 1;
            }
        }
        if (bits > 0) {
            sb.append((char) (BASE + ((acc << (BITS - bits)) & (ALPHABET_SIZE - 1))));
        }
        return sb.toString();
    }

    /** Returns {@code null} if the text contains characters outside the alphabet. Trailing padding bits are dropped. */
    public static byte[] fromText(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length() * BITS / 8);
        int acc = 0;
        int bits = 0;
        for (int i = 0; i < text.length(); i++) {
            int v = text.charAt(i) - BASE;
            if (v < 0 || v >= ALPHABET_SIZE) {
                return null;
            }
            acc = (acc << BITS) | v;
            bits += BITS;
            while (bits >= 8) {
                out.write((acc >>> (bits - 8)) & 0xFF);
                bits -= 8;
                acc &= (1 << bits) - 1;
            }
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- fragments

    /**
     * Largest chunk (in bytes) such that a fragment always fits into one chat command.
     *
     * @param prefixLength length of {@code "w <name> "} plus {@link #HEADER}
     */
    public static int maxChunkSize(int prefixLength) {
        int chars = MAX_COMMAND_LENGTH - prefixLength;
        return chars * BITS / 8 - FRAGMENT_HEADER_SIZE;
    }

    /** @return the text of each whisper (without the header), one per fragment */
    public static List<String> encode(byte[] blob, int messageId, int chunkSize) {
        if (blob.length == 0 || chunkSize <= 0) {
            throw new IllegalArgumentException("Nothing to encode");
        }
        int total = (blob.length + chunkSize - 1) / chunkSize;
        if (total > MAX_FRAGMENTS) {
            throw new IllegalArgumentException("Message needs " + total + " fragments, the maximum is " + MAX_FRAGMENTS);
        }
        List<String> result = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int start = i * chunkSize;
            int end = Math.min(blob.length, start + chunkSize);
            int length = end - start;
            byte[] raw = new byte[FRAGMENT_HEADER_SIZE + length];
            raw[0] = (byte) messageId;
            raw[1] = (byte) (i << 4 | total);
            raw[2] = (byte) (length >> 8);
            raw[3] = (byte) length;
            System.arraycopy(blob, start, raw, FRAGMENT_HEADER_SIZE, length);
            result.add(toText(raw));
        }
        return result;
    }

    /** Parses one whisper text (without the header). Returns {@code null} if it is not a valid fragment. */
    public static Fragment decodeFragment(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_CHARS) {
            return null;
        }
        byte[] raw = fromText(text);
        if (raw == null || raw.length < FRAGMENT_HEADER_SIZE + 1) {
            return null;
        }
        int messageId = raw[0] & 0xFF;
        int index = (raw[1] & 0xFF) >> 4;
        int total = raw[1] & 0x0F;
        int length = (raw[2] & 0xFF) << 8 | (raw[3] & 0xFF);
        int available = raw.length - FRAGMENT_HEADER_SIZE;
        // the text encoding may add one byte of zero padding at the end
        if (total < 1 || total > MAX_FRAGMENTS || index >= total || length < 1 || length > available || available - length > 1) {
            return null;
        }
        return new Fragment(messageId, index, total, Arrays.copyOfRange(raw, FRAGMENT_HEADER_SIZE, FRAGMENT_HEADER_SIZE + length));
    }

    // ---------------------------------------------------------------- reassembly

    /** Puts fragments back together. Bounded per sender and in total, and incomplete messages expire. */
    public static final class Reassembler {
        private static final long TIMEOUT_MS = 30_000;
        private static final int MAX_PENDING_PER_SENDER = 4;
        private static final int MAX_PENDING_TOTAL = 64;

        private record Key(UUID sender, int messageId) {
        }

        private static final class Partial {
            final byte[][] parts;
            final long createdAt;
            int received;

            Partial(int total, long createdAt) {
                this.parts = new byte[total][];
                this.createdAt = createdAt;
            }
        }

        private final Map<Key, Partial> pending = new HashMap<>();

        /** @return the complete message once all fragments have arrived, otherwise {@code null} */
        public byte[] offer(UUID sender, Fragment fragment, long nowMs) {
            pending.values().removeIf(p -> nowMs - p.createdAt > TIMEOUT_MS);
            if (fragment.total() == 1) {
                return fragment.data();
            }
            Key key = new Key(sender, fragment.messageId());
            Partial partial = pending.get(key);
            if (partial == null) {
                if (pending.size() >= MAX_PENDING_TOTAL) {
                    return null;
                }
                long forSender = pending.keySet().stream().filter(k -> k.sender().equals(sender)).count();
                if (forSender >= MAX_PENDING_PER_SENDER) {
                    return null;
                }
                partial = new Partial(fragment.total(), nowMs);
                pending.put(key, partial);
            } else if (partial.parts.length != fragment.total()) {
                pending.remove(key);
                return null;
            }
            if (partial.parts[fragment.index()] == null) {
                partial.parts[fragment.index()] = fragment.data();
                partial.received++;
            }
            if (partial.received < partial.parts.length) {
                return null;
            }
            pending.remove(key);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] part : partial.parts) {
                out.writeBytes(part);
            }
            return out.toByteArray();
        }

        public void clear() {
            pending.clear();
        }
    }
}
