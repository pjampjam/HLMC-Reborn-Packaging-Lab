package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.tr7zw.notenoughanimations.logic.HeldItemHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Not Enough Animations draws the hanging 3D lantern; the swing is ours (owner round 5). Its own physics stepped once per tick
 * in world space off the head yaw, so it looked 20 fps and shook when the body turned under the head. The two swing angles now
 * come from HeldSwing (the boombox pendulum: body frame, simulated per tick, drawn between ticks); the look angles it still
 * reads for the arm are interpolated.
 */
@Pseudo
@Mixin(value = HeldItemHandler.class, remap = false)
public abstract class NeaLanternSmoothMixin {
    private static float holyLoisPartial() { return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true); }

    @WrapOperation(method = "lanternAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"), require = 0)
    private float holyLoisSmoothPitch(LivingEntity entity, Operation<Float> original) { return entity.getXRot(holyLoisPartial()); }

    @WrapOperation(method = "lanternAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"), require = 0)
    private float holyLoisSmoothYaw(LivingEntity entity, Operation<Float> original) { return entity.getYRot(holyLoisPartial()); }

    /** First clamp: the forward/back swing (degrees, about X). */
    @WrapOperation(method = "lanternAnimation", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/util/Mth;clamp(FFF)F"), require = 0)
    private float holyLoisSmoothSwingForward(float value, float min, float max, Operation<Float> original, @Local(argsOnly = true) LivingEntity entity) {
        return holylois.boombox.HeldSwing.tiltNow(entity.getId());
    }

    /** Second clamp: the side to side swing (degrees, about Z). */
    @WrapOperation(method = "lanternAnimation", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/util/Mth;clamp(FFF)F"), require = 0)
    private float holyLoisSmoothSwingSide(float value, float min, float max, Operation<Float> original, @Local(argsOnly = true) LivingEntity entity) {
        return holylois.boombox.HeldSwing.sideNow(entity.getId());
    }
}
