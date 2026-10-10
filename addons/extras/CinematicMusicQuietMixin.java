package holylois.boombox.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ji AFK Cinematic 2.3.2 plays its own soundtrack when the cinematic starts. While boombox or music disc music reaches you it stays
 * silent (owner 2026-10-10): the boombox is the soundtrack, in your ears (BoomboxInEar). Music that already plays fades out.
 */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.music.CinematicMusicManager", remap = false)
public abstract class CinematicMusicQuietMixin {
    @Shadow public static boolean isOurMusicPlaying;
    @Shadow public static void stopMusic() { throw new AbstractMethodError(); }

    @Inject(method = "checkAndPlayMusic()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void holyLoisBoomboxIsTheSoundtrack(CallbackInfo info) {
        if (holylois.boombox.BoomboxPulse.musicNearby()) info.cancel();
    }

    @Inject(method = "tick(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"), remap = false)
    private static void holyLoisFadeForBoombox(net.minecraft.client.Minecraft mc, CallbackInfo info) {
        if (isOurMusicPlaying && holylois.boombox.BoomboxPulse.musicNearby()) stopMusic();
    }
}
