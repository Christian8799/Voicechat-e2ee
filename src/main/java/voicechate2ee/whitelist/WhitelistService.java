package voicechate2ee.whitelist;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.mixin.chat.KeyPairAccessor;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hands your voice key to the players on your whitelist, and receives theirs. Runs on the client thread; the audio
 * threads only talk to {@link ReceivedKeys}, {@link OwnVoiceKey} and {@link WhitelistState}.
 *
 * <ul>
 *   <li>Every second it looks at the whitelist: each whitelisted player who is online and has not got your current
 *       voice key yet receives a <b>grant</b> (two whispers, see {@link VoiceKeyGrant}).</li>
 *   <li>If you hear a whitelisted player but cannot read them (for example because you restarted the game), you send
 *       them a short <b>request</b> and they answer with a new grant.</li>
 *   <li>Incoming whispers carrying our header are taken out of the chat by {@code ChatListenerMixin}.</li>
 * </ul>
 */
public final class WhitelistService {

    private static final System.Logger LOG = System.getLogger("voicechat-e2ee");
    private static final WhitelistService INSTANCE = new WhitelistService();

    private static final int SCAN_INTERVAL_TICKS = 20;
    /**
     * Vanilla servers kick for "spam" when too many chat messages arrive in a burst. Every whisper counts as 20 and
     * the count falls by 1 per tick, kicking at about 200. We stay far below that, so a burst of 7 whispers is
     * sent immediately and after that one per second.
     */
    private static final int SPAM_COST = 20;
    private static final int SPAM_BUDGET = 140;
    private static final int MAX_QUEUE = 24;
    private static final long REQUEST_INTERVAL_MS = 20_000;
    private static final long GRANT_RETRY_MS = 60_000;
    private static final long NOTICE_COOLDOWN_MS = 30_000;

    private final SecureRandom random = new SecureRandom();
    private final ArrayDeque<String> outbox = new ArrayDeque<>();
    private final ChatCodec.Reassembler reassembler = new ChatCodec.Reassembler();
    /** The epoch of our key that each player has been sent in this session. */
    private final Map<UUID, Integer> granted = new HashMap<>();
    private final Map<UUID, Long> grantFailedAt = new HashMap<>();
    private final Map<UUID, Long> requestSentAt = new HashMap<>();
    private final Map<UUID, Long> requestHandledAt = new HashMap<>();
    private final Map<String, Long> notices = new HashMap<>();
    private int spamCounter;
    private int scanTicks;
    private boolean scanNow;
    private boolean connected;

    private WhitelistService() {
    }

