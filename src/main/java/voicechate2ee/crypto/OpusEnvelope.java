package voicechate2ee.crypto;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Versteckt die verschluesselten Daten im Padding eines gueltigen (leeren) Opus-Pakets.
 *
 * <p>Warum? Spieler ohne diese Mod wuerden den Chiffretext sonst als Opus-Audio dekodieren und
 * Rauschen hoeren. Opus ignoriert die Inhalte von Padding-Bytes (RFC 6716, Abschnitt 3.2.5). Ein Client
 * ohne Mod sieht hier also ein Paket mit einem leeren Frame und gibt Stille aus, waehrend die Mod
 * die Nutzdaten aus dem Padding zieht.
 *
 * <p>Aufbau: {@code TOC(0xFB: CELT 20ms, Code 3) | 0x41 (CBR, Padding, 1 Frame) | Paddinglaenge | Padding}.
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
        // Jedes 255-Byte steht fuer 254 Padding-Bytes plus ein weiteres Laengenbyte
        while (remaining > 254) {
            out.write(255);
            remaining -= 254;
        }
        out.write(remaining);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    /** Gibt die versteckten Nutzdaten zurueck oder {@code null}, wenn das Paket nicht von dieser Mod stammt. */
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
        // Der Frame selbst muss leer sein: danach folgt nur noch das Padding
        if (padding != packet.length - i) {
            return null;
        }
        return Arrays.copyOfRange(packet, i, packet.length);
    }
}
