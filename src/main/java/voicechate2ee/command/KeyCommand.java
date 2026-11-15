package voicechate2ee.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;
import voicechate2ee.config.E2EEState;
import voicechate2ee.crypto.VoiceKey;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Der Client-Befehl {@code /vckey}. Er wird komplett lokal ausgefuehrt und nie an den Server geschickt.
 *
 * <p>Absichtlich gibt es keinen Befehl, der einen Key als Argument entgegennimmt: Ein Tippfehler im Befehlsnamen
 * wuerde den Key sonst als Server-Befehl an den Server senden. Fremde Keys traegt man in die Konfigurationsdatei ein.
 */
public final class KeyCommand {

    private KeyCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                ClientCommands.literal("vckey")
                        .executes(ctx -> {
                            status(msg -> ctx.getSource().sendFeedback(msg));
                            return 1;
                        })
                        .then(ClientCommands.literal("generate").executes(ctx -> {
                            generate(msg -> ctx.getSource().sendFeedback(msg));
                            return 1;
                        }))
                        .then(ClientCommands.literal("show").executes(ctx -> {
                            show(msg -> ctx.getSource().sendFeedback(msg));
                            return 1;
                        }))
                        .then(ClientCommands.literal("on").executes(ctx -> {
                            setEnabled(true, msg -> ctx.getSource().sendFeedback(msg));
                            return 1;
                        }))
                        .then(ClientCommands.literal("off").executes(ctx -> {
                            setEnabled(false, msg -> ctx.getSource().sendFeedback(msg));
                            return 1;
                        }))
                        .then(ClientCommands.literal("strict")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    setStrict(true, msg -> ctx.getSource().sendFeedback(msg));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    setStrict(false, msg -> ctx.getSource().sendFeedback(msg));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("reload").executes(ctx -> {
                            E2EEState.forceReload();
                            status(msg -> ctx.getSource().sendFeedback(msg));
                            return 1;
                        }))
        ));
    }

    private static void status(Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        out.accept(Component.literal("[Voice E2EE] Verschluesselung: " + (s.enabled() ? "AN" : "AUS")));
        if (s.key() != null) {
            out.accept(Component.literal("[Voice E2EE] Key-Code: " + s.key().fingerprint()
                    + " (gleicher Code = gleicher Key)"));
        } else {
            out.accept(Component.literal("[Voice E2EE] Es ist kein Key gesetzt. /vckey generate erzeugt einen."));
        }
        out.accept(Component.literal("[Voice E2EE] Nur verschluesselte Sprecher hoeren: "
                + (s.requireEncryption() ? "ja" : "nein")));
        out.accept(Component.literal("[Voice E2EE] Befehle: /vckey generate | show | on | off | strict on|off | reload"));
    }

    private static void generate(Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        String key = VoiceKey.generatePassphrase();
        try {
            E2EEState.update(true, s.requireEncryption(), key);
        } catch (IOException e) {
            out.accept(Component.literal("[Voice E2EE] Konnte den Key nicht speichern: " + e.getMessage()));
            return;
        }
        out.accept(Component.literal("[Voice E2EE] Neuer Key erzeugt und gespeichert: " + key));
        out.accept(Component.literal("[Voice E2EE] Schick ihn nur an Leute, die dich hoeren duerfen. "
                + "Alle bisherigen Mitspieler brauchen jetzt den neuen Key."));
    }

    private static void show(Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        if (s.keyText().isEmpty()) {
            out.accept(Component.literal("[Voice E2EE] Es ist kein Key gesetzt."));
            return;
        }
        out.accept(Component.literal("[Voice E2EE] Dein Key: " + s.keyText()));
        out.accept(Component.literal("[Voice E2EE] Achtung: Nicht in Streams oder Screenshots zeigen."));
    }

    private static void setEnabled(boolean enabled, Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        try {
            E2EEState.update(enabled, s.requireEncryption(), s.keyText());
        } catch (IOException e) {
            out.accept(Component.literal("[Voice E2EE] Konnte die Einstellung nicht speichern: " + e.getMessage()));
            return;
        }
        status(out);
    }

    private static void setStrict(boolean strict, Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        try {
            E2EEState.update(s.enabled(), strict, s.keyText());
        } catch (IOException e) {
            out.accept(Component.literal("[Voice E2EE] Konnte die Einstellung nicht speichern: " + e.getMessage()));
            return;
        }
        status(out);
    }
}
