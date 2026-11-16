package voicechate2ee.crypto;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Hides the encrypted data in the padding of a valid (empty) Opus packet.
 *
 * <p>Why? Players without this mod would otherwise decode the ciphertext as Opus audio and hear noise.
 * Opus ignores the contents of padding bytes (RFC 6716, section 3.2.5). A client without the mod therefore
 * sees a packet with an empty frame and plays silence, while the mod extracts the payload from the padding.
 *
 * <p>Layout: {@code TOC(0xFB: CELT 20 ms, code 3) | 0x41 (CBR, padding, 1 frame) | padding length | padding}.
 */
public final class OpusEnvelope {

    private static final int TOC = 0xFB;
    private static final int FRAME_COUNT_BYTE = 0x41;

    private OpusEnvelope() {
    }

    public static byte[] wrap(byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        out.write(TOC);
        out.write(FRAME_COUNT_BYTE);
        int remaining = payload.length;
        // Each 255 byte stands for 254 padding bytes plus one more length byte
        while (remaining > 254) {
            out.write(255);
            remaining -= 254;
        }
        out.write(remaining);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    /** Returns the hidden payload, or {@code null} if the packet does not come from this mod. */
    public static byte[] unwrap(byte[] packet) {
        if (packet == null || packet.length < 3
                || (packet[0] & 0xFF) != TOC || (packet[1] & 0xFF) != FRAME_COUNT_BYTE) {
            return null;
        }
        int i = 2;
        int padding = 0;
        while (true) {
            if (i >= packet.length) {
                return null;
            }
            int b = packet[i++] & 0xFF;
            if (b == 255) {
                padding += 254;
            } else {
                padding += b;
                break;
            }
        }
        // The frame itself must be empty: only the padding may follow
        if (padding != packet.length - i) {
            return null;
        }
        return Arrays.copyOfRange(packet, i, packet.length);
    }
}
