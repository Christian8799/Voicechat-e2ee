package voicechate2ee;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.config.SettingsModel;
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
import java.util.UUID;

/**
 * Simple self-test without Minecraft and without JUnit. Run with:
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
        testHearEncryptedWhenDisabled();
        testEncryptedPlayers();
        testSettingsModel();
        failures += WhitelistSelfTest.run();
        checks += WhitelistSelfTest.checks();
        System.out.println(checks + " checks, " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAILED: " + name);
        }
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static void testKey() throws Exception {
        VoiceKey a = VoiceKey.fromPassphrase("hello-world");
        VoiceKey b = VoiceKey.fromPassphrase("hello-world");
        VoiceKey c = VoiceKey.fromPassphrase("hello-world!");
        check(a.fingerprint().equals(b.fingerprint()), "same key -> same code");
        check(!a.fingerprint().equals(c.fingerprint()), "different key -> different code");
        String g = VoiceKey.generatePassphrase();
        check(g.matches("([A-Z2-9]{4}-){5}[A-Z2-9]{4}"), "generated key has the expected format: " + g);
        check(!g.equals(VoiceKey.generatePassphrase()), "generated keys are different");
    }

    private static void testCipher() throws Exception {
        VoiceKey key = VoiceKey.fromPassphrase("key-1");
        VoiceKey other = VoiceKey.fromPassphrase("key-2");
        PacketCipher cipher = new PacketCipher();
        byte[] plain = random(97);
        byte[] sealed = cipher.seal(plain, key);
        check(sealed.length == plain.length + 28, "overhead = 12 nonce + 16 tag");
        check(Arrays.equals(PacketCipher.open(sealed, key), plain), "round trip");
        check(PacketCipher.open(sealed, other) == null, "wrong key is rejected");
        boolean allRejected = true;
        for (int i = 0; i < sealed.length; i++) {
            byte[] t = sealed.clone();
            t[i] ^= 0x01;
            if (PacketCipher.open(t, key) != null) {
                allRejected = false;
            }
        }
        check(allRejected, "every single tampering is detected");
        check(PacketCipher.open(new byte[5], key) == null, "too short packet is rejected");
        check(PacketCipher.open(null, key) == null, "null is rejected");
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            nonces.add(Arrays.toString(Arrays.copyOf(cipher.seal(new byte[1], key), 12)));
        }
        check(nonces.size() == 20000, "nonces never repeat");
        check(!Arrays.equals(cipher.seal(plain, key), cipher.seal(plain, key)), "same data -> different ciphertext");
    }

    private static void testEnvelope() {
        int[] sizes = {0, 1, 2, 253, 254, 255, 256, 507, 508, 509, 510, 762, 1000, 1275};
        for (int n : sizes) {
            byte[] payload = random(n);
            byte[] wrapped = OpusEnvelope.wrap(payload);
            byte[] back = OpusEnvelope.unwrap(wrapped);
            check(back != null && Arrays.equals(back, payload), "envelope round trip, size " + n);
        }
        check(OpusEnvelope.unwrap(new byte[]{(byte) 0x78, 0x01, 0x02}) == null, "regular Opus packet is not recognized");
        check(OpusEnvelope.unwrap(new byte[0]) == null, "empty packet");
        check(OpusEnvelope.unwrap(null) == null, "null");
        byte[] w = OpusEnvelope.wrap(random(50));
        byte[] shortened = Arrays.copyOf(w, w.length - 1);
        check(OpusEnvelope.unwrap(shortened) == null, "truncated packet is rejected");
        byte[] extended = Arrays.copyOf(w, w.length + 1);
        check(OpusEnvelope.unwrap(extended) == null, "extended packet is rejected");
    }

    private static void testWrappers() throws Exception {
        Path dir = Files.createTempDirectory("e2ee-test");
        Path cfg = dir.resolve("voicechat-e2ee.properties");
        E2EEState.init(cfg);
        E2EEState.Snapshot s = E2EEState.snapshot();
        check(Files.exists(cfg), "config was created");
        check(s.enabled() && s.requireEncryption() && s.key() != null, "defaults: on, strict, key generated");

        byte[] opus = random(80);
        EncryptingOpusEncoder enc = new EncryptingOpusEncoder(new FakeEncoder(opus));
        RecordingDecoder rec = new RecordingDecoder();
        DecryptingOpusDecoder dec = new DecryptingOpusDecoder(rec);

        byte[] packet = enc.encode(new short[960]);
        check(packet.length > opus.length && !Arrays.equals(packet, opus), "packet is encrypted");
        check(!contains(packet, opus), "plaintext Opus does not appear in the packet");
        short[][] frames = dec.decode(packet, 3);
        check(frames.length == 1 && Arrays.equals(rec.lastInput, opus) && rec.lastFrames == 3,
                "receiver gets the original Opus packet (including frame count)");
        check(Arrays.equals(dec.decode(packet).length == 1 ? rec.lastInput : null, opus), "single decode as well");

        int callsBefore = rec.calls;
        check(dec.decode(opus, 1).length == 0 && rec.calls == callsBefore, "strict: plaintext packet is dropped");

        E2EEState.update(true, false, s.keyText());
        dec.decode(opus, 1);
        check(Arrays.equals(rec.lastInput, opus), "not strict: plaintext packet gets through");

        E2EEState.update(true, true, "OTHER-KEY");
        callsBefore = rec.calls;
        check(dec.decode(packet, 1).length == 0 && rec.calls == callsBefore, "wrong key: packet is dropped");
        byte[] packet2 = enc.encode(new short[960]);
        E2EEState.update(true, true, s.keyText());
        check(dec.decode(packet2, 1).length == 0, "packet encrypted with a different key is not read");
        check(dec.decode(packet, 1).length == 1, "key set back: old packet is readable again");

        E2EEState.update(false, true, s.keyText());
        check(Arrays.equals(enc.encode(new short[960]), opus), "off: encoder passes the original through");
        dec.decode(opus, 1);
        check(Arrays.equals(rec.lastInput, opus), "off: decoder passes the original through");

        E2EEState.update(true, true, "");
        check(enc.encode(new short[960]).length == 0, "no key: encoder sends nothing (fail-closed)");
        check(dec.decode(packet, 1).length == 0, "no key: decoder drops");

        String tricky = "a\\b c=d:e";
        E2EEState.update(true, true, tricky);
        check(E2EEState.snapshot().keyText().equals(tricky), "special characters in the key survive saving/loading");

        Files.writeString(cfg, "enabled=true\nrequireEncryption=true\nkey=EDITED-BY-HAND\n");
        Files.setLastModifiedTime(cfg, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
        Thread.sleep(1200);
        check(E2EEState.snapshot().keyText().equals("EDITED-BY-HAND"), "file change is picked up without a restart");
    }

    /** Even with encryption switched off, players who speak encrypted with your own key stay audible. */
    private static void testHearEncryptedWhenDisabled() throws Exception {
        Path dir = Files.createTempDirectory("e2ee-test-disabled");
        E2EEState.init(dir.resolve("voicechat-e2ee.properties"));
        String keyText = "SHARED-KEY";
        String otherKeyText = "STRANGER-KEY";
        VoiceKey myKey = VoiceKey.fromPassphrase(keyText);
        VoiceKey otherKey = VoiceKey.fromPassphrase(otherKeyText);

        byte[] opus = random(80);
        byte[] fromFriend = OpusEnvelope.wrap(new PacketCipher().seal(opus, myKey));
        byte[] fromStranger = OpusEnvelope.wrap(new PacketCipher().seal(opus, otherKey));

        RecordingDecoder rec = new RecordingDecoder();
        DecryptingOpusDecoder dec = new DecryptingOpusDecoder(rec);

        for (boolean strict : new boolean[]{true, false}) {
            // Encryption OFF (regardless of "strict"): a friend with the same key stays audible
            E2EEState.update(false, strict, keyText);
            rec.lastInput = null;
            short[][] frames = dec.decode(fromFriend, 2);
            check(frames.length == 1 && Arrays.equals(rec.lastInput, opus) && rec.lastFrames == 2,
                    "off (strict=" + strict + "): friend with the same key is decrypted and played");
            rec.lastInput = null;
            check(dec.decode(fromFriend).length == 1 && Arrays.equals(rec.lastInput, opus),
                    "off (strict=" + strict + "): single decode decrypts as well");

            // Unencrypted players stay audible
            rec.lastInput = null;
            dec.decode(opus, 1);
            check(Arrays.equals(rec.lastInput, opus), "off (strict=" + strict + "): plaintext player stays audible");

            // Different key: not readable, passed through unchanged (a real Opus decoder turns it into silence)
            rec.lastInput = null;
            dec.decode(fromStranger, 1);
            check(Arrays.equals(rec.lastInput, fromStranger),
                    "off (strict=" + strict + "): packet with a foreign key is not decrypted");
        }

        // Off and no key: nothing to decrypt, everything is passed through unchanged
        E2EEState.update(false, true, "");
        rec.lastInput = null;
        dec.decode(fromFriend, 1);
        check(Arrays.equals(rec.lastInput, fromFriend), "off without a key: packet is passed through unchanged");

        // On + strict: unchanged behavior (only readable packets get through)
        E2EEState.update(true, true, keyText);
        int calls = rec.calls;
        check(dec.decode(opus, 1).length == 0 && rec.calls == calls, "on+strict: plaintext is still dropped");
        check(dec.decode(fromStranger, 1).length == 0 && rec.calls == calls, "on+strict: foreign key is still dropped");
        check(dec.decode(fromFriend, 1).length == 1, "on+strict: friend is heard");
        // On + not strict: plaintext additionally gets through
        E2EEState.update(true, false, keyText);
        rec.lastInput = null;
        dec.decode(opus, 1);
        check(Arrays.equals(rec.lastInput, opus), "on+not strict: plaintext gets through");
    }

    /** Detection of which players speak encrypted (for the incognito icon). */
    private static void testEncryptedPlayers() throws Exception {
        Path dir = Files.createTempDirectory("e2ee-test-players");
        E2EEState.init(dir.resolve("voicechat-e2ee.properties"));
        String keyText = "ICON-KEY";
        E2EEState.update(true, false, keyText);
        VoiceKey key = VoiceKey.fromPassphrase(keyText);
        String code = key.fingerprint();

        byte[] opus = random(60);
        byte[] sealed = OpusEnvelope.wrap(new PacketCipher().seal(opus, key));
        UUID friend = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID lurker = UUID.randomUUID();

        EncryptedPlayers.clear();
        EncryptedPlayers.bindSession("session-1");
        DecryptingOpusDecoder friendDec = new DecryptingOpusDecoder(new RecordingDecoder(), friend);
        DecryptingOpusDecoder strangerDec = new DecryptingOpusDecoder(new RecordingDecoder(), stranger);

        check(!EncryptedPlayers.isEncrypted(friend, code), "before the first packet: no icon");
        friendDec.decode(sealed, 1);
        strangerDec.decode(opus, 1);
        check(EncryptedPlayers.isEncrypted(friend, code), "encrypted speaker gets the icon");
        check(!EncryptedPlayers.isEncrypted(stranger, code), "plaintext speaker gets no icon");
        check(!EncryptedPlayers.isEncrypted(lurker, code), "player without a packet gets no icon");
        check(!EncryptedPlayers.isEncrypted(null, code) && !EncryptedPlayers.isEncrypted(friend, null),
                "null is handled safely");

        // The icon stays even if the player is only silent (no new packets)
        check(EncryptedPlayers.isEncrypted(friend, code), "icon stays while silent");

        // With your own encryption switched off, the friend is still recognized
        E2EEState.update(false, true, keyText);
        friendDec.decode(sealed, 1);
        check(EncryptedPlayers.isEncrypted(friend, code), "off: encrypted friend still gets the icon");

        // Different key on my side: the old icon no longer counts
        check(!EncryptedPlayers.isEncrypted(friend, VoiceKey.fromPassphrase("NEW-KEY").fingerprint()),
                "key change: icon disappears immediately");

        // Friend switches to unencrypted: icon disappears with the next packet
        friendDec.decode(opus, 1);
        check(!EncryptedPlayers.isEncrypted(friend, code), "friend sends unencrypted: icon disappears");

        // Foreign key (encrypted, but not with mine): no icon
        byte[] foreign = OpusEnvelope.wrap(new PacketCipher().seal(opus, VoiceKey.fromPassphrase("SOMEONE-ELSE")));
        friendDec.decode(sealed, 1);
        check(EncryptedPlayers.isEncrypted(friend, code), "encrypted again: icon is back");
        friendDec.decode(foreign, 1);
        check(!EncryptedPlayers.isEncrypted(friend, code), "packet with a foreign key: no icon");

        // A new voice chat connection clears the list
        friendDec.decode(sealed, 1);
        check(EncryptedPlayers.isEncrypted(friend, code), "before session change: icon is there");
        EncryptedPlayers.bindSession("session-1");
        check(EncryptedPlayers.isEncrypted(friend, code), "same session: list is kept");
        EncryptedPlayers.bindSession("session-2");
        check(!EncryptedPlayers.isEncrypted(friend, code), "new session: list is empty");

        // A decoder without a speaker ID (null) must not break anything
        DecryptingOpusDecoder anonymous = new DecryptingOpusDecoder(new RecordingDecoder());
        check(anonymous.decode(sealed, 1).length == 1, "decoder without a speaker ID still decrypts");
    }

    /** The logic behind the settings window and the quick toggle button (everything except drawing). */
    private static void testSettingsModel() throws Exception {
        // normalizeKey
        check(SettingsModel.normalizeKey(null).isEmpty(), "key: null -> empty");
        check(SettingsModel.normalizeKey("  ABCD-EFGH  ").equals("ABCD-EFGH"), "key: surrounding spaces are removed");
        check(SettingsModel.normalizeKey("AB\r\nCD\tEF").equals("ABCDEF"), "key: line breaks and tabs are removed (paste)");
        check(SettingsModel.normalizeKey("my secret phrase").equals("my secret phrase"), "key: spaces inside are kept");
        check(SettingsModel.normalizeKey("x".repeat(500)).length() == SettingsModel.MAX_KEY_LENGTH, "key: length is capped");
        check(SettingsModel.normalizeKey(" \n ").isEmpty(), "key: only blanks -> empty");

        Path dir = Files.createTempDirectory("e2ee-test-settings");
        Path cfg = dir.resolve("voicechat-e2ee.properties");
        E2EEState.init(cfg);
        E2EEState.update(true, true, "GOOD-LONG-KEY-123456");

        // quick toggle
        check(SettingsModel.toggleEncryption() == SettingsModel.ToggleResult.CHANGED && !E2EEState.snapshot().enabled(),
                "toggle: on -> off");
        check(SettingsModel.toggleEncryption() == SettingsModel.ToggleResult.CHANGED && E2EEState.snapshot().enabled(),
                "toggle: off -> on");
        check(E2EEState.snapshot().keyText().equals("GOOD-LONG-KEY-123456") && E2EEState.snapshot().requireEncryption(),
                "toggle: key and strict stay untouched");
        SettingsModel.toggleEncryption();
        E2EEState.forceReload();
        check(!E2EEState.snapshot().enabled(), "toggle is saved to the file");
        SettingsModel.toggleEncryption();

        // strict
        check(SettingsModel.setStrict(false) && !E2EEState.snapshot().requireEncryption(), "strict off");
        check(E2EEState.snapshot().enabled(), "strict: encryption setting untouched");
        check(SettingsModel.setStrict(true) && E2EEState.snapshot().requireEncryption(), "strict on");

        // applying a key
        E2EEState.Snapshot s = E2EEState.snapshot();
        check(!SettingsModel.canApply(s, "GOOD-LONG-KEY-123456"), "apply: same key -> disabled");
        check(!SettingsModel.canApply(s, "  GOOD-LONG-KEY-123456 \n"), "apply: same key with spaces -> disabled");
        check(!SettingsModel.canApply(s, "") && !SettingsModel.canApply(s, "   "), "apply: empty -> disabled");
        check(SettingsModel.canApply(s, "ANOTHER-LONG-KEY-9999"), "apply: new key -> enabled");
        check(SettingsModel.keyLine(s, "ANOTHER-LONG-KEY-9999").tone() == SettingsModel.Tone.WARN
                && SettingsModel.keyLine(s, "ANOTHER-LONG-KEY-9999").text().startsWith("Unsaved"), "typed key is marked unsaved");
        check(SettingsModel.keyLine(s, "GOOD-LONG-KEY-123456").text().contains(s.key().fingerprint()),
                "saved key: key code is shown");
        check(!SettingsModel.applyKey("") && !SettingsModel.applyKey("\n") && E2EEState.snapshot().keyText().equals("GOOD-LONG-KEY-123456"),
                "empty key is never applied");
        String codeBefore = s.key().fingerprint();
        check(SettingsModel.applyKey("  ANOTHER-LONG-KEY-9999 \n") && E2EEState.snapshot().keyText().equals("ANOTHER-LONG-KEY-9999"),
                "apply: key is trimmed and saved");
        check(!E2EEState.snapshot().key().fingerprint().equals(codeBefore), "apply: key code changes with the key");
        E2EEState.forceReload();
        check(E2EEState.snapshot().keyText().equals("ANOTHER-LONG-KEY-9999"), "applied key is in the file");
        check(E2EEState.snapshot().enabled() && E2EEState.snapshot().requireEncryption(), "apply: switches untouched");

        // short key warning
        SettingsModel.applyKey("short");
        check(SettingsModel.keyLine(E2EEState.snapshot(), "short").tone() == SettingsModel.Tone.WARN
                && SettingsModel.keyLine(E2EEState.snapshot(), "short").text().contains("short"), "short key: warning");

        // generating a key
        E2EEState.update(false, false, E2EEState.snapshot().keyText());
        String old = E2EEState.snapshot().keyText();
        String generated = SettingsModel.generateKey();
        check(generated != null && generated.matches("([A-Z2-9]{4}-){5}[A-Z2-9]{4}"), "generate: format");
        check(generated != null && !generated.equals(old) && E2EEState.snapshot().keyText().equals(generated), "generate: new key is saved");
        check(!E2EEState.snapshot().enabled() && !E2EEState.snapshot().requireEncryption(),
                "generate: switches untouched (encryption stays off)");
        check(SettingsModel.keyLine(E2EEState.snapshot(), generated).tone() == SettingsModel.Tone.NORMAL,
                "generated key: no warning");

        // without a key
        E2EEState.update(false, true, "");
        s = E2EEState.snapshot();
        check(!SettingsModel.canToggleEncryption(s), "no key + off: cannot be turned on");
        check(SettingsModel.toggleEncryption() == SettingsModel.ToggleResult.NEEDS_KEY && !E2EEState.snapshot().enabled(),
                "no key + off: quick toggle asks for a key instead of turning on");
        check(SettingsModel.keyLine(s, "").tone() == SettingsModel.Tone.WARN, "no key: warning line");
        check(!SettingsModel.isSendingEncrypted(s), "no key: not sending encrypted");
        check(SettingsModel.quickToggleTooltip(s).contains("No key"), "no key: tooltip says so");
        E2EEState.update(true, true, "");
        s = E2EEState.snapshot();
        check(SettingsModel.canToggleEncryption(s) && !SettingsModel.isSendingEncrypted(s),
                "enabled without key: can be turned off, but is not sending encrypted");
        check(SettingsModel.sendLine(s).tone() == SettingsModel.Tone.WARN && SettingsModel.sendLine(s).text().contains("nothing"),
                "enabled without key: warns that nothing is sent");
        check(SettingsModel.toggleEncryption() == SettingsModel.ToggleResult.CHANGED && !E2EEState.snapshot().enabled(),
                "enabled without key: toggle turns it off");

        // texts
        E2EEState.update(true, true, "GOOD-LONG-KEY-123456");
        s = E2EEState.snapshot();
        check(SettingsModel.encryptionLabel(s).endsWith("ON") && SettingsModel.strictLabel(s).endsWith("ON"), "labels: on");
        check(SettingsModel.sendLine(s).text().equals("You send encrypted."), "send line: encrypted");
        check(SettingsModel.quickToggleTooltip(s).startsWith("Encryption: ON"), "quick toggle tooltip: on");
        E2EEState.update(false, false, "GOOD-LONG-KEY-123456");
        s = E2EEState.snapshot();
        check(SettingsModel.encryptionLabel(s).endsWith("OFF") && SettingsModel.strictLabel(s).endsWith("OFF"), "labels: off");
        check(SettingsModel.hearLine(s).text().contains("still hear"), "hear line: off still hears players with the key");
        check(SettingsModel.quickToggleTooltip(s).contains("turn it on"), "quick toggle tooltip: off");

        // every line of text has to fit into the window. The game's font is at most 6 pixels per character wide
        // for these texts; the window offers 228 pixels (buttons: 220, so a bit less)
        int maxLine = 38;
        int maxButton = 36;
        boolean linesFit = true;
        String[] keys = {"", "short", "GOOD-LONG-KEY-123456"};
        for (boolean enabled : new boolean[]{true, false}) {
            for (boolean strict : new boolean[]{true, false}) {
                for (String key : keys) {
                    E2EEState.update(enabled, strict, key);
                    E2EEState.Snapshot t = E2EEState.snapshot();
                    linesFit &= SettingsModel.sendLine(t).text().length() <= maxLine;
                    linesFit &= SettingsModel.hearLine(t).text().length() <= maxLine;
                    linesFit &= SettingsModel.keyLine(t, key).text().length() <= maxLine;
                    linesFit &= SettingsModel.keyLine(t, key + "x").text().length() <= maxLine;
                    linesFit &= SettingsModel.keyLine(t, "").text().length() <= maxLine;
                    linesFit &= SettingsModel.encryptionLabel(t).length() <= maxButton;
                    linesFit &= SettingsModel.strictLabel(t).length() <= maxButton;
                }
            }
        }
        for (String hint : SettingsModel.hintLines()) {
            linesFit &= hint.length() <= maxLine;
        }
        // the two buttons in the key row are 117 pixels wide each, the Show/Hide button 40
        int maxHalfButton = 17;
        linesFit &= SettingsModel.APPLY_LABEL.length() <= maxHalfButton;
        linesFit &= SettingsModel.GENERATE_LABEL.length() <= maxHalfButton;
        linesFit &= SettingsModel.CONFIRM_LABEL.length() <= maxHalfButton;
        linesFit &= SettingsModel.revealLabel(true).length() <= 5 && SettingsModel.revealLabel(false).length() <= 5;
        check(linesFit, "all texts fit into the settings window");
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
