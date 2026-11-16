package voicechate2ee.mixin.ui;

import de.maxhenkel.voicechat.gui.group.GroupEntry;
import de.maxhenkel.voicechat.gui.widgets.ListScreenEntryBase;
import de.maxhenkel.voicechat.voice.client.ClientManager;
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

/**
 * Group window (member list): encrypted players (including you) get the incognito icon right after the
 * name, next to the skin and name.
 *
 * <p>If the volume slider is shown while hovering and the icon no longer fits next to it, the icon is
 * left out so it does not cover the slider.
 */
@Mixin(value = GroupEntry.class, remap = false)
public abstract class GroupEntryMixin extends ListScreenEntryBase<GroupEntry> {

    @Shadow
    public abstract PlayerState getState();

    @Inject(method = "extractContent", at = @At("RETURN"), require = 0)
    private void voicechatE2ee$renderBadge(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        PlayerState state = getState();
        if (state == null || !EncryptionBadges.isEncrypted(state.getUuid())) {
            return;
        }
        // Dimensions from GroupEntry (deliberately local: mixin classes should not have fields that are named like those of the target class)
        final int padding = 4;
        final int maxSliderWidth = 100;
        final int gap = 4;

        Minecraft minecraft = Minecraft.getInstance();
        int left = getContentX();
        int top = getContentY();
        int width = getContentWidth();
        int height = getContentHeight();

        int outlineSize = height - padding * 2;
        int nameWidth = minecraft.font.width(Component.literal(state.getName()));
        int badgeSize = IncognitoIcon.GRID;
        int badgeX = left + padding + outlineSize + padding + nameWidth + gap;
        int badgeY = top + (height - badgeSize) / 2;

        int limit = left + width - padding;
        boolean sliderShown = hovered && !ClientManager.getPlayerStateManager().getOwnID().equals(state.getUuid());
        if (sliderShown) {
            int sliderWidth = Math.min(width - (padding + outlineSize + padding + nameWidth + padding + padding), maxSliderWidth);
            limit = left + (width - sliderWidth - padding) - 2;
        }
        if (badgeX + badgeSize > limit) {
            return;
        }
        IncognitoIcon.draw(guiGraphics, badgeX, badgeY, badgeSize);
    }
}
