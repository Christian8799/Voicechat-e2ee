package voicechate2ee.mixin;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.voice.client.AudioChannel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voicechate2ee.opus.DecryptingOpusDecoder;

/**
 * Haengt die Entschluesselung in den Empfangsweg von Simple Voice Chat ein: Jeder Audiokanal (ein Sprecher)
 * bekommt einen Decoder, der Pakete zuerst entschluesselt und erst dann dekodiert.
 */
@Mixin(value = AudioChannel.class, remap = false)
public abstract class AudioChannelMixin {

    @Shadow
    @Final
    @Mutable
    private OpusDecoder decoder;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voicechatE2ee$wrapDecoder(CallbackInfo ci) {
        this.decoder = new DecryptingOpusDecoder(this.decoder);
    }
}
