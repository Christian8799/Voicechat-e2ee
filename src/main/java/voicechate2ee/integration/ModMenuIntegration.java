package voicechate2ee.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import voicechate2ee.gui.E2EESettingsScreen;

/** Optional Mod Menu integration, isolated so Mod Menu remains an optional dependency. */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<? extends Screen> getModConfigScreenFactory() {
        return E2EESettingsScreen::new;
    }
}
