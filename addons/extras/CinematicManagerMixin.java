package holylois.boombox.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ji AFK Cinematic 2.3.2, fishing (owner 2026-10-10): the camera stays on the current shot the whole time the bobber is out,
 * bites included, and changes angle 3 s after you reel in.
 */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.cinematic.CinematicManager", remap = false)
public abstract class CinematicManagerMixin {
    @Shadow private static int ticksLeftInCurrentShot;
    @Shadow private static boolean fishingCinematic;
    @Unique private static boolean holyLois$hookWasOut;

    @Inject(method = "startCinematic(Z)V", at = @At("HEAD"), remap = false)
    private static void holyLoisFishingStart(boolean fishing, CallbackInfo info) { holylois.boombox.CinematicTweaks.fishing = fishing; }

    @Inject(method = "advanceShot()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void holyLoisHoldWhileFishing(CallbackInfo info) {
        if (!fishingCinematic || !holylois.boombox.CinematicTweaks.hookOut()) return;
        // Check again in a second instead of cutting away from the bobber.
        ticksLeftInCurrentShot = Math.max(ticksLeftInCurrentShot, 20);
        info.cancel();
    }

    @Inject(method = "tick()V", at = @At("HEAD"), remap = false)
    private static void holyLoisReeledIn(CallbackInfo info) {
        boolean out = fishingCinematic && holylois.boombox.CinematicTweaks.hookOut();
        if (holyLois$hookWasOut && !out && fishingCinematic) ticksLeftInCurrentShot = Math.min(ticksLeftInCurrentShot, 60);
        holyLois$hookWasOut = out;
        if (!fishingCinematic) holylois.boombox.CinematicTweaks.fishing = false;
    }
}
