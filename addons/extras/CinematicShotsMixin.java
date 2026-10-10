package holylois.boombox.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Ji AFK Cinematic 2.3.2: indoors, underground and while fishing it plans character shots only, never shots of a wall. While you
 * wait for a bite (and 3 s after reeling in) it draws the mod's fishing shot, you and the bobber, on our own clock (CinematicManagerMixin).
 */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.cinematic.CameraController", remap = false)
public abstract class CinematicShotsMixin {
    @ModifyVariable(method = "prepareSequence(IJ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static int holyLoisCharacterShots(int characterPercentage) {
        return holylois.boombox.CinematicTweaks.characterPercentage(characterPercentage);
    }

    @ModifyExpressionValue(method = "evaluateFrame(FF)V", at = @At(value = "FIELD", target = "Lcom/ji/afkcinematic/cinematic/CameraController;biteActive:Z", opcode = org.objectweb.asm.Opcodes.GETSTATIC), remap = false)
    private static boolean holyLoisFishingFraming(boolean bite) { return bite || holylois.boombox.CinematicTweaks.fishingWait; }

    @ModifyExpressionValue(method = "evaluateFrame(FF)V", at = @At(value = "INVOKE", target = "Lcom/ji/afkcinematic/cinematic/CinematicManager;getBiteTicks()I"), remap = false)
    private static int holyLoisFishingClock(int ticks) { return holylois.boombox.CinematicTweaks.fishingWait ? holylois.boombox.CinematicTweaks.waitTicks : ticks; }
}
