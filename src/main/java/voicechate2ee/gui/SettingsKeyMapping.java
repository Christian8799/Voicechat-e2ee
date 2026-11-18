package voicechate2ee.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.SettingsModel;
import voicechate2ee.whitelist.WhitelistService;
import voicechate2ee.whitelist.WhitelistState;

/** Registers all configurable shortcuts for Voice Chat E2EE. All mappings are unbound by default. */
public final class SettingsKeyMapping {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("voicechat_e2ee", "general"));

    private static KeyMapping openSettings;
    private static KeyMapping toggleEncryption;
    private static KeyMapping openWhitelist;
    private static KeyMapping rotateKey;
    private static KeyMapping resendKeys;
    private static KeyMapping toggleStatus;

    private SettingsKeyMapping() {
    }

    public static void register() {
        openSettings = register("key.voicechat_e2ee.open_settings");
        toggleEncryption = register("key.voicechat_e2ee.toggle_encryption");
        openWhitelist = register("key.voicechat_e2ee.open_whitelist");
        rotateKey = register("key.voicechat_e2ee.rotate_key");
        resendKeys = register("key.voicechat_e2ee.resend_keys");
        toggleStatus = register("key.voicechat_e2ee.toggle_status");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                if (client.player != null) client.gui.setScreen(new E2EESettingsScreen(null));
            }
            while (toggleEncryption.consumeClick()) {
                if (client.player == null) continue;
                SettingsModel.ToggleResult result = SettingsModel.toggleEncryption();
                switch (result) {
                    case CHANGED -> WhitelistService.chat("Encryption " +
                            (E2EEState.snapshot().enabled() ? "enabled." : "disabled."));
                    case NEEDS_KEY -> client.gui.setScreen(new E2EESettingsScreen(null));
                    case FAILED -> WhitelistService.chat("Could not save the encryption setting.");
                }
            }
            while (openWhitelist.consumeClick()) {
                if (client.player != null) client.gui.setScreen(new WhitelistSettingsScreen(null));
            }
            while (rotateKey.consumeClick()) {
                if (client.player == null) continue;
                if (!WhitelistState.isWhitelistMode()) {
                    WhitelistService.chat("Key rotation shortcut is only available in whitelist mode.");
                } else {
                    WhitelistService.getInstance().rotateOwnKey();
                    WhitelistService.chat("Your whitelist voice key was rotated. Current grants are being refreshed.");
                }
            }
            while (resendKeys.consumeClick()) {
                if (client.player == null) continue;
                if (!WhitelistState.isWhitelistMode()) {
                    WhitelistService.chat("Resending whitelist grants is only available in whitelist mode.");
                } else {
                    WhitelistService.getInstance().resend();
                    WhitelistService.chat("Current whitelist key grants queued for resend.");
                }
            }
            while (toggleStatus.consumeClick()) {
                if (client.player == null) continue;
                E2EEState.Snapshot state = E2EEState.snapshot();
                WhitelistService.chat("Status: encryption " + (state.enabled() ? "ON" : "OFF")
                        + ", mode " + (WhitelistState.isWhitelistMode() ? "WHITELIST" : "SHARED KEY")
                        + ", strict " + (state.requireEncryption() ? "ON" : "OFF") + ".");
            }
        });
    }

    private static KeyMapping register(String translationKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
    }
}
