package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tr7zw.notenoughanimations.logic.HeldItemHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Not Enough Animations' hanging lantern moved at 20 fps (owner round 4): it reads the raw tick pitch/yaw and steps the
 * lantern once per tick. Here it gets the interpolated look angles and a lantern position blended between its last two ticks.
 */
@Pseudo
@Mixin(value = HeldItemHandler.class, remap = false)
public abstract class NeaLanternSmoothMixin {
    private static final Map<Object, Vec3[]> HOLYLOIS_STEPS = new WeakHashMap<>();

    private static float holyLoisPartial() { return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true); }

    @WrapOperation(method = "lanternAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"), require = 0)
    private float holyLoisSmoothPitch(LivingEntity entity, Operation<Float> original) { return entity.getXRot(holyLoisPartial()); }

    @WrapOperation(method = "lanternAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"), require = 0)
    private float holyLoisSmoothYaw(LivingEntity entity, Operation<Float> original) { return entity.getYRot(holyLoisPartial()); }

    /** The first read draws the lantern: give it the position between the previous tick and the latest one. */
    @WrapOperation(method = "lanternAnimation", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
        target = "Ldev/tr7zw/notenoughanimations/logic/HeldItemHandler$HeldItemState;lanternPos:Lnet/minecraft/world/phys/Vec3;"), require = 0)
    private Vec3 holyLoisSmoothLantern(HeldItemHandler.HeldItemState state, Operation<Vec3> original) {
        Vec3 now = original.call(state);
        if (now == null) return null;
        var steps = HOLYLOIS_STEPS.computeIfAbsent(state, s -> new Vec3[]{now, now});
        if (!now.equals(steps[1])) { steps[0] = steps[1]; steps[1] = now; }
        return steps[0].lerp(steps[1], holyLoisPartial());
    }
}
