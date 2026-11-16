package voicechate2ee.mixin;

import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.voice.client.MicThread;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.opus.EncryptingOpusEncoder;

/**
 * Hooks the encryption into the send path of Simple Voice Chat: the Opus encoder of the microphone thread
 * is replaced by an encoder that encrypts every packet before it goes to the server.
 */
@Mixin(value = MicThread.class, remap = false)
public abstract class MicThreadMixin {

    @Shadow
    @Final
    @Mutable
    private OpusEncoder encoder;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voicechatE2ee$wrapEncoder(CallbackInfo ci) {
        this.encoder = new EncryptingOpusEncoder(this.encoder);
    }
}
