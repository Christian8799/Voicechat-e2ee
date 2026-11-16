package voicechate2ee.config;

import voicechate2ee.crypto.VoiceKey;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.Properties;

/**
 * Holds the settings and the key. The file {@code config/voicechat-e2ee.properties} is checked for changes at
 * most once per second, so key changes take effect immediately without a restart.
 */
public final class E2EEState {

    /** Immutable state at a point in time. */
    public record Snapshot(boolean enabled, boolean requireEncryption, String keyText, VoiceKey key) {
    }

    private static final System.Logger LOG = System.getLogger("voicechat-e2ee");
    private static final long CHECK_INTERVAL_MS = 1000L;

    private static volatile Snapshot current = new Snapshot(false, true, "", null);
    private static volatile Path file;
    private static volatile long lastCheck;
    private static long lastModified = -1L;

    private E2EEState() {
    }

    /** Called on startup. On the first start, creates the file with a freshly generated key. */
    public static synchronized void init(Path configFile) {
        file = configFile;
        if (!Files.exists(configFile)) {
            try {
                write(true, true, VoiceKey.generatePassphrase());
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR, "Could not create the configuration: " + configFile, e);
            }
        }
        reload();
    }

    public static Snapshot snapshot() {
        long now = System.currentTimeMillis();
        if (file != null && now - lastCheck >= CHECK_INTERVAL_MS) {
            synchronized (E2EEState.class) {
                if (now - lastCheck >= CHECK_INTERVAL_MS) {
                    lastCheck = now;
                    reloadIfChanged();
                }
            }
        }
        return current;
    }

    /** Changes the settings and saves them. */
    public static synchronized void update(boolean enabled, boolean requireEncryption, String keyText) throws IOException {
        write(enabled, requireEncryption, keyText);
        reload();
    }

    /** Re-reads the file immediately (for {@code /vckey reload}). */
    public static synchronized void forceReload() {
        reload();
    }

    private static void reloadIfChanged() {
        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified != lastModified) {
                reload();
            }
        } catch (IOException e) {
            // File temporarily unreadable: keep the old state
        }
    }

    private static void reload() {
        try {
            Properties p = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(reader);
            }
            boolean enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
            boolean strict = Boolean.parseBoolean(p.getProperty("requireEncryption", "true").trim());
            String keyText = p.getProperty("key", "").trim();

            Snapshot old = current;
            VoiceKey key = null;
            if (!keyText.isEmpty()) {
                key = (old.key() != null && keyText.equals(old.keyText()))
                        ? old.key()
                        : VoiceKey.fromPassphrase(keyText);
            }
            current = new Snapshot(enabled, strict, keyText, key);
            lastModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read the configuration, keeping the previous state", e);
        }
    }

    private static void write(boolean enabled, boolean strict, String keyText) throws IOException {
        String content = "# Voice Chat E2EE\n"
                + "# Only players with the same key can hear each other in voice chat.\n"
                + "# You do not need to edit this file: all settings are in the game, next to the Simple Voice\n"
                + "# Chat window (incognito buttons). If you do edit it, changes take effect within one second.\n"
                + "# IMPORTANT: Only give the key to people who are allowed to hear you, and never post it publicly.\n"
                + "\n"
                + "# Encryption on/off. This controls how YOU send:\n"
                + "# true  = your microphone is sent encrypted with the key.\n"
                + "# false = you send unencrypted, as if this mod were not installed. You still hear players who speak\n"
                + "#         encrypted with your key (and you also hear all unencrypted players).\n"
                + "enabled=" + enabled + "\n"
                + "\n"
                + "# Only applies when enabled=true.\n"
                + "# true: Only packets with valid encryption are played (players without the key are silent).\n"
                + "# false: Players without this mod stay audible as well (you hear them unencrypted).\n"
                + "requireEncryption=" + strict + "\n"
                + "\n"
                + "# The shared key. Either pass on the one entered here, or enter the one from\n"
                + "# a friend. You can also use your own long passphrase.\n"
                + "key=" + escape(keyText) + "\n";

        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "").replace("\n", "");
    }
}
