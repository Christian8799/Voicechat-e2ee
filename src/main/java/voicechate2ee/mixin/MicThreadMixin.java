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
 * Haengt die Verschluesselung in den Sendeweg von Simple Voice Chat ein: Der Opus-Encoder des Mikrofon-Threads
 * wird durch einen Encoder ersetzt, der jedes Paket verschluesselt, bevor es zum Server geht.
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
