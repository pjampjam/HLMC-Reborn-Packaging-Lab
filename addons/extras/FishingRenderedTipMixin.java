package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import holylois.boombox.FishingLineState;
import holylois.boombox.RenderedRodTip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHookRenderer.class)
public abstract class FishingRenderedTipMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V", at = @At("RETURN"), require = 1)
    private void holyLoisLineOwner(FishingHook hook, FishingHookRenderState state, float delta, CallbackInfo callback) {
        ((FishingLineState)state).holyLoisLocalOwner(hook.getPlayerOwner() == Minecraft.getInstance().player && Minecraft.getInstance().player != null);
    }
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V", ordinal = 1), require = 1)
    private void holyLoisLineTip(SubmitNodeCollector collector, PoseStack stack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer vanilla,
        Operation<Void> original, @Local(argsOnly = true) FishingHookRenderState state) {
        if (!((FishingLineState)state).holyLoisLocalOwner()) { original.call(collector, stack, type, vanilla); return; }
        SubmitNodeCollector.CustomGeometryRenderer adjusted = (pose, vertices) -> {
            var tip = RenderedRodTip.current();
            if (tip == null) { vanilla.render(pose, vertices); return; }
            var end = tip.subtract(new Vec3(state.x, state.y + 0.25, state.z));
            float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
            VanillaFishingCurve.holyLoisDrawLine((float)end.x, (float)end.y, (float)end.z, width, pose, vertices);
            RenderedRodTip.attachments++;
        };
        original.call(collector, stack, type, adjusted);
    }
}
