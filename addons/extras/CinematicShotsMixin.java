package holylois.boombox.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Ji AFK Cinematic 2.3.2: indoors, underground and while fishing it plans character shots only, never shots of a wall. */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.cinematic.CameraController", remap = false)
public abstract class CinematicShotsMixin {
    @ModifyVariable(method = "prepareSequence(IJ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static int holyLoisCharacterShots(int characterPercentage) {
        return holylois.boombox.CinematicTweaks.characterPercentage(characterPercentage);
    }
}
