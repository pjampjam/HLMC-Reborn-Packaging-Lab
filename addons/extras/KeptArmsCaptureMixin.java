package holylois.boombox.mixins;

import holylois.boombox.KeptArms;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Humanoid models: remember the arms at the end of setupAnim (KeptArms; players again later, see KeptArmsPlayerMixin). */
@Mixin(HumanoidModel.class)
public abstract class KeptArmsCaptureMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 1)
    private void holyLoisKeepArms(HumanoidRenderState state, CallbackInfo info) { KeptArms.capture(this, state); }
}
