package voicechate2ee.gui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Opens the settings window a moment later. Used by the {@code /vckey gui} command, which is only a
 * fallback in case the buttons in the Simple Voice Chat window are missing: the chat window closes itself after
 * a command, which would immediately close a window that is opened right away.
 */
public final class ScreenOpener {

    private static final int DELAY_TICKS = 2;

    private static volatile int remaining = -1;

    private ScreenOpener() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (remaining < 0) {
                return;
            }
            if (--remaining > 0) {
                return;
            }
            remaining = -1;
            client.gui.setScreen(new E2EESettingsScreen(null));
        });
    }

    /** Opens the settings window after the current chat screen is gone. */
    public static void request() {
        remaining = DELAY_TICKS;
    }
}
