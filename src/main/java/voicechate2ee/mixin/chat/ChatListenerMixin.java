package voicechate2ee.mixin.chat;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import voicechate2ee.whitelist.ChatCodec;
import voicechate2ee.whitelist.WhitelistService;
import voicechate2ee.whitelist.WhitelistState;

import java.time.Instant;
import java.util.UUID;

/**
 * Takes the whispers of the whitelist system (voice key grants and requests) out of the chat: they are handed to
 * {@link WhitelistService} and never shown. This hook is optional ({@code require = 0}): if it no longer fits
 * after a Minecraft update, whitelist mode cannot receive keys, but the game and the shared key keep working.
 */
@Mixin(value = ChatListener.class, remap = false)
public abstract class ChatListenerMixin {

    @Inject(method = "showMessageToPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatTrustLevel;createTag(Lnet/minecraft/network/chat/PlayerChatMessage;)Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"),
            cancellable = true, require = 0)
    private void voicechatE2ee$takeKeyMessages(ChatType.Bound boundChatType, PlayerChatMessage message, Component decoratedMessage,
                                               GameProfile sender, boolean onlyShowSecure, Instant received,
                                               CallbackInfoReturnable<Boolean> cir) {
        // Incoming whispers carry protocol messages from other players. Outgoing whispers may be echoed
        // back by the server with MSG_COMMAND_OUTGOING; hide our own protocol echo too, but never process it.
        boolean incoming = boundChatType.chatType().is(ChatType.MSG_COMMAND_INCOMING);
        boolean outgoing = boundChatType.chatType().is(ChatType.MSG_COMMAND_OUTGOING);
        if (!incoming && !outgoing) {
            return;
        }
        String content = message.signedContent();
        if (content == null || !content.startsWith(ChatCodec.HEADER)) {
            return;
        }
        String payload = content.substring(ChatCodec.HEADER.length());
        if (ChatCodec.decodeFragment(payload) == null) {
            return;
        }
        UUID self = WhitelistState.selfId();
        if (outgoing || sender.id().equals(self)) {
            // The sender-side echo is only hidden. Never feed it to the grant receiver.
            cir.setReturnValue(false);
            return;
        }
        WhitelistService.getInstance().onChatPayload(payload, sender.id(), sender.name());
        cir.setReturnValue(true);
    }
}
