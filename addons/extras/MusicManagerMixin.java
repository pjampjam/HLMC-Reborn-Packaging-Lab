package holylois.boombox.mixins;

import holylois.boombox.BoomboxClient;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's own music stops while a boombox plays nearby and comes back on its usual schedule afterwards. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Shadow public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisBoomboxPause(CallbackInfo callback) {
        if (!BoomboxClient.near) return;
        stopPlaying();
        callback.cancel();
    }
}