    public static WhitelistService getInstance() {
        return INSTANCE;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.tick(client));
    }

    // ---------------------------------------------------------------- called by the commands

    /** A player was added: they get your key as soon as possible. */
    public void onAdded(UUID player) {
        granted.remove(player);
        grantFailedAt.remove(player);
        scanNow = true;
    }

    /**
     * A player was removed. Your voice key is replaced, so the removed player cannot keep listening with the old
     * one, and everybody who remains gets the new key.
     */
    public void onRemoved(UUID player) {
        ReceivedKeys.forget(player);
        EncryptedPlayers.markUnverified(player);
        OwnVoiceKey.rotate();
        granted.clear();
        grantFailedAt.clear();
        scanNow = true;
    }

    /** Sends your key to everybody on the whitelist again. */
    public void resend() {
        granted.clear();
        grantFailedAt.clear();
        scanNow = true;
    }

    public void onModeChanged() {
        scanNow = true;
    }

    /** Did this player get your current key in this session? */
    public boolean isGranted(UUID player) {
        Integer epoch = granted.get(player);
        return epoch != null && epoch == OwnVoiceKey.current().epoch();
    }

    // ---------------------------------------------------------------- tick

    private void tick(Minecraft mc) {
        UUID self = mc.getUser().getProfileId();
        WhitelistState.setSelfId(self);
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            if (connected) {
                connected = false;
                resetSession();
            }
            return;
        }
        connected = true;
        if (spamCounter > 0) {
            spamCounter--;
        }
        if (scanNow || ++scanTicks >= SCAN_INTERVAL_TICKS) {
            scanTicks = 0;
            scanNow = false;
            scan(mc, connection, self);
        }
        while (!outbox.isEmpty() && spamCounter + SPAM_COST <= SPAM_BUDGET) {
            connection.sendCommand(outbox.poll());
            spamCounter += SPAM_COST;
        }
    }

    private void resetSession() {
        outbox.clear();
        reassembler.clear();
        granted.clear();
        grantFailedAt.clear();
        requestSentAt.clear();
        requestHandledAt.clear();
        notices.clear();
        ReceivedKeys.clear();
        spamCounter = 0;
    }

    private void scan(Minecraft mc, ClientPacketListener connection, UUID self) {
        long now = System.currentTimeMillis();

        // 1. whitelisted players we hear but cannot read: ask them for their key
        for (UUID speaker : ReceivedKeys.drainMissing()) {
            PlayerInfo info = connection.getPlayerInfo(speaker);
            Long last = requestSentAt.get(speaker);
            if (info == null || !WhitelistState.contains(speaker) || (last != null && now - last < REQUEST_INTERVAL_MS)) {
                continue;
            }
            if (queue(info.getProfile().name(), VoiceKeyGrant.encodeRequest(now / 1000))) {
                requestSentAt.put(speaker, now);
            }
        }

        // 2. hand out our own key (only in whitelist mode and while encryption is on)
        if (!WhitelistState.isWhitelistMode() || !E2EEState.snapshot().enabled()) {
            return;
        }
        OwnVoiceKey.Key key = OwnVoiceKey.current();
        for (WhitelistState.Entry entry : WhitelistState.entries()) {
            UUID uuid = entry.uuid();
            Integer epoch = granted.get(uuid);
            Long failed = grantFailedAt.get(uuid);
            if (uuid.equals(self) || (epoch != null && epoch == key.epoch()) || (failed != null && now - failed < GRANT_RETRY_MS)) {
                continue;
            }
            PlayerInfo info = connection.getPlayerInfo(uuid);
            if (info == null) {
                continue;
            }
            PublicKey recipientKey = profileKeyOf(info);
            if (recipientKey == null) {
                grantFailedAt.put(uuid, now); // the server has no chat key for them (yet)
                continue;
            }
            PrivateKey ownKey = ownProfilePrivateKey(mc);
            if (ownKey == null) {
                return; // our own profile key is not loaded yet: try again at the next scan
            }
            try {
                VoiceKeyGrant.Grant grant = VoiceKeyGrant.create(ownKey, recipientKey, self, uuid,
                        key.bytes(), key.epoch(), key.nextCounter(), now / 1000);
                if (!queue(info.getProfile().name(), VoiceKeyGrant.encodeGrant(grant))) {
                    return; // queue is full: the rest follows at the next scan
                }
                granted.put(uuid, key.epoch());
            } catch (GeneralSecurityException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not create a voice key grant for " + entry.name(), e);
                grantFailedAt.put(uuid, now);
            }
        }
    }

    private boolean queue(String recipientName, byte[] blob) {
        String prefix = "w " + recipientName + " " + ChatCodec.HEADER;
        List<String> texts;
        try {
            texts = ChatCodec.encode(blob, random.nextInt(1 << 16), ChatCodec.maxChunkSize(prefix.length()));
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.WARNING, "Message does not fit into whispers", e);
            return true; // retrying would not help
        }
        if (outbox.size() + texts.size() > MAX_QUEUE) {
            return false;
        }
        for (String text : texts) {
            outbox.add(prefix + text);
        }
        return true;
    }

    // ---------------------------------------------------------------- incoming whispers

    /**
     * A whisper that contains our header arrived (called by the chat mixin).
     *
     * @param payload the text after the header
     */
    public void onChatPayload(String payload, UUID sender, String senderName) {
        UUID self = WhitelistState.selfId();
        ChatCodec.Fragment fragment = ChatCodec.decodeFragment(payload);
        if (self == null || sender.equals(self) || fragment == null) {
            return;
        }
        long now = System.currentTimeMillis();
        byte[] blob = reassembler.offer(sender, fragment, now);
        if (blob == null || blob.length == 0) {
            return;
        }
        try {
            switch (blob[0]) {
                case VoiceKeyGrant.TYPE_GRANT -> onGrant(blob, sender, senderName, self, now);
                case VoiceKeyGrant.TYPE_REQUEST -> onRequest(blob, sender, now);
                default -> { }
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Error while handling a voice key message", e);
        }
    }

    private void onGrant(byte[] blob, UUID sender, String senderName, UUID self, long now) {
        VoiceKeyGrant.Grant grant = VoiceKeyGrant.decodeGrant(blob);
        if (grant == null || !VoiceKeyGrant.isFresh(grant.timestampSeconds(), now / 1000)) {
            return;
        }
        if (!WhitelistState.contains(sender)) {
            // Not on the whitelist: we do not accept the key, but tell the user once in a while
            notice("unlisted:" + sender, now, senderName + " wants you to hear them, but is not on your whitelist. "
                    + "To allow it: /vcwl add " + senderName);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(sender);
        PublicKey senderKey = info == null ? null : profileKeyOf(info);
        PrivateKey ownKey = ownProfilePrivateKey(mc);
        if (senderKey == null || ownKey == null) {
            return;
        }
        // Signed by the Mojang-certified key of exactly this player, for exactly us
        if (!VoiceKeyGrant.verify(senderKey, sender, self, grant)) {
            return;
        }
        try {
            VoiceKeyGrant.Opened opened = VoiceKeyGrant.open(ownKey, sender, self, grant);
            boolean isNew = ReceivedKeys.put(sender, opened.epoch(), opened.key(), opened.firstCounter());
            Arrays.fill(opened.key(), (byte) 0);
            if (isNew) {
                notice("received:" + sender, now, "You can now hear " + senderName + " (they whitelisted you).");
            }
        } catch (GeneralSecurityException e) {
            // not for us, damaged or from an old profile key
        }
    }

    private void onRequest(byte[] blob, UUID sender, long now) {
        long timestamp = VoiceKeyGrant.decodeRequest(blob);
        if (timestamp < 0 || !VoiceKeyGrant.isFresh(timestamp, now / 1000)
                || !WhitelistState.contains(sender) || !WhitelistState.isWhitelistMode()) {
            return;
        }
        Long last = requestHandledAt.get(sender);
        if (last != null && now - last < REQUEST_INTERVAL_MS) {
            return;
        }
        requestHandledAt.put(sender, now);
        granted.remove(sender);
        grantFailedAt.remove(sender);
        scanNow = true;
    }

    // ---------------------------------------------------------------- helpers

    private void notice(String key, long now, String text) {
        notices.values().removeIf(t -> now - t > NOTICE_COOLDOWN_MS);
        if (notices.putIfAbsent(key, now) == null) {
            chat(text);
        }
    }

    public static void chat(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("[Voice E2EE] " + text));
    }

    /** The Mojang-certified chat key the server announced for this player, or null. */
    private static PublicKey profileKeyOf(PlayerInfo info) {
        RemoteChatSession session = info.getChatSession();
        if (session == null) {
            return null;
        }
        ProfilePublicKey ppk = session.profilePublicKey();
        //noinspection ConstantValue
        if (ppk == null) {
            return null;
        }
        return ppk.data().key();
    }

    /** Our own profile key, or null if it is not loaded (yet) or cannot be reached. */
    private static PrivateKey ownProfilePrivateKey(Minecraft mc) {
        if (!(mc.getProfileKeyPairManager() instanceof AccountProfileKeyPairManager manager)) {
            return null;
        }
        try {
            CompletableFuture<Optional<ProfileKeyPair>> future = ((KeyPairAccessor) (Object) manager).voicechatE2ee$keyPair();
            if (!future.isDone() || future.isCompletedExceptionally()) {
                return null;
            }
            return future.getNow(Optional.empty()).map(ProfileKeyPair::privateKey).orElse(null);
        } catch (RuntimeException e) {
            return null; // e.g. the accessor mixin was not applied
        }
    }
}
