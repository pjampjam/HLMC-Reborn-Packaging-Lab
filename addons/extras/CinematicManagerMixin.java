package holylois.boombox.mixins;

import holylois.boombox.CinematicTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ji AFK Cinematic 2.3.2, fishing (owner 2026-10-10, second pass): while you wait for a bite the camera cycles through the mod's
 * fishing angles, each framing you and the bobber (CinematicShotsMixin draws them), a new angle every shot instead of one shot on
 * a loop. With a fish on the hook it stays on the mod's own bite focus until the fish is caught or gone; after reeling in it keeps
 * that framing for 3 s, then the normal shots continue.
 */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.cinematic.CinematicManager", remap = false)
public abstract class CinematicManagerMixin {
    @Shadow private static int ticksLeftInCurrentShot;
    @Shadow private static int focusTicks;
    @Shadow private static boolean fishingCinematic;
    @Shadow private static boolean biteActive;
    @Shadow public static int getShotDurationTicks() { throw new AbstractMethodError(); }
    @Unique private static boolean holyLois$hookWasOut, holyLois$biteWasActive;

    @Inject(method = "startCinematic(Z)V", at = @At("HEAD"), remap = false)
    private static void holyLoisFishingStart(boolean fishing, CallbackInfo info) { CinematicTweaks.fishing = fishing; }

    @Inject(method = "advanceShot()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void holyLoisFishingShots(CallbackInfo info) {
        if (!fishingCinematic) return;
        if (CinematicTweaks.hookOut()) {
            // A fish on the hook: stay on it. Waiting: the next angle on you and the bobber (no cycle count, so it never runs out).
            if (biteActive) ticksLeftInCurrentShot = Math.max(ticksLeftInCurrentShot, 20);
            else { CinematicTweaks.nextFishingAngle(); ticksLeftInCurrentShot = getShotDurationTicks(); }
            info.cancel();
        } else if (CinematicTweaks.afterReel > 0) {
            ticksLeftInCurrentShot = Math.max(ticksLeftInCurrentShot, CinematicTweaks.afterReel);
            info.cancel();
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"), remap = false)
    private static void holyLoisFishingTick(CallbackInfo info) {
        if (!fishingCinematic) {
            CinematicTweaks.fishing = false; CinematicTweaks.fishingWait = false; CinematicTweaks.afterReel = 0;
            holyLois$hookWasOut = false; holyLois$biteWasActive = false;
            return;
        }
        boolean out = CinematicTweaks.hookOut();
        boolean wait = out && !biteActive && !CinematicTweaks.fishingShotBroken;
        // Entering the wait (cast, or a fish got away): frame you and the bobber right away with a fresh angle.
        if (wait && (!CinematicTweaks.fishingWait || CinematicTweaks.afterReel > 0)) { CinematicTweaks.afterReel = 0; CinematicTweaks.nextFishingAngle(); }
        if (holyLois$hookWasOut && !out) {
            // Reeled in: hold this framing 3 s (continuing the bite shot's clock if a fish was on), then change angle.
            CinematicTweaks.afterReel = 60;
            if (holyLois$biteWasActive) CinematicTweaks.waitTicks = focusTicks;
            ticksLeftInCurrentShot = 60;
        }
        if (CinematicTweaks.afterReel > 0 && !out) CinematicTweaks.afterReel--;
        CinematicTweaks.fishingWait = wait || (!out && CinematicTweaks.afterReel > 0 && !CinematicTweaks.fishingShotBroken);
        if (CinematicTweaks.fishingWait) CinematicTweaks.waitTicks++;
        holyLois$hookWasOut = out;
        holyLois$biteWasActive = biteActive;
    }
}
