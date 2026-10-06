package holylois.boombox.mixins;

import dev.tr7zw.firstperson.api.FirstPersonAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Align only the rendered first-person body; leave world/player rotations alone. */
@Mixin(LivingEntityRenderer.class)
public abstract class FirstPersonAimMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"), require = 1)
    private void holyLoisSteadyAim(LivingEntity player, LivingEntityRenderState state, float delta, CallbackInfo callback) {
        if (player != Minecraft.getInstance().player || !FirstPersonAPI.isRenderingPlayer() || !player.isUsingItem()) return;
        var item = player.getUseItem();
        if (!item.is(Items.BOW) && !item.is(Items.CROSSBOW) && !item.is(Items.TRIDENT)) return;
        state.bodyRot = player.getViewYRot(delta);
        state.yRot = 0;
        state.walkAnimationSpeed = 0;
    }
}
