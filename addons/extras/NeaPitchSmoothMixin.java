package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Not Enough Animations raises held items and maps with your look: use the frame's interpolated pitch, not the tick's. */
@Pseudo
@Mixin(targets = {"dev.tr7zw.notenoughanimations.animations.hands.LookAtItemAnimation",
    "dev.tr7zw.notenoughanimations.animations.hands.MapHoldingAnimation"}, remap = false)
public abstract class NeaPitchSmoothMixin {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Ldev/tr7zw/transition/mc/EntityUtil;getXRot(Lnet/minecraft/world/entity/Entity;)F"), require = 0)
    private float holyLoisSmoothPitch(Entity entity, Operation<Float> original) {
        return entity.getXRot(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
}
