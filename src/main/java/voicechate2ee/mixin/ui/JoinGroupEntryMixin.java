package voicechate2ee.mixin.ui;

import de.maxhenkel.voicechat.gui.group.JoinGroupEntry;
import de.maxhenkel.voicechat.gui.widgets.ListScreenEntryBase;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.gui.EncryptionBadges;
import voicechate2ee.gui.IncognitoIcon;

import java.util.List;

/**
 * Join group window (list of all groups with the heads of their members): encrypted players get the
 * incognito icon in the bottom right corner of their head.
 *
 * <p>The head positions are taken from {@code JoinGroupEntry.extractContent}. The hook is at
 * {@code RETURN} (all exits), because the method returns early when the mouse is not hovering.
 */
@Mixin(value = JoinGroupEntry.class, remap = false)
public abstract class JoinGroupEntryMixin extends ListScreenEntryBase<JoinGroupEntry> {

    @Shadow
    public abstract JoinGroupEntry.Group getGroup();

    @Inject(method = "extractContent", at = @At("RETURN"), require = 0)
    private void voicechatE2ee$renderBadges(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        JoinGroupEntry.Group group = getGroup();
        if (group == null) {
            return;
        }
        List<PlayerState> members = group.getMembers();
        if (members.isEmpty()) {
            return;
        }
        // Dimensions from JoinGroupEntry (deliberately local: mixin classes should not have fields that are named like those of the target class)
        final int skinSize = 12;
        final int padding = 4;
        final int lockSize = 16;
        final int rows = 2;

        Minecraft minecraft = Minecraft.getInstance();
        int left = getContentX();
        int top = getContentY();
        int width = getContentWidth();
        int height = getContentHeight();

        boolean hasPassword = group.getGroup().hasPassword();
        int textWidth = minecraft.font.width(Component.literal(group.getGroup().getName())) + (hasPassword ? lockSize + padding : 0);
        int headsPerRow = (width - (padding + textWidth + padding + padding)) / (skinSize + 1);

        // 1 image pixel = 1 GUI pixel, matching the head size of 12 pixels
        int badgeSize = IncognitoIcon.GRID;

        for (int i = 0; i < members.size(); i++) {
            if (i >= headsPerRow * rows) {
                break;
            }
            PlayerState state = members.get(i);
            if (!EncryptionBadges.isEncrypted(state.getUuid())) {
                continue;
            }
            int headXIndex = i / rows;
            int headYIndex = i % rows;
            int headPosX = left + width - skinSize - padding - headXIndex * (skinSize + 1);
            int headPosY = top + height / 2 - ((skinSize * 2 + 2) / 2) + ((skinSize * 2 + 2) / 2) * headYIndex;

            IncognitoIcon.draw(guiGraphics, headPosX + skinSize - badgeSize, headPosY + skinSize - badgeSize, badgeSize);
        }
    }
}
