package holylois.boombox.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import holylois.boombox.RenderedRodTip;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class RodItemPoseMixin {
    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"), require = 1)
    private void holyLoisRodTip(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo callback) {
        RenderedRodTip.capture(pose);
    }
}
