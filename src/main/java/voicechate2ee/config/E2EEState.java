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
 * Haelt Einstellungen und Key. Die Datei {@code config/voicechat-e2ee.properties} wird maximal
 * einmal pro Sekunde auf Aenderungen geprueft, Key-Wechsel gelten also sofort ohne Neustart.
 */
public final class E2EEState {

    /** Unveraenderlicher Zustand zu einem Zeitpunkt. */
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

    /** Wird beim Start aufgerufen. Legt beim ersten Start die Datei mit einem frisch erzeugten Key an. */
    public static synchronized void init(Path configFile) {
        file = configFile;
        if (!Files.exists(configFile)) {
            try {
                write(true, true, VoiceKey.generatePassphrase());
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR, "Konnte Konfiguration nicht anlegen: " + configFile, e);
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

    /** Aendert Einstellungen und speichert sie. */
    public static synchronized void update(boolean enabled, boolean requireEncryption, String keyText) throws IOException {
        write(enabled, requireEncryption, keyText);
        reload();
    }

    /** Liest die Datei sofort neu ein (fuer {@code /vckey reload}). */
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
            // Datei kurzzeitig nicht lesbar: alten Zustand behalten
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
            LOG.log(System.Logger.Level.WARNING, "Konfiguration konnte nicht gelesen werden, alter Zustand bleibt aktiv", e);
        }
    }

    private static void write(boolean enabled, boolean strict, String keyText) throws IOException {
        String content = "# Voice Chat E2EE\n"
                + "# Nur Spieler mit demselben key koennen sich gegenseitig im Voicechat hoeren.\n"
                + "# Aenderungen an dieser Datei gelten nach spaetestens einer Sekunde, ohne Neustart.\n"
                + "# WICHTIG: Gib den key nur an Leute weiter, die dich hoeren duerfen, und poste ihn nirgends oeffentlich.\n"
                + "\n"
                + "# Verschluesselung an/aus. Aus = Voicechat verhaelt sich wie ohne diese Mod.\n"
                + "enabled=" + enabled + "\n"
                + "\n"
                + "# true: Nur Pakete mit gueltiger Verschluesselung werden abgespielt (Spieler ohne den key sind stumm).\n"
                + "# false: Spieler ohne diese Mod bleiben zusaetzlich hoerbar (du hoerst sie unverschluesselt).\n"
                + "requireEncryption=" + strict + "\n"
                + "\n"
                + "# Der gemeinsame Schluessel. Entweder den hier eingetragenen weitergeben oder den von\n"
                + "# einem Freund eintragen. Ein eigenes langes Passwort ist auch moeglich.\n"
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
