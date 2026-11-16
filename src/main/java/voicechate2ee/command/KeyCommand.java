package voicechate2ee.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;
import voicechate2ee.config.E2EEState;
import voicechate2ee.crypto.VoiceKey;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The client command {@code /vckey}. It runs entirely locally and is never sent to the server.
 *
 * <p>There is deliberately no command that takes a key as an argument: a typo in the command name would
 * otherwise send the key to the server as a server command. Other people's keys are entered in the
 * configuration file.
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
        out.accept(Component.literal("[Voice E2EE] Encryption: " + (s.enabled() ? "ON" : "OFF")));
        if (!s.enabled()) {
            out.accept(Component.literal("[Voice E2EE] You are sending unencrypted. You can still hear players "
                    + "who speak encrypted with your key."));
        }
        if (s.key() != null) {
            out.accept(Component.literal("[Voice E2EE] Key code: " + s.key().fingerprint()
                    + " (same code = same key)"));
        } else {
            out.accept(Component.literal("[Voice E2EE] No key is set. /vckey generate creates one."));
        }
        out.accept(Component.literal("[Voice E2EE] Only hear encrypted speakers: "
                + (s.requireEncryption() ? "yes" : "no")
                + (s.enabled() ? "" : " (only applies while encryption is ON)")));
        out.accept(Component.literal("[Voice E2EE] Commands: /vckey generate | show | on | off | strict on|off | reload"));
    }

    private static void generate(Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        String key = VoiceKey.generatePassphrase();
        try {
            E2EEState.update(true, s.requireEncryption(), key);
        } catch (IOException e) {
            out.accept(Component.literal("[Voice E2EE] Could not save the key: " + e.getMessage()));
            return;
        }
        out.accept(Component.literal("[Voice E2EE] New key generated and saved: " + key));
        out.accept(Component.literal("[Voice E2EE] Only send it to people who are allowed to hear you. "
                + "Everyone you played with before now needs the new key."));
    }

    private static void show(Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        if (s.keyText().isEmpty()) {
            out.accept(Component.literal("[Voice E2EE] No key is set."));
            return;
        }
        out.accept(Component.literal("[Voice E2EE] Your key: " + s.keyText()));
        out.accept(Component.literal("[Voice E2EE] Warning: Do not show it on streams or in screenshots."));
    }

    private static void setEnabled(boolean enabled, Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        try {
            E2EEState.update(enabled, s.requireEncryption(), s.keyText());
        } catch (IOException e) {
            out.accept(Component.literal("[Voice E2EE] Could not save the setting: " + e.getMessage()));
            return;
        }
        status(out);
    }

    private static void setStrict(boolean strict, Consumer<Component> out) {
        E2EEState.Snapshot s = E2EEState.snapshot();
        try {
            E2EEState.update(s.enabled(), strict, s.keyText());
        } catch (IOException e) {
            out.accept(Component.literal("[Voice E2EE] Could not save the setting: " + e.getMessage()));
            return;
        }
        status(out);
    }
}
