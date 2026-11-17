package voicechate2ee.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import voicechate2ee.whitelist.ReceivedKeys;
import voicechate2ee.whitelist.WhitelistService;
import voicechate2ee.whitelist.WhitelistState;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The client command {@code /vcwl}: choose who may hear you. It runs entirely locally and is never sent to the
 * server (the key messages that the whitelist sends are whispers, written by {@link WhitelistService}).
 *
 * <ul>
 *   <li>{@code /vcwl add <player>}: the player (who must be online) may hear you from now on</li>
 *   <li>{@code /vcwl remove <name>}: they cannot hear you anymore; your voice key is replaced</li>
 *   <li>{@code /vcwl mode whitelist|key}: only your whitelist hears you / everybody with your shared key does</li>
 *   <li>{@code /vcwl} or {@code /vcwl list}: mode and who is on the list</li>
 *   <li>{@code /vcwl resend}: hand your key to everybody on the list again</li>
 * </ul>
 */
public final class WhitelistCommand {

    private static final String PREFIX = "[Voice E2EE] ";

    private WhitelistCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                ClientCommands.literal("vcwl")
                        .executes(ctx -> {
                            status(ctx.getSource()::sendFeedback);
                            return 1;
                        })
                        .then(ClientCommands.literal("list").executes(ctx -> {
                            status(ctx.getSource()::sendFeedback);
                            return 1;
                        }))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("player", StringArgumentType.word())
                                        .suggests(WhitelistCommand::suggestOnline)
                                        .executes(ctx -> {
                                            add(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("player", StringArgumentType.word())
                                        .suggests(WhitelistCommand::suggestListed)
                                        .executes(ctx -> {
                                            remove(ctx.getSource()::sendFeedback, StringArgumentType.getString(ctx, "player"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("mode")
                                .then(ClientCommands.literal("whitelist").executes(ctx -> {
                                    setMode(ctx.getSource()::sendFeedback, WhitelistState.Mode.WHITELIST);
                                    return 1;
                                }))
                                .then(ClientCommands.literal("key").executes(ctx -> {
                                    setMode(ctx.getSource()::sendFeedback, WhitelistState.Mode.KEY);
                                    return 1;
                                })))
                        .then(ClientCommands.literal("resend").executes(ctx -> {
                            WhitelistService.getInstance().resend();
                            out(ctx.getSource()::sendFeedback, "Your voice key is sent to everybody on the whitelist again.");
                            return 1;
                        }))
        ));
    }

    // ---------------------------------------------------------------- actions

    private static void add(FabricClientCommandSource source, String name) {
        Consumer<Component> out = source::sendFeedback;
        if (damaged(out)) {
            return;
        }
        ClientPacketListener connection = source.getClient().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
        if (info == null) {
            out(out, name + " is not online. Only players who are online can be added.");
            return;
        }
        UUID uuid = info.getProfile().id();
        String realName = info.getProfile().name();
        if (uuid.equals(WhitelistState.selfId())) {
            out(out, "You cannot add yourself.");
            return;
        }
        try {
            boolean added = WhitelistState.add(uuid, realName);
            WhitelistService.getInstance().onAdded(uuid);
            out(out, added ? realName + " was added. They can hear you once the key grant reaches them." : realName + " is already on your whitelist.");
        } catch (IOException e) {
            out(out, "Could not save the whitelist: " + e.getMessage());
            return;
        }
        out(out, realName + " needs this mod as well. They hear you as soon as your key has reached them.");
        if (!WhitelistState.isWhitelistMode()) {
            out(out, "Whitelist mode is off, so the whitelist does not apply yet. Turn it on: /vcwl mode whitelist");
        }
    }

    private static void remove(Consumer<Component> out, String name) {
        if (damaged(out)) {
            return;
        }
        WhitelistState.Entry entry = WhitelistState.findByName(name);
        if (entry == null) {
            out(out, name + " is not on your whitelist.");
            return;
        }
        try {
            WhitelistState.remove(entry.uuid());
        } catch (IOException e) {
            out(out, "Could not save the whitelist: " + e.getMessage());
            return;
        }
        WhitelistService.getInstance().onRemoved(entry.uuid());
        out(out, entry.name() + " can not hear you anymore. Your voice key was replaced and the others got the new one.");
        out(out, "You do not hear " + entry.name() + " anymore either.");
    }

    private static void setMode(Consumer<Component> out, WhitelistState.Mode mode) {
        if (damaged(out)) {
            return;
        }
        try {
            WhitelistState.setMode(mode);
        } catch (IOException e) {
            out(out, "Could not save the setting: " + e.getMessage());
            return;
        }
        WhitelistService.getInstance().onModeChanged();
        if (mode == WhitelistState.Mode.WHITELIST) {
            out(out, "Whitelist mode ON: only the players on your whitelist can hear you.");
        } else {
            out(out, "Shared key mode: everybody with your shared key can hear you. The whitelist is not used for sending.");
        }
    }

    private static void status(Consumer<Component> out) {
        if (damaged(out)) {
            return;
        }
        boolean whitelistMode = WhitelistState.isWhitelistMode();
        out(out, "Mode: " + (whitelistMode ? "whitelist (only the players below hear you)" : "shared key (the whitelist is not used for sending)"));
        var entries = WhitelistState.entries();
        if (entries.isEmpty()) {
            out(out, "Your whitelist is empty. Add somebody with /vcwl add <player>.");
            return;
        }
        WhitelistService service = WhitelistService.getInstance();
        for (WhitelistState.Entry entry : entries) {
            String hears = !whitelistMode ? "-" : service.isGranted(entry.uuid()) ? "grant queued (unconfirmed)" : "key not queued";
            String youHear = ReceivedKeys.has(entry.uuid()) ? "key received" : "no key yet";
            out(out, "  " + entry.name() + ": they hear you: " + hears + ", you hear them: " + youHear);
        }
    }

    private static boolean damaged(Consumer<Component> out) {
        String error = WhitelistState.loadError();
        if (error == null) {
            return false;
        }
        out(out, "The whitelist file could not be read (" + error + "). Fix or delete config/voicechat-e2ee-whitelist.txt.");
        return true;
    }

    private static void out(Consumer<Component> out, String text) {
        out.accept(Component.literal(PREFIX + text));
    }

    // ---------------------------------------------------------------- suggestions

    private static CompletableFuture<Suggestions> suggestOnline(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        ClientPacketListener connection = ctx.getSource().getClient().getConnection();
        if (connection == null) {
            return builder.buildFuture();
        }
        UUID self = WhitelistState.selfId();
        return SharedSuggestionProvider.suggest(connection.getOnlinePlayers().stream()
                .filter(info -> !info.getProfile().id().equals(self))
                .map(info -> info.getProfile().name()), builder);
    }

    private static CompletableFuture<Suggestions> suggestListed(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(WhitelistState.entries().stream().map(WhitelistState.Entry::name), builder);
    }
}
