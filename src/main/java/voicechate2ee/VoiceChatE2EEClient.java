package voicechate2ee;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import voicechate2ee.command.KeyCommand;
import voicechate2ee.config.E2EEState;
import voicechate2ee.gui.ScreenOpener;

public final class VoiceChatE2EEClient implements ClientModInitializer {

    private static final System.Logger LOG = System.getLogger("voicechat-e2ee");

    @Override
    public void onInitializeClient() {
        E2EEState.init(FabricLoader.getInstance().getConfigDir().resolve("voicechat-e2ee.properties"));
        KeyCommand.register();
        ScreenOpener.register();

        E2EEState.Snapshot s = E2EEState.snapshot();
        LOG.log(System.Logger.Level.INFO, "Voice Chat E2EE loaded. Encryption: "
                + (s.enabled() ? "ON" : "OFF")
                + (s.key() != null ? ", key code " + s.key().fingerprint() : ", no key set"));
    }
}
