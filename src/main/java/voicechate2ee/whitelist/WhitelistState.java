package voicechate2ee.whitelist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The whitelist (who may hear you) and the mode (shared key or whitelist), stored in
 * {@code config/voicechat-e2ee-whitelist.txt}.
 *
 * <p>Reads are lock-free (the audio threads need them), changes replace an immutable snapshot and are saved
 * atomically. If the file exists but cannot be understood it is <b>never overwritten</b>: changes are refused
 * until the user fixes or deletes it, and the mode stays on the shared key.
 */
public final class WhitelistState {

    public enum Mode {
        /** Everybody with the same shared key hears you (the original behavior). */
        KEY,
        /** Only players on your whitelist hear you. */
        WHITELIST
    }

    public record Entry(UUID uuid, String name) {
    }

    private record Snapshot(Mode mode, Map<UUID, Entry> players) {
    }

    private static final String HEADER = "voicechat-e2ee-whitelist-v1";

    private static volatile Snapshot current = new Snapshot(Mode.KEY, Map.of());
    private static volatile Path file;
    private static volatile String loadError;
    private static volatile UUID selfId;

    private WhitelistState() {
    }

    /** Loads the file (if it exists). Never throws: problems end up in {@link #loadError()}. */
    public static synchronized void init(Path configFile) {
        file = configFile;
        loadError = null;
        current = new Snapshot(Mode.KEY, Map.of());
        if (!Files.exists(configFile)) {
            return;
        }
        try {
            current = parse(Files.readAllLines(configFile, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            loadError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private static Snapshot parse(List<String> lines) throws IOException {
        Mode mode = Mode.KEY;
        Map<UUID, Entry> players = new LinkedHashMap<>();
        boolean headerSeen = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                if (!line.equals(HEADER)) {
                    throw new IOException("unknown file format");
                }
                headerSeen = true;
            } else if (line.startsWith("mode=")) {
                mode = Mode.valueOf(line.substring(5).strip().toUpperCase(java.util.Locale.ROOT));
            } else if (line.startsWith("player=")) {
                String[] parts = line.substring(7).split(",", 2);
                UUID uuid = UUID.fromString(parts[0].strip());
                String name = parts.length > 1 ? parts[1].strip() : uuid.toString();
                players.put(uuid, new Entry(uuid, name));
            } else {
                throw new IOException("unknown line: " + line);
            }
        }
        if (!headerSeen) {
            throw new IOException("unknown file format");
        }
        return new Snapshot(mode, Collections.unmodifiableMap(players));
    }

    // ---------------------------------------------------------------- reading

    public static Mode mode() {
        return current.mode;
    }

    public static boolean isWhitelistMode() {
        return current.mode == Mode.WHITELIST;
    }

    public static boolean contains(UUID uuid) {
        return uuid != null && current.players.containsKey(uuid);
    }

    public static List<Entry> entries() {
        return new ArrayList<>(current.players.values());
    }

    public static Entry findByName(String name) {
        for (Entry e : current.players.values()) {
            if (e.name().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    /** Null if the file was read fine (or does not exist yet). */
    public static String loadError() {
        return loadError;
    }

    /** Your own UUID, set by the client thread. The audio threads need it and must not touch Minecraft. */
    public static UUID selfId() {
        return selfId;
    }

    public static void setSelfId(UUID id) {
        selfId = id;
    }

    // ---------------------------------------------------------------- changing

    public static synchronized void setMode(Mode mode) throws IOException {
        Snapshot old = current;
        save(new Snapshot(mode, old.players));
    }

    /** @return false if the player was already on the list (the name is updated) */
    public static synchronized boolean add(UUID uuid, String name) throws IOException {
        Snapshot old = current;
        Map<UUID, Entry> players = new LinkedHashMap<>(old.players);
        boolean added = !players.containsKey(uuid);
        players.put(uuid, new Entry(uuid, name.replaceAll("[\\s,]", "")));
        save(new Snapshot(old.mode, Collections.unmodifiableMap(players)));
        return added;
    }

    /** @return false if the player was not on the list */
    public static synchronized boolean remove(UUID uuid) throws IOException {
        Snapshot old = current;
        if (!old.players.containsKey(uuid)) {
            return false;
        }
        Map<UUID, Entry> players = new LinkedHashMap<>(old.players);
        players.remove(uuid);
        save(new Snapshot(old.mode, Collections.unmodifiableMap(players)));
        return true;
    }

    private static void save(Snapshot next) throws IOException {
        if (file == null) {
            throw new IOException("not initialized");
        }
        if (loadError != null) {
            throw new IOException("the whitelist file is damaged (" + loadError + "), fix or delete it first");
        }
        StringBuilder sb = new StringBuilder(HEADER).append('\n')
                .append("# Voice Chat E2EE: who may hear you. Edit with /vcwl in the game.\n")
                .append("# mode=key       everybody with your shared key hears you\n")
                .append("# mode=whitelist only the players below hear you\n")
                .append("mode=").append(next.mode.name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        for (Entry e : next.players.values()) {
            sb.append("player=").append(e.uuid()).append(',').append(e.name()).append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        current = next;
    }
}
