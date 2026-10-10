package holylois.boombox.mixins;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Like a jukebox: while boombox or music disc music reaches you, Minecraft's own background music stops and waits (owner 2026-10-10). */
@Mixin(MusicManager.class)
public abstract class BoomboxQuietsMusicMixin {
    @Shadow public abstract void stopPlaying();

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void holyLoisQuietForBoombox(CallbackInfo info) {
        if (!holylois.boombox.BoomboxPulse.musicNearby()) return;
        stopPlaying();
        info.cancel();
    }
}
