package voicechate2ee.mixin.chat;

import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Gives access to the Minecraft profile key pair of the logged in account. Whitelist mode signs the key grants with
 * it and opens the grants that were encrypted for it.
 */
@Mixin(value = AccountProfileKeyPairManager.class, remap = false)
public interface KeyPairAccessor {

    @Accessor("keyPair")
    CompletableFuture<Optional<ProfileKeyPair>> voicechatE2ee$keyPair();
}
