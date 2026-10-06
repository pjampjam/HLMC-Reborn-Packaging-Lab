package holylois.boombox.mixins;

import holylois.boombox.LocalPlayerRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LocalPlayerPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"), require = 1)
    private void holyLoisLocalPose(LivingEntity entity, LivingEntityRenderState state, float delta, CallbackInfo callback) {
        ((LocalPlayerRenderState)state).holyLoisLocalPlayer(entity == Minecraft.getInstance().player);
    }
}
