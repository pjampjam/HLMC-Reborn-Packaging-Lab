package holylois.boombox.mixins;

import holylois.boombox.KeptArms;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kept arms get their angles right before they are transformed, after any animation (KeptArms). */
@Mixin(ModelPart.class)
public abstract class KeptArmsPartMixin {
    @Inject(method = "translateAndRotate", at = @At("HEAD"), require = 1)
    private void holyLoisKeptArm(com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo info) { KeptArms.transforming((ModelPart) (Object) this); }
}
