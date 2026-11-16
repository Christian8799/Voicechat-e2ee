package voicechate2ee.mixin.ui;

import de.maxhenkel.voicechat.gui.volume.PlayerVolumeEntry;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.gui.EncryptionBadges;
import voicechate2ee.gui.IncognitoIcon;

/**
 * Volume window (player list): encrypted players get the incognito icon in the bottom right corner of their
 * (24 pixel) skin, twice the size of the base image so it stays sharp.
 */
@Mixin(value = PlayerVolumeEntry.class, remap = false)
public abstract class PlayerVolumeEntryMixin {

    @Shadow
    public abstract PlayerState getState();

    @Inject(method = "renderElement", at = @At("RETURN"), require = 0)
    private void voicechatE2ee$renderBadge(GuiGraphicsExtractor guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta, int skinX, int skinY, int textX, int textY, CallbackInfo ci) {
        PlayerState state = getState();
        if (state == null || !EncryptionBadges.isEncrypted(state.getUuid())) {
            return;
        }
        // Dimension from VolumeEntry.SKIN_SIZE (deliberately local: mixin classes should not have fields that are named like those of the target class)
        final int skinSize = 24;
        int badgeSize = IncognitoIcon.GRID * 2;
        IncognitoIcon.draw(guiGraphics, skinX + skinSize - badgeSize, skinY + skinSize - badgeSize, badgeSize);
    }
}
