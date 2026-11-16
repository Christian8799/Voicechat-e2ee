package voicechate2ee.mixin.ui;

import de.maxhenkel.voicechat.gui.VoiceChatScreen;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.gui.E2EEButtons;
import voicechate2ee.gui.McPanel;
import voicechate2ee.gui.StripLayout;

/**
 * Simple Voice Chat window (opened with the voice chat key): adds a small panel next to it with two buttons,
 * one to switch the encryption on/off and one to open the settings of this mod.
 *
 * <p>The window is a fixed texture without free space, which is why the buttons sit in a panel of their own
 * next to it. Both hooks are optional ({@code require = 0}): if Simple Voice Chat changes this screen, the
 * buttons are missing, but the game and the encryption keep working.
 */
@Mixin(value = VoiceChatScreen.class, remap = false)
public abstract class VoiceChatScreenMixin extends VoiceChatScreenBase {

    // Mixin classes need a constructor that matches the superclass; it is never called
    protected VoiceChatScreenMixin(Component title, int xSize, int ySize) {
        super(title, xSize, ySize);
    }

    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void voicechatE2ee$addButtons(CallbackInfo ci) {
        StripLayout.Placement strip = StripLayout.place(guiLeft, guiTop, xSize, ySize, width, height);
        addRenderableWidget(E2EEButtons.quickToggle(strip.buttonX(0), strip.buttonY(0), this));
        addRenderableWidget(E2EEButtons.settings(strip.buttonX(1), strip.buttonY(1), this));
    }

    @Inject(method = "extractBackgroundRenderState", at = @At("RETURN"), require = 0)
    private void voicechatE2ee$drawStrip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        StripLayout.Placement strip = StripLayout.place(guiLeft, guiTop, xSize, ySize, width, height);
        McPanel.draw(guiGraphics, strip.x(), strip.y(), strip.width(), strip.height());
    }
}
