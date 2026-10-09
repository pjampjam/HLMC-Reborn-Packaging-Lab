package holylois.boombox.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import holylois.boombox.FishLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grows a weighed fish around its own centre, after the hand/ground/frame placement (ItemTransform ends with the -0.5 centring
 * translate, so the quads here run 0..1 and the centre is 0.5), so it stays in the hand, the frame and on the cutting board.
 */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class FishLayerMixin {
    @Inject(method = "applyTransform", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;mulPose(Lorg/joml/Matrix4fc;)V"))
    private void holyLoisFishScale(PoseStack.Pose pose, CallbackInfo info) {
        float scale = FishLook.current;
        if (scale == 1) return;
        pose.translate(0.5f, 0.5f + FishLook.lift, 0.5f);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5f, -0.5f, -0.5f);
    }
}
