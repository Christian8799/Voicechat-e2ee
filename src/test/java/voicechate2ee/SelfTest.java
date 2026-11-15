package voicechate2ee;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.crypto.PacketCipher;
import voicechate2ee.crypto.VoiceKey;
import voicechate2ee.opus.DecryptingOpusDecoder;
import voicechate2ee.opus.EncryptingOpusEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Einfacher Selbsttest ohne Minecraft und ohne JUnit. Starten mit:
 * {@code java -cp <build/classes/java/main:build/classes/java/test:voicechat-api> voicechate2ee.SelfTest}
 */
public final class SelfTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        testKey();
        testCipher();
        testEnvelope();
        testWrappers();
        System.out.println(checks + " Pruefungen, " + failures + " fehlgeschlagen");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FEHLER: " + name);
        }
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static void testKey() throws Exception {
        VoiceKey a = VoiceKey.fromPassphrase("hallo-welt");
        VoiceKey b = VoiceKey.fromPassphrase("hallo-welt");
        VoiceKey c = VoiceKey.fromPassphrase("hallo-welt!");
        check(a.fingerprint().equals(b.fingerprint()), "gleicher Key -> gleicher Code");
        check(!a.fingerprint().equals(c.fingerprint()), "anderer Key -> anderer Code");
        String g = VoiceKey.generatePassphrase();
        check(g.matches("([A-Z2-9]{4}-){5}[A-Z2-9]{4}"), "generierter Key hat erwartetes Format: " + g);
        check(!g.equals(VoiceKey.generatePassphrase()), "generierte Keys unterscheiden sich");
    }

    private static void testCipher() throws Exception {
        VoiceKey key = VoiceKey.fromPassphrase("key-1");
        VoiceKey other = VoiceKey.fromPassphrase("key-2");
        PacketCipher cipher = new PacketCipher();
        byte[] plain = random(97);
        byte[] sealed = cipher.seal(plain, key);
        check(sealed.length == plain.length + 28, "Overhead = 12 Nonce + 16 Tag");
        check(Arrays.equals(PacketCipher.open(sealed, key), plain), "Roundtrip");
        check(PacketCipher.open(sealed, other) == null, "falscher Key wird abgelehnt");
        boolean allRejected = true;
        for (int i = 0; i < sealed.length; i++) {
            byte[] t = sealed.clone();
            t[i] ^= 0x01;
            if (PacketCipher.open(t, key) != null) {
                allRejected = false;
            }
        }
        check(allRejected, "jede einzelne Manipulation wird erkannt");
        check(PacketCipher.open(new byte[5], key) == null, "zu kurzes Paket wird abgelehnt");
        check(PacketCipher.open(null, key) == null, "null wird abgelehnt");
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            nonces.add(Arrays.toString(Arrays.copyOf(cipher.seal(new byte[1], key), 12)));
        }
        check(nonces.size() == 20000, "Nonces wiederholen sich nicht");
        check(!Arrays.equals(cipher.seal(plain, key), cipher.seal(plain, key)), "gleiche Daten -> verschiedener Chiffretext");
    }

    private static void testEnvelope() {
        int[] sizes = {0, 1, 2, 253, 254, 255, 256, 507, 508, 509, 510, 762, 1000, 1275};
        for (int n : sizes) {
            byte[] payload = random(n);
            byte[] wrapped = OpusEnvelope.wrap(payload);
            byte[] back = OpusEnvelope.unwrap(wrapped);
            check(back != null && Arrays.equals(back, payload), "Envelope-Roundtrip, Groesse " + n);
        }
        check(OpusEnvelope.unwrap(new byte[]{(byte) 0x78, 0x01, 0x02}) == null, "normales Opus-Paket wird nicht erkannt");
        check(OpusEnvelope.unwrap(new byte[0]) == null, "leeres Paket");
        check(OpusEnvelope.unwrap(null) == null, "null");
        byte[] w = OpusEnvelope.wrap(random(50));
        byte[] shortened = Arrays.copyOf(w, w.length - 1);
        check(OpusEnvelope.unwrap(shortened) == null, "abgeschnittenes Paket wird abgelehnt");
        byte[] extended = Arrays.copyOf(w, w.length + 1);
        check(OpusEnvelope.unwrap(extended) == null, "verlaengertes Paket wird abgelehnt");
    }

    private static void testWrappers() throws Exception {
        Path dir = Files.createTempDirectory("e2ee-test");
        Path cfg = dir.resolve("voicechat-e2ee.properties");
        E2EEState.init(cfg);
        E2EEState.Snapshot s = E2EEState.snapshot();
        check(Files.exists(cfg), "Konfig wurde angelegt");
        check(s.enabled() && s.requireEncryption() && s.key() != null, "Standard: an, streng, Key erzeugt");

        byte[] opus = random(80);
        EncryptingOpusEncoder enc = new EncryptingOpusEncoder(new FakeEncoder(opus));
        RecordingDecoder rec = new RecordingDecoder();
        DecryptingOpusDecoder dec = new DecryptingOpusDecoder(rec);

        byte[] packet = enc.encode(new short[960]);
        check(packet.length > opus.length && !Arrays.equals(packet, opus), "Paket ist verschluesselt");
        check(!contains(packet, opus), "Klartext-Opus kommt im Paket nicht vor");
        short[][] frames = dec.decode(packet, 3);
        check(frames.length == 1 && Arrays.equals(rec.lastInput, opus) && rec.lastFrames == 3,
                "Empfaenger bekommt das Original-Opus-Paket (inkl. Frame-Anzahl)");
        check(Arrays.equals(dec.decode(packet).length == 1 ? rec.lastInput : null, opus), "Einzel-Decode ebenfalls");

        int callsBefore = rec.calls;
        check(dec.decode(opus, 1).length == 0 && rec.calls == callsBefore, "streng: Klartext-Paket wird verworfen");

        E2EEState.update(true, false, s.keyText());
        dec.decode(opus, 1);
        check(Arrays.equals(rec.lastInput, opus), "nicht streng: Klartext-Paket kommt durch");

        E2EEState.update(true, true, "ANDERER-KEY");
        callsBefore = rec.calls;
        check(dec.decode(packet, 1).length == 0 && rec.calls == callsBefore, "falscher Key: Paket wird verworfen");
        byte[] packet2 = enc.encode(new short[960]);
        E2EEState.update(true, true, s.keyText());
        check(dec.decode(packet2, 1).length == 0, "Paket mit anderem Key verschluesselt wird nicht gelesen");
        check(dec.decode(packet, 1).length == 1, "Key zurueck gesetzt: altes Paket wieder lesbar");

        E2EEState.update(false, true, s.keyText());
        check(Arrays.equals(enc.encode(new short[960]), opus), "aus: Encoder reicht Original durch");
        dec.decode(opus, 1);
        check(Arrays.equals(rec.lastInput, opus), "aus: Decoder reicht Original durch");

        E2EEState.update(true, true, "");
        check(enc.encode(new short[960]).length == 0, "kein Key: Encoder sendet nichts (fail-closed)");
        check(dec.decode(packet, 1).length == 0, "kein Key: Decoder verwirft");

        String tricky = "a\\b c=d:e";
        E2EEState.update(true, true, tricky);
        check(E2EEState.snapshot().keyText().equals(tricky), "Sonderzeichen im Key ueberleben Speichern/Laden");

        Files.writeString(cfg, "enabled=true\nrequireEncryption=true\nkey=VON-HAND-GEAENDERT\n");
        Files.setLastModifiedTime(cfg, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
        Thread.sleep(1200);
        check(E2EEState.snapshot().keyText().equals("VON-HAND-GEAENDERT"), "Datei-Aenderung wird ohne Neustart uebernommen");
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static final class FakeEncoder implements OpusEncoder {
        private final byte[] out;

        FakeEncoder(byte[] out) {
            this.out = out;
        }

        @Override
        public byte[] encode(short[] rawAudio) {
            return out.clone();
        }

        @Override
        public void resetState() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingDecoder implements OpusDecoder {
        byte[] lastInput;
        int lastFrames;
        int calls;

        @Override
        public short[] decode(byte[] data) {
            calls++;
            lastInput = data;
            return new short[]{1};
        }

        @Override
        public short[][] decode(byte[] input, int frames) {
            calls++;
            lastInput = input;
            lastFrames = frames;
            return new short[][]{{1}};
        }

        @Override
        public void resetState() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
        }
    }
}
