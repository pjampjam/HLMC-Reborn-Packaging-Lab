package holylois.boombox.mixins;

import holylois.boombox.KeptArms;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Around each model draw: kept arms are held during it (KeptArms), body and armor models alike. */
@Mixin(ModelFeatureRenderer.class)
public abstract class KeptArmsDrawMixin {
    private static final String RENDER = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    @Inject(method = "prepareModel", at = @At(value = "INVOKE", target = RENDER), require = 1)
    private void holyLoisKeptArmsDraw(ModelFeatureRenderer.Submit<?> submit, CallbackInfo info) { KeptArms.beginDraw(submit.model(), submit.state()); }

    @Inject(method = "prepareModel", at = @At(value = "INVOKE", target = RENDER, shift = At.Shift.AFTER), require = 1)
    private void holyLoisKeptArmsDrawn(ModelFeatureRenderer.Submit<?> submit, CallbackInfo info) { KeptArms.endDraw(); }
}
