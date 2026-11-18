package voicechate2ee.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Registers the configurable key mapping used to open this mod's settings. */
public final class SettingsKeyMapping {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("voicechat_e2ee", "general"));

    private static KeyMapping openSettings;

    private SettingsKeyMapping() {
    }

    public static void register() {
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voicechat_e2ee.open_settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.gui.setScreen(new E2EESettingsScreen(null));
                }
            }
        });
    }
}
