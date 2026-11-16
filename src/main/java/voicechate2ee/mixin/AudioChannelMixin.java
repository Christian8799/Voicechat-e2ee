package voicechate2ee.mixin;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.voice.client.AudioChannel;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.config.EncryptedPlayers;
import voicechate2ee.opus.DecryptingOpusDecoder;

import java.util.UUID;

/**
 * Hooks the decryption into the receive path of Simple Voice Chat: every audio channel (one speaker)
 * gets a decoder that decrypts packets first and only then decodes them. For this, the decoder knows the
 * channel ID, which is the UUID of the speaker.
 */
@Mixin(value = AudioChannel.class, remap = false)
public abstract class AudioChannelMixin {

    @Shadow
    @Final
    @Mutable
    private OpusDecoder decoder;

    @Shadow
    @Final
    private UUID uuid;

    @Shadow
    @Final
    private ClientVoicechat client;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voicechatE2ee$wrapDecoder(CallbackInfo ci) {
        // New voice chat connection (e.g. server change): reset the list of encrypted players
        EncryptedPlayers.bindSession(this.client);
        this.decoder = new DecryptingOpusDecoder(this.decoder, this.uuid);
    }
}
