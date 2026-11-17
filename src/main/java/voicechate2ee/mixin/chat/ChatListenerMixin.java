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
        // Only consume incoming private whispers. A public chat message containing our marker must remain visible.
        if (!boundChatType.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
            return;
        }
        String content = message.signedContent();
        int index = content.indexOf(ChatCodec.HEADER);
        if (index == -1) {
            return;
        }
        UUID self = WhitelistState.selfId();
        if (sender.id().equals(self)) {
            // the server echoing our own whisper back to us
            cir.setReturnValue(false);
            return;
        }
        WhitelistService.getInstance().onChatPayload(content.substring(index + ChatCodec.HEADER.length()), sender.id(), sender.name());
        cir.setReturnValue(true);
    }
}
