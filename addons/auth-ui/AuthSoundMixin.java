package holylois.auth.mixins;

import holylois.auth.HolyLoisAuthClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep form clicks audible without changing or saving the player's volume settings. */
@Mixin(SoundEngine.class)
public abstract class AuthSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void holyLoisQuietLogin(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        if (HolyLoisAuthClient.muteWorldAudio() && sound.getSource() != SoundSource.MASTER)
            callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
