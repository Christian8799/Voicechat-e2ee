package voicechate2ee.config;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks authenticated encrypted speakers and structurally detected, unreadable E2EE envelopes. */
public final class EncryptedPlayers {
    public static final String WHITELIST_CODE = "whitelist";

    private static final Map<UUID, String> VERIFIED = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> DETECTED_ENCRYPTED = ConcurrentHashMap.newKeySet();
    private static volatile WeakReference<Object> session = new WeakReference<>(null);

    private EncryptedPlayers() {}

    /** Clear tracking when Simple Voice Chat connection/session changes. */
    public static void bindSession(Object token) {
        if (session.get() == token) return;
        synchronized (EncryptedPlayers.class) {
            if (session.get() != token) {
                session = new WeakReference<>(token);
                clear();
            }
        }
    }

    /** A packet was successfully authenticated/decrypted with the indicated key. */
    public static void markEncrypted(UUID player, String keyCode) {
        if (player == null || keyCode == null) return;
        VERIFIED.put(player, keyCode);
        DETECTED_ENCRYPTED.add(player);
    }

    /** The latest packet was not an E2EE envelope. */
    public static void markUnverified(UUID player) {
        if (player == null) return;
        VERIFIED.remove(player);
        DETECTED_ENCRYPTED.remove(player);
    }

    /** An E2EE-shaped envelope was observed, but local decryption/authentication failed. */
    public static void markDetectedEncrypted(UUID player) {
        if (player == null) return;
        DETECTED_ENCRYPTED.add(player);
        VERIFIED.remove(player);
    }

    public static boolean isDetectedEncrypted(UUID player) {
        return player != null && DETECTED_ENCRYPTED.contains(player);
    }

    /** Was the latest packet decrypted with this key or a whitelist voice key? */
    public static boolean isEncrypted(UUID player, String currentKeyCode) {
        if (player == null) return false;
        String stored = VERIFIED.get(player);
        return stored != null && (stored.equals(WHITELIST_CODE) || stored.equals(currentKeyCode));
    }

    public static void clear() {
        VERIFIED.clear();
        DETECTED_ENCRYPTED.clear();
    }
}
