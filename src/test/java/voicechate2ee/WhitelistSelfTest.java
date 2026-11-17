package voicechate2ee;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.config.SettingsModel;
import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.opus.DecryptingOpusDecoder;
import voicechate2ee.opus.EncryptingOpusEncoder;
import voicechate2ee.whitelist.ChatCodec;
import voicechate2ee.whitelist.OwnVoiceKey;
import voicechate2ee.whitelist.ReceivedKeys;
import voicechate2ee.whitelist.VoiceKeyGrant;
import voicechate2ee.whitelist.WhitelistCipher;
import voicechate2ee.whitelist.WhitelistState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Self-test of the whitelist system (no Minecraft, no JUnit). Called by {@link SelfTest}, or run on its own. */
public final class WhitelistSelfTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        int failed = run();
        System.out.println(checks + " checks, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    public static int checks() {
        return checks;
    }

    public static int run() throws Exception {
        testCodec();
        testGrant();
        testCipherAndReplay();
        testState();
        testVoicePath();
        return failures;
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

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    // ---------------------------------------------------------------- chat codec

    private static boolean allowedInChat(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c == 127 || c == 167 || Character.isSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    private static void testCodec() {
        Random rnd = new Random(42);
        for (int n = 0; n < 200; n++) {
            byte[] data = new byte[rnd.nextInt(600)];
            rnd.nextBytes(data);
            String text = ChatCodec.toText(data);
            byte[] back = ChatCodec.fromText(text);
            check(text.length() == (data.length * 8 + 13) / 14, "text length for " + data.length + " bytes");
            check(allowedInChat(text), "text is made of plain, allowed chat characters");
            check(back != null && back.length >= data.length && back.length <= data.length + 1
                    && Arrays.equals(Arrays.copyOf(back, data.length), data), "text round trip, " + data.length + " bytes");
        }
        check(ChatCodec.fromText("abc") == null, "characters outside the alphabet are rejected");
        check(ChatCodec.fromText("\u8E00") == null && ChatCodec.fromText("\u4DFF") == null, "alphabet borders are exact");
        List<String> wideId = ChatCodec.encode(new byte[]{1, 2, 3}, 0xABCD, 32);
        check(ChatCodec.decodeFragment(wideId.getFirst()).messageId() == 0xABCD, "fragment IDs preserve all 16 bits");
        check(ChatCodec.fromText("\u4E00\u8DFF") != null, "first and last character of the alphabet are accepted");

        // Every whisper fits into a chat command, for every possible name length, and reassembles in any order
        for (int nameLength = 3; nameLength <= 16; nameLength++) {
            String prefix = "w " + "a".repeat(nameLength) + " " + ChatCodec.HEADER;
            int chunk = ChatCodec.maxChunkSize(prefix.length());
            check(chunk > 350, "a whisper carries more than 350 bytes (name length " + nameLength + ": " + chunk + ")");
            for (int trial = 0; trial < 100; trial++) {
                byte[] blob = new byte[1 + rnd.nextInt(chunk * 2)];
                rnd.nextBytes(blob);
                List<String> texts = ChatCodec.encode(blob, rnd.nextInt(256), chunk);
                List<ChatCodec.Fragment> fragments = new ArrayList<>();
                boolean fits = true;
                for (String t : texts) {
                    fits &= (prefix + t).length() <= ChatCodec.MAX_COMMAND_LENGTH;
                    fragments.add(ChatCodec.decodeFragment(t));
                }
                check(fits, "every whisper is at most 255 characters long");
                Collections.shuffle(fragments, rnd);
                ChatCodec.Reassembler reassembler = new ChatCodec.Reassembler();
                UUID sender = UUID.randomUUID();
                byte[] result = null;
                for (ChatCodec.Fragment f : fragments) {
                    check(f != null, "fragment parses");
                    byte[] r = reassembler.offer(sender, f, 1000);
                    if (r != null) {
                        result = r;
                    }
                }
                check(result != null && Arrays.equals(result, blob), "message is reassembled (" + blob.length + " bytes)");
            }
        }
        boolean refused = false;
        try {
            ChatCodec.encode(new byte[5000], 1, 300);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "a message that needs too many fragments is refused");

        for (int i = 0; i < 3000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = rnd.nextInt(400); j > 0; j--) {
                sb.append((char) rnd.nextInt(0x10000));
            }
            ChatCodec.decodeFragment(sb.toString());
        }
        check(ChatCodec.decodeFragment(null) == null && ChatCodec.decodeFragment("") == null, "empty input is no fragment");
        check(true, "random text never crashes the parser");

        ChatCodec.Reassembler r = new ChatCodec.Reassembler();
        UUID spammer = UUID.randomUUID();
        for (int id = 0; id < 4; id++) {
            check(r.offer(spammer, new ChatCodec.Fragment(id, 0, 2, new byte[]{1}), 0) == null, "pending message " + id);
        }
        r.offer(spammer, new ChatCodec.Fragment(99, 0, 2, new byte[]{1}), 0);
        check(r.offer(spammer, new ChatCodec.Fragment(99, 1, 2, new byte[]{2}), 0) == null, "a fifth incomplete message from one sender is ignored");
        check(r.offer(spammer, new ChatCodec.Fragment(0, 1, 2, new byte[]{2}), 1000) != null, "messages complete within the time limit");
        check(r.offer(spammer, new ChatCodec.Fragment(1, 1, 2, new byte[]{2}), 40_000) == null, "incomplete messages expire");
    }

    // ---------------------------------------------------------------- grants

    private static void testGrant() throws Exception {
        KeyPair alice = rsa();
        KeyPair bob = rsa();
        KeyPair eve = rsa();
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        byte[] voiceKey = random(32);
        long now = 1_800_000_000L;

        VoiceKeyGrant.Grant grant = VoiceKeyGrant.create(alice.getPrivate(), bob.getPublic(), aliceId, bobId, voiceKey, 777, 1234L, now);
        byte[] blob = VoiceKeyGrant.encodeGrant(grant);
        check(blob.length == 521, "a grant is 521 bytes, which is two whispers");
        check(ChatCodec.encode(blob, 1, ChatCodec.maxChunkSize(("w " + "a".repeat(16) + " " + ChatCodec.HEADER).length())).size() == 2,
                "a grant fits into two whispers even with the longest name");
        VoiceKeyGrant.Grant decoded = VoiceKeyGrant.decodeGrant(blob);
        check(decoded != null && decoded.epoch() == 777 && decoded.timestampSeconds() == now, "grant round trip");
        check(VoiceKeyGrant.verify(alice.getPublic(), aliceId, bobId, decoded), "signature of the sender is valid");
        VoiceKeyGrant.Opened opened = VoiceKeyGrant.open(bob.getPrivate(), aliceId, bobId, decoded);
        check(Arrays.equals(opened.key(), voiceKey) && opened.epoch() == 777 && opened.firstCounter() == 1234L, "recipient gets key, epoch and first counter");

        check(!VoiceKeyGrant.verify(eve.getPublic(), aliceId, bobId, decoded), "signature of somebody else is rejected");
        check(!VoiceKeyGrant.verify(alice.getPublic(), aliceId, UUID.randomUUID(), decoded), "a grant for another recipient is rejected");
        check(!VoiceKeyGrant.verify(alice.getPublic(), UUID.randomUUID(), bobId, decoded), "a grant claiming another sender is rejected");
        check(!VoiceKeyGrant.verify(alice.getPublic(), aliceId, bobId,
                new VoiceKeyGrant.Grant(now + 1, decoded.epoch(), decoded.wrappedKey(), decoded.signature())), "changed timestamp is detected");
        check(!VoiceKeyGrant.verify(alice.getPublic(), aliceId, bobId,
                new VoiceKeyGrant.Grant(now, decoded.epoch() + 1, decoded.wrappedKey(), decoded.signature())), "changed epoch is detected");
        byte[] tampered = decoded.wrappedKey().clone();
        tampered[10] ^= 1;
        check(!VoiceKeyGrant.verify(alice.getPublic(), aliceId, bobId,
                new VoiceKeyGrant.Grant(now, decoded.epoch(), tampered, decoded.signature())), "changed encrypted key is detected");

        boolean eveOpened = true;
        try {
            VoiceKeyGrant.open(eve.getPrivate(), aliceId, bobId, decoded);
        } catch (java.security.GeneralSecurityException e) {
            eveOpened = false;
        }
        check(!eveOpened, "a third party cannot open the grant");
        boolean wrongContext = true;
        try {
            VoiceKeyGrant.open(bob.getPrivate(), aliceId, UUID.randomUUID(), decoded);
        } catch (java.security.GeneralSecurityException e) {
            wrongContext = false;
        }
        check(!wrongContext, "the encrypted key is bound to sender and recipient");

        check(VoiceKeyGrant.decodeGrant(Arrays.copyOf(blob, 520)) == null && VoiceKeyGrant.decodeGrant(new byte[0]) == null, "damaged grants are rejected");
        check(VoiceKeyGrant.decodeGrant(null) == null, "null is not a grant");
        long ts = VoiceKeyGrant.decodeRequest(VoiceKeyGrant.encodeRequest(now));
        check(ts == now && VoiceKeyGrant.decodeRequest(blob) == -1 && VoiceKeyGrant.decodeRequest(new byte[3]) == -1, "request round trip");
        check(VoiceKeyGrant.isFresh(now, now + 299) && !VoiceKeyGrant.isFresh(now, now + 301) && !VoiceKeyGrant.isFresh(now + 301, now), "five minute window");
    }

    // ---------------------------------------------------------------- packet cipher, replay window

    private static void testCipherAndReplay() throws Exception {
        byte[] key = random(32);
        UUID speaker = UUID.randomUUID();
        byte[] plain = random(80);
        byte[] sealed = WhitelistCipher.seal(plain, key, 0xCAFEBABE, 5L, speaker);
        check(sealed.length == plain.length + 28, "overhead is 12 nonce + 16 tag, as in key mode");
        check(WhitelistCipher.epochOf(sealed) == 0xCAFEBABE && WhitelistCipher.counterOf(sealed) == 5L, "epoch and counter are readable from the nonce");
        check(Arrays.equals(WhitelistCipher.open(sealed, key, speaker), plain), "round trip");
        check(WhitelistCipher.open(sealed, random(32), speaker) == null, "wrong key is rejected");
        check(WhitelistCipher.open(sealed, key, UUID.randomUUID()) == null, "packets cannot be passed off as another speaker's");
        boolean allRejected = true;
        for (int i = 0; i < sealed.length; i++) {
            byte[] t = sealed.clone();
            t[i] ^= 1;
            allRejected &= WhitelistCipher.open(t, key, speaker) == null;
        }
        check(allRejected, "every single changed byte is detected");
        check(WhitelistCipher.open(new byte[10], key, speaker) == null && WhitelistCipher.open(null, key, speaker) == null, "too short / null");

        UUID alice = UUID.randomUUID();
        ReceivedKeys.clear();
        ReceivedKeys.put(alice, 1, key, 100L);
        byte[] p100 = WhitelistCipher.seal(plain, key, 1, 100L, alice);
        byte[] p101 = WhitelistCipher.seal(plain, key, 1, 101L, alice);
        byte[] p099 = WhitelistCipher.seal(plain, key, 1, 99L, alice);
        byte[] p300 = WhitelistCipher.seal(plain, key, 1, 300L, alice);
        check(ReceivedKeys.open(alice, p101) != null, "first packet is accepted");
        check(ReceivedKeys.open(alice, p101) == null, "the same packet again (replay) is rejected");
        check(ReceivedKeys.open(alice, p100) != null, "slightly reordered packet is accepted");
        check(ReceivedKeys.open(alice, p100) == null, "...but only once");
        check(ReceivedKeys.open(alice, p099) == null, "packets from before the grant are rejected");
        check(ReceivedKeys.open(alice, p300) != null, "jump forward");
        check(ReceivedKeys.open(alice, WhitelistCipher.seal(plain, key, 1, 200L, alice)) == null, "more than 64 packets behind: rejected");
        check(ReceivedKeys.open(alice, WhitelistCipher.seal(plain, key, 1, 299L, alice)) != null, "within the window: accepted");
        check(!ReceivedKeys.put(alice, 1, random(32), 0L), "a grant for an epoch we have is ignored");
        check(ReceivedKeys.open(alice, p300) == null, "...so it cannot reset the replay protection");
        check(ReceivedKeys.put(alice, 2, random(32), 0L) && ReceivedKeys.put(alice, 3, random(32), 0L), "newer epochs are added");
        check(ReceivedKeys.open(alice, WhitelistCipher.seal(plain, key, 1, 400L, alice)) == null, "only the last two epochs are kept");
        check(!ReceivedKeys.put(alice, 1, key, 100L), "an evicted old epoch cannot be reinstalled by replaying its grant");
        check(ReceivedKeys.open(UUID.randomUUID(), p101) == null && ReceivedKeys.open(null, p101) == null, "unknown speaker");
        ReceivedKeys.forget(alice);
        check(!ReceivedKeys.has(alice), "forget removes the keys");
    }

    // ---------------------------------------------------------------- whitelist file

    private static void testState() throws Exception {
        Path dir = Files.createTempDirectory("e2ee-wl");
        Path file = dir.resolve("voicechat-e2ee-whitelist.txt");
        WhitelistState.init(file);
        check(WhitelistState.mode() == WhitelistState.Mode.KEY && WhitelistState.entries().isEmpty() && WhitelistState.loadError() == null,
                "defaults: shared key mode, empty list");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        check(WhitelistState.add(a, "Alice"), "add");
        check(!WhitelistState.add(a, "Alice2"), "adding again updates the name");
        WhitelistState.add(b, "Bo, b\n");
        WhitelistState.setMode(WhitelistState.Mode.WHITELIST);
        check(WhitelistState.contains(a) && WhitelistState.contains(b) && !WhitelistState.contains(UUID.randomUUID()) && !WhitelistState.contains(null), "contains");
        WhitelistState.init(file);
        check(WhitelistState.isWhitelistMode() && WhitelistState.entries().size() == 2 && WhitelistState.findByName("alice2") != null
                && WhitelistState.findByName("Bob") != null, "everything survives a restart (names are cleaned up)");
        check(WhitelistState.remove(a) && !WhitelistState.remove(a) && !WhitelistState.contains(a), "remove");

        String before = Files.readString(file);
        Files.writeString(file, before + "something=odd\n");
        WhitelistState.init(file);
        check(WhitelistState.loadError() != null && WhitelistState.mode() == WhitelistState.Mode.KEY && WhitelistState.entries().isEmpty(),
                "a damaged file is reported, mode falls back to the shared key");
        boolean refused = false;
        try {
            WhitelistState.add(UUID.randomUUID(), "X");
        } catch (java.io.IOException e) {
            refused = true;
        }
        check(refused && Files.readString(file).endsWith("something=odd\n"), "a damaged file is never overwritten");
        Files.delete(file);
        WhitelistState.init(file);
        check(WhitelistState.loadError() == null, "deleting the file fixes it");
    }

    // ---------------------------------------------------------------- the real voice path

    private static final class FakeEncoder implements OpusEncoder {
        private final byte[] output;

        FakeEncoder(byte[] output) {
            this.output = output;
        }

        @Override
        public byte[] encode(short[] rawAudio) {
            return output;
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

        @Override
        public short[] decode(byte[] data) {
            lastInput = data;
            return new short[]{1};
        }

        @Override
        public short[][] decode(byte[] input, int frames) {
            lastInput = input;
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

    private static void testVoicePath() throws Exception {
        Path dir = Files.createTempDirectory("e2ee-wl-voice");
        E2EEState.init(dir.resolve("voicechat-e2ee.properties"));
        E2EEState.update(true, true, ""); // encryption on, strict, no shared key
        WhitelistState.init(dir.resolve("voicechat-e2ee-whitelist.txt"));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID mallory = UUID.randomUUID();
        WhitelistState.setSelfId(alice);
        EncryptedPlayers.clear();
        ReceivedKeys.clear();

        byte[] opus = random(80);
        EncryptingOpusEncoder encoder = new EncryptingOpusEncoder(new FakeEncoder(opus));

        // Shared key mode without a key: nothing is sent (as before)
        check(encoder.encode(new short[960]).length == 0, "key mode without a key: fail-closed");

        // Whitelist mode: Alice sends with her own voice key, no shared key needed
        WhitelistState.setMode(WhitelistState.Mode.WHITELIST);
        check(SettingsModel.isSendingEncrypted(E2EEState.snapshot()) && SettingsModel.canToggleEncryption(E2EEState.snapshot()),
                "whitelist mode needs no shared key");
        byte[] packet = encoder.encode(new short[960]);
        check(packet.length > opus.length && OpusEnvelope.unwrap(packet) != null, "whitelist mode: packet is encrypted and hidden in the Opus envelope");

        RecordingDecoder bobRec = new RecordingDecoder();
        DecryptingOpusDecoder bobDecoder = new DecryptingOpusDecoder(bobRec, alice);
        check(bobDecoder.decode(packet, 1).length == 0 && bobRec.lastInput == null, "before the grant: Bob hears nothing (strict)");
        check(!EncryptedPlayers.isEncrypted(alice, null), "no icon without a key");

        // Bob is not on his own whitelist yet: no request. Then he whitelists Alice and the missing key is noticed.
        check(ReceivedKeys.drainMissing().isEmpty(), "a speaker who is not on the whitelist is not requested");
        WhitelistState.add(alice, "Alice");
        bobDecoder.decode(packet, 1);
        check(ReceivedKeys.drainMissing().contains(alice), "a whitelisted speaker we cannot read is noticed, so the key can be requested");

        // The grant travels through the whisper channel and is opened with the profile keys
        KeyPair aliceProfile = rsa();
        KeyPair bobProfile = rsa();
        OwnVoiceKey.Key key = OwnVoiceKey.current();
        VoiceKeyGrant.Grant grant = VoiceKeyGrant.create(aliceProfile.getPrivate(), bobProfile.getPublic(), alice, bob,
                key.bytes(), key.epoch(), key.nextCounter(), System.currentTimeMillis() / 1000);
        List<String> whispers = ChatCodec.encode(VoiceKeyGrant.encodeGrant(grant), 7, ChatCodec.maxChunkSize(30));
        ChatCodec.Reassembler reassembler = new ChatCodec.Reassembler();
        byte[] received = null;
        for (String w : whispers) {
            byte[] r = reassembler.offer(alice, ChatCodec.decodeFragment(w), 0);
            if (r != null) {
                received = r;
            }
        }
        VoiceKeyGrant.Grant got = VoiceKeyGrant.decodeGrant(received);
        check(got != null && VoiceKeyGrant.verify(aliceProfile.getPublic(), alice, bob, got), "Bob verifies the grant that came through whispers");
        VoiceKeyGrant.Opened opened = VoiceKeyGrant.open(bobProfile.getPrivate(), alice, bob, got);
        ReceivedKeys.put(alice, opened.epoch(), opened.key(), opened.firstCounter());

        byte[] next = encoder.encode(new short[960]);
        check(bobDecoder.decode(next, 3).length == 1 && Arrays.equals(bobRec.lastInput, opus), "after the grant: Bob hears Alice");
        check(EncryptedPlayers.isEncrypted(alice, null), "Alice gets the incognito icon, also without a shared key");
        check(bobDecoder.decode(next, 3).length == 0, "the same packet again is a replay and is dropped");

        // Somebody else (also whitelisted by Bob, but without Alice's key) hears nothing
        RecordingDecoder malloryRec = new RecordingDecoder();
        DecryptingOpusDecoder malloryDecoder = new DecryptingOpusDecoder(malloryRec, mallory);
        check(malloryDecoder.decode(encoder.encode(new short[960]), 1).length == 0, "a player without the key hears nothing");
        // ... and the server cannot pass Alice's packets off as someone else's
        check(malloryDecoder.decode(encoder.encode(new short[960]), 1).length == 0 && malloryRec.lastInput == null, "packets are bound to their speaker");

        // Alice removes Bob: her key is replaced, Bob's old key no longer works
        int oldEpoch = key.epoch();
        OwnVoiceKey.Key rotated = OwnVoiceKey.rotate();
        check(rotated.epoch() != oldEpoch, "a new epoch after rotation");
        byte[] afterRemoval = encoder.encode(new short[960]);
        check(bobDecoder.decode(afterRemoval, 1).length == 0, "after removal Bob cannot hear Alice anymore");
        check(!EncryptedPlayers.isEncrypted(alice, null), "...and the icon is gone");
        ReceivedKeys.forget(alice);

        // A grant for the new key makes her audible again (this is what happens for the players who stay)
        VoiceKeyGrant.Grant second = VoiceKeyGrant.create(aliceProfile.getPrivate(), bobProfile.getPublic(), alice, bob,
                rotated.bytes(), rotated.epoch(), rotated.nextCounter(), System.currentTimeMillis() / 1000);
        VoiceKeyGrant.Opened openedSecond = VoiceKeyGrant.open(bobProfile.getPrivate(), alice, bob, VoiceKeyGrant.decodeGrant(VoiceKeyGrant.encodeGrant(second)));
        ReceivedKeys.put(alice, openedSecond.epoch(), openedSecond.key(), openedSecond.firstCounter());
        check(bobDecoder.decode(encoder.encode(new short[960]), 1).length == 1, "the new key works");

        // Encryption off: the encoder passes the original through, whitelist or not
        E2EEState.update(false, true, "");
        check(Arrays.equals(encoder.encode(new short[960]), opus), "encryption off: nothing is encrypted");
        E2EEState.update(true, true, "");

        // Unknown own ID: fail-closed
        WhitelistState.setSelfId(null);
        check(encoder.encode(new short[960]).length == 0, "whitelist mode without own ID: fail-closed");
        WhitelistState.setSelfId(alice);

        // Texts of the settings
        check(SettingsModel.sendLine(E2EEState.snapshot()).text().contains("whitelist"), "send line mentions the whitelist");
        check(SettingsModel.hearLine(E2EEState.snapshot()).text().contains("whitelist"), "hear line mentions the whitelist");
        WhitelistState.setMode(WhitelistState.Mode.KEY);
        check(!SettingsModel.isSendingEncrypted(E2EEState.snapshot()), "back in key mode without a key: not sending encrypted");
        ReceivedKeys.clear();
        EncryptedPlayers.clear();
    }
}
