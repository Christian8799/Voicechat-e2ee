package voicechate2ee.config;

import voicechate2ee.crypto.VoiceKey;

import java.io.IOException;

/**
 * Everything the settings GUI and the quick toggle button need that does not depend on Minecraft: the texts,
 * the rules (what can be clicked when) and the actions that change {@link E2EEState}.
 *
 * <p>Keeping this free of GUI classes means it is covered by the self-test.
 */
public final class SettingsModel {

    /** How a line of text should be colored. */
    public enum Tone {
        NORMAL, GOOD, WARN
    }

    /** One line of text for the GUI. */
    public record Line(String text, Tone tone) {
    }

    /** Result of the quick toggle. */
    public enum ToggleResult {
        /** The setting was changed. */
        CHANGED,
        /** Encryption cannot be turned on without a key: the settings screen should be opened. */
        NEEDS_KEY,
        /** The change could not be saved. */
        FAILED
    }

    /** Longest key that can be entered (the text field is limited to this, too). */
    public static final int MAX_KEY_LENGTH = 128;
    /** Keys shorter than this get a warning. Generated keys are 29 characters long. */
    public static final int SHORT_KEY_LENGTH = 12;

    private static final System.Logger LOG = System.getLogger("voicechat-e2ee");

    private SettingsModel() {
    }

    // ---------------------------------------------------------------- rules

    /** Removes line breaks and other control characters and surrounding spaces. */
    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        String s = sb.toString().trim();
        if (s.length() > MAX_KEY_LENGTH) {
            s = s.substring(0, MAX_KEY_LENGTH).trim();
        }
        return s;
    }

    /** True if the text differs from the key that is currently in use. */
    public static boolean isKeyDirty(E2EEState.Snapshot s, String editText) {
        return !normalizeKey(editText).equals(s.keyText());
    }

    /** The "Apply key" button: there must be a key and it must differ from the current one. */
    public static boolean canApply(E2EEState.Snapshot s, String editText) {
        return !normalizeKey(editText).isEmpty() && isKeyDirty(s, editText);
    }

    /** Turning encryption ON needs a key. Turning it OFF is always possible. */
    public static boolean canToggleEncryption(E2EEState.Snapshot s) {
        return s.enabled() || s.key() != null;
    }

    /** True if the microphone is actually sent encrypted right now. */
    public static boolean isSendingEncrypted(E2EEState.Snapshot s) {
        return s.enabled() && s.key() != null;
    }

    // ---------------------------------------------------------------- texts

    public static final String APPLY_LABEL = "Apply key";
    public static final String GENERATE_LABEL = "Generate new key";
    /** Shown on the generate button after the first click (replacing a key cannot be undone). */
    public static final String CONFIRM_LABEL = "Click to confirm";
    public static final String DONE_LABEL = "Done";

    public static String revealLabel(boolean keyVisible) {
        return keyVisible ? "Hide" : "Show";
    }

    public static String encryptionLabel(E2EEState.Snapshot s) {
        return "Encryption: " + (s.enabled() ? "ON" : "OFF");
    }

    public static String strictLabel(E2EEState.Snapshot s) {
        return "Only hear encrypted players: " + (s.requireEncryption() ? "ON" : "OFF");
    }

    public static String encryptionTooltip() {
        return "ON: your voice is sent encrypted with your key.\n"
                + "OFF: you send unencrypted, as if this mod were not installed.\n"
                + "You always hear players who speak encrypted with your key.";
    }

    public static String strictTooltip() {
        return "ON: only players who speak encrypted with your key are played.\n"
                + "OFF: players without the mod stay audible as well.\n"
                + "Only applies while encryption is ON.";
    }

    /** Tooltip of the incognito quick toggle button. */
    public static String quickToggleTooltip(E2EEState.Snapshot s) {
        if (s.key() == null) {
            return "Encryption: OFF\nNo key set. Click to set one up.";
        }
        if (s.enabled()) {
            return "Encryption: ON\nYou send encrypted.\nClick to turn it off.";
        }
        return "Encryption: OFF\nYou send unencrypted.\nClick to turn it on.";
    }

    public static String settingsTooltip() {
        return "Voice Chat E2EE settings";
    }

    public static Line sendLine(E2EEState.Snapshot s) {
        if (s.enabled() && s.key() == null) {
            return new Line("No key set: you send nothing!", Tone.WARN);
        }
        return s.enabled()
                ? new Line("You send encrypted.", Tone.GOOD)
                : new Line("You send unencrypted.", Tone.WARN);
    }

    public static Line hearLine(E2EEState.Snapshot s) {
        if (!s.enabled()) {
            return new Line("You still hear players with your key.", Tone.NORMAL);
        }
        return s.requireEncryption()
                ? new Line("Players without your key are muted.", Tone.NORMAL)
                : new Line("Players without the mod stay audible.", Tone.NORMAL);
    }

    /** The line under the key field: key code, or what is wrong with the key. */
    public static Line keyLine(E2EEState.Snapshot s, String editText) {
        if (!normalizeKey(editText).isEmpty() && isKeyDirty(s, editText)) {
            return new Line("Unsaved key. Press Apply to use it.", Tone.WARN);
        }
        if (s.key() == null) {
            return new Line("No key set. Generate or enter one.", Tone.WARN);
        }
        if (s.keyText().length() < SHORT_KEY_LENGTH) {
            return new Line("Key code: " + s.key().fingerprint() + " (short key!)", Tone.WARN);
        }
        return new Line("Key code: " + s.key().fingerprint() + " (same = same key)", Tone.NORMAL);
    }

    /** The three hint lines at the bottom of the settings screen. */
    public static String[] hintLines() {
        return new String[]{
                "Only share your key with people",
                "who may hear you. Never post it.",
                "Ctrl+C / Ctrl+V work in the key field."
        };
    }

    // -------------------------------------------------------------- actions

    /** Quick toggle: encryption on/off. */
    public static ToggleResult toggleEncryption() {
        E2EEState.Snapshot s = E2EEState.snapshot();
        if (!canToggleEncryption(s)) {
            return ToggleResult.NEEDS_KEY;
        }
        return save(!s.enabled(), s.requireEncryption(), s.keyText()) ? ToggleResult.CHANGED : ToggleResult.FAILED;
    }

    public static boolean setStrict(boolean strict) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        return save(s.enabled(), strict, s.keyText());
    }

    /** Uses the entered key. Returns false if it is empty or could not be saved. */
    public static boolean applyKey(String editText) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        String key = normalizeKey(editText);
        if (key.isEmpty()) {
            return false;
        }
        return save(s.enabled(), s.requireEncryption(), key);
    }

    /** Generates and saves a new random key. Returns it, or null if it could not be saved. */
    public static String generateKey() {
        E2EEState.Snapshot s = E2EEState.snapshot();
        String key = VoiceKey.generatePassphrase();
        return save(s.enabled(), s.requireEncryption(), key) ? key : null;
    }

    private static boolean save(boolean enabled, boolean strict, String keyText) {
        try {
            E2EEState.update(enabled, strict, keyText);
            return true;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the settings", e);
            return false;
        }
    }
}
