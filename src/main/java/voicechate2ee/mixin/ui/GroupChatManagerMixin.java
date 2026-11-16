package voicechate2ee.mixin.ui;

import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.config.ClientConfig;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.GroupChatManager;
import de.maxhenkel.voicechat.voice.client.GroupPlayerIconOrientation;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.gui.EncryptionBadges;
import voicechate2ee.gui.IncognitoIcon;

import java.util.List;

/**
 * Group list in the HUD (the heads of the group members at the edge of the screen): after the heads are
 * drawn, every encrypted player (including you) gets the incognito icon at the corner of their head.
 *
 * <p>The layout (position, scale, orientation, 11 units spacing) is taken from
 * {@code GroupChatManager.renderIcons}. The icon sticks out 3 units beyond the head, exactly as far as
 * there is room between two heads.
 */
@Mixin(value = GroupChatManager.class, remap = false)
public abstract class GroupChatManagerMixin {

    @Inject(method = "renderIcons", at = @At("TAIL"), require = 0)
    private static void voicechatE2ee$renderBadges(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        if (ClientManager.getClient() == null) {
            return;
        }
        ClientConfig config = VoicechatClient.CLIENT_CONFIG;
        List<PlayerState> members = GroupChatManager.getGroupMembers(config.showOwnGroupIcon.get());
        if (members.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        guiGraphics.pose().pushMatrix();
        int posX = config.groupPlayerIconPosX.get();
        int posY = config.groupPlayerIconPosY.get();
        if (posX < 0) {
            guiGraphics.pose().translate(mc.getWindow().getGuiScaledWidth(), 0F);
        }
        if (posY < 0) {
            guiGraphics.pose().translate(0F, mc.getWindow().getGuiScaledHeight());
        }
        guiGraphics.pose().translate(posX, posY);

        float scale = config.groupHudIconScale.get().floatValue();
        guiGraphics.pose().scale(scale, scale);

        boolean vertical = config.groupPlayerIconOrientation.get().equals(GroupPlayerIconOrientation.VERTICAL);

        // Head: 8x8 units at (1,1), or (-9,-9) if the list is anchored to the right/bottom
        float headX = posX < 0 ? -1F - 8F : 1F;
        float headY = posY < 0 ? -1F - 8F : 1F;
        // Icon at the head corner that points away from the next head
        float badgeX = posX < 0 ? headX - 3F : headX + 4F;
        float badgeY = posY < 0 ? headY - 3F : headY + 4F;

        for (int i = 0; i < members.size(); i++) {
            PlayerState state = members.get(i);
            if (!EncryptionBadges.isEncrypted(state.getUuid())) {
                continue;
            }
            guiGraphics.pose().pushMatrix();
            if (vertical) {
                guiGraphics.pose().translate(0F, posY < 0 ? i * -11F : i * 11F);
            } else {
                guiGraphics.pose().translate(posX < 0 ? i * -11F : i * 11F, 0F);
            }
            IncognitoIcon.draw(guiGraphics, badgeX, badgeY, IncognitoIcon.GRID);
            guiGraphics.pose().popMatrix();
        }

        guiGraphics.pose().popMatrix();
    }
}
