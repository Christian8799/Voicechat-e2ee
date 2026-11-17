package voicechate2ee.gui;

import de.maxhenkel.voicechat.voice.client.ClientManager;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.config.SettingsModel;
import voicechate2ee.crypto.VoiceKey;

import java.util.UUID;

/** Answers for the UI: does this player get the incognito icon? */
public final class EncryptionBadges {

    private EncryptionBadges() {
    }

    /**
     * <ul>
     *   <li><b>You:</b> yes, as long as you send encrypted (encryption on, and a key set or whitelist mode).</li>
     *   <li><b>Others:</b> yes, if their most recently heard speech was readable with your key, or with the
     *       voice key they gave you because you are on their whitelist.</li>
     * </ul>
     * Without your own key, only players heard through the whitelist get an icon, because nobody else can be
     * recognized as "encrypted with my key".
     */
    public static boolean isEncrypted(UUID player) {
        if (player == null) {
            return false;
        }
        E2EEState.Snapshot state = E2EEState.snapshot();
        VoiceKey key = state.key();
        // New voice chat connection: clear the list (see EncryptedPlayers)
        EncryptedPlayers.bindSession(ClientManager.getClient());
        if (player.equals(ClientManager.getPlayerStateManager().getOwnID())) {
            return SettingsModel.isSendingEncrypted(state);
        }
        return EncryptedPlayers.isEncrypted(player, key == null ? null : key.fingerprint());
    }
}
