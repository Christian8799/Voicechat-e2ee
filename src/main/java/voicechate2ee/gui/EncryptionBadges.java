package voicechate2ee.gui;

import de.maxhenkel.voicechat.voice.client.ClientManager;
import voicechate2ee.config.E2EEState;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.crypto.VoiceKey;

import java.util.UUID;

/** Answers for the UI: does this player get the incognito icon? */
public final class EncryptionBadges {

    private EncryptionBadges() {
    }

    /**
     * <ul>
     *   <li><b>You:</b> yes, as long as you send encrypted (encryption on and a key set).</li>
     *   <li><b>Others:</b> yes, if their most recently heard speech was readable with your key.</li>
     * </ul>
     * Without your own key there is no icon, because without a key nobody can be recognized as
     * "encrypted with my key".
     */
    public static boolean isEncrypted(UUID player) {
        if (player == null) {
            return false;
        }
        E2EEState.Snapshot state = E2EEState.snapshot();
        VoiceKey key = state.key();
        if (key == null) {
            return false;
        }
        // New voice chat connection: clear the list (see EncryptedPlayers)
        EncryptedPlayers.bindSession(ClientManager.getClient());
        if (player.equals(ClientManager.getPlayerStateManager().getOwnID())) {
            return state.enabled();
        }
        return EncryptedPlayers.isEncrypted(player, key.fingerprint());
    }
}
