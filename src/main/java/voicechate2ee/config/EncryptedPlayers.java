package voicechate2ee.config;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which players speak encrypted with <b>your</b> key. The receive path adds a player as soon as a
 * packet from them has been decrypted successfully, and removes them again as soon as a packet arrives from
 * them that is not readable with your key (e.g. unencrypted or a different key). The UI asks this class
 * who gets the incognito icon.
 *
 * <p>A player only counts as encrypted while the stored key code matches your current key. If you change the
 * key, all icons disappear immediately until people speak with the new key again. The list is cleared on every
 * new voice chat connection (server change).
 *
 * <p>This class knows neither Minecraft nor Simple Voice Chat and is thread-safe: it is written from the
 * audio threads and read from the render thread.
 */
public final class EncryptedPlayers {

    private static final Map<UUID, String> VERIFIED = new ConcurrentHashMap<>();
    private static volatile WeakReference<Object> session = new WeakReference<>(null);

    private EncryptedPlayers() {
    }

    /**
     * Binds the list to a voice chat connection (e.g. the {@code ClientVoicechat} instance). If it is a
     * different one than before, all old entries are discarded.
     */
    public static void bindSession(Object token) {
        if (session.get() == token) {
            return;
        }
        synchronized (EncryptedPlayers.class) {
            if (session.get() != token) {
                session = new WeakReference<>(token);
                VERIFIED.clear();
            }
        }
    }

    /** A packet from this player was decrypted with the key that has the key code {@code keyCode}. */
    public static void markEncrypted(UUID player, String keyCode) {
        if (player == null || keyCode == null) {
            return;
        }
        if (!keyCode.equals(VERIFIED.get(player))) {
            VERIFIED.put(player, keyCode);
        }
    }

    /** A packet from this player is not readable with your key (unencrypted or a different key). */
    public static void markUnverified(UUID player) {
        if (player != null) {
            VERIFIED.remove(player);
        }
    }

    /** Is this player last heard speaking encrypted with the key that currently has the key code {@code currentKeyCode}? */
    public static boolean isEncrypted(UUID player, String currentKeyCode) {
        return player != null && currentKeyCode != null && currentKeyCode.equals(VERIFIED.get(player));
    }

    /** Forgets all players. */
    public static void clear() {
        VERIFIED.clear();
    }
}
