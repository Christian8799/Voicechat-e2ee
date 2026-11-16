package voicechate2ee.gui;

import de.maxhenkel.voicechat.gui.widgets.ImageButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.SettingsModel;

/**
 * The two buttons that are added to the Simple Voice Chat window: a quick toggle for the encryption and a
 * button that opens the settings. They are Simple Voice Chat {@link ImageButton}s, so they look and behave
 * like the buttons in that window. The icons are drawn from rectangles, the texture is not used.
 */
public final class E2EEButtons {

    /** {@link ImageButton} needs a texture, but both buttons draw their icon themselves. */
    private static final Identifier UNUSED_TEXTURE = Identifier.fromNamespaceAndPath("voicechat_e2ee", "icons/unused");

    private E2EEButtons() {
    }

    /** Quick toggle: incognito icon, green dot while encryption is on, crossed out while it is off. */
    public static ImageButton quickToggle(int x, int y, Screen screen) {
        return new ImageButton(x, y, UNUSED_TEXTURE, button -> toggle(screen), new ToggleTooltip()) {
            @Override
            protected void extractImage(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
                boolean on = SettingsModel.isSendingEncrypted(E2EEState.snapshot());
                // 7 x 7 image at twice the size: sharp
                IncognitoIcon.draw(guiGraphics, getX() + 3, getY() + 3, IncognitoIcon.GRID * 2, on);
                if (on) {
                    guiGraphics.fill(getX() + 13, getY() + 13, getX() + 19, getY() + 19, 0xFF000000);
                    guiGraphics.fill(getX() + 14, getY() + 14, getX() + 18, getY() + 18, 0xFF3FD13F);
                }
            }
        };
    }

    /** Opens the settings. */
    public static ImageButton settings(int x, int y, Screen screen) {
        ImageButton button = new ImageButton(x, y, UNUSED_TEXTURE, b -> openSettings(screen), null) {
            @Override
            protected void extractImage(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
                GearIcon.draw(guiGraphics, getX() + (StripLayout.BUTTON - GearIcon.SIZE) / 2,
                        getY() + (StripLayout.BUTTON - GearIcon.SIZE) / 2);
            }
        };
        button.setTooltip(Tooltip.create(Component.literal(SettingsModel.settingsTooltip())));
        return button;
    }

    private static void toggle(Screen screen) {
        if (SettingsModel.toggleEncryption() == SettingsModel.ToggleResult.NEEDS_KEY) {
            openSettings(screen);
        }
    }

    private static void openSettings(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new E2EESettingsScreen(parent));
    }

    /** Keeps the tooltip of the quick toggle in sync with the current state (only rebuilt when it changes). */
    private static final class ToggleTooltip implements ImageButton.TooltipSupplier {

        @Nullable
        private String last;

        @Override
        public void updateTooltip(ImageButton button) {
            String text = SettingsModel.quickToggleTooltip(E2EEState.snapshot());
            if (!text.equals(last)) {
                last = text;
                button.setTooltip(Tooltip.create(Component.literal(text)));
            }
        }
    }
}
