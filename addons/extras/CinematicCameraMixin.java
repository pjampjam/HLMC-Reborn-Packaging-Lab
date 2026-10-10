package holylois.boombox.mixins;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ji AFK Cinematic 2.3.2 pulls the camera only 0.25 blocks back along its ray; keep it clear of blocks on every side. */
@Pseudo
@Mixin(targets = "com.ji.afkcinematic.cinematic.CameraCollisionHelper", remap = false)
public abstract class CinematicCameraMixin {
    @Inject(method = "resolveCollision", at = @At("RETURN"), cancellable = true, remap = false)
    private static void holyLoisClearOfWalls(Vec3 anchor, Vec3 desired, CallbackInfoReturnable<Vec3> info) {
        info.setReturnValue(holylois.boombox.CinematicTweaks.clear(anchor, info.getReturnValue()));
    }
}
