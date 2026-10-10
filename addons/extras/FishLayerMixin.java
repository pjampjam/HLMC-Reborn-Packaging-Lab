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
        if (FishLook.swingZ != 0 || FishLook.swingX != 0) {
            // A carried boombox swings around its handle (model y 11.5 of 16) like a lantern on a walk.
            pose.translate(0.5f, 0.72f, 0.5f);
            if (!Float.isNaN(FishLook.swingYaw)) {
                // Boombox (owner 2026-10-10): it swings out from the body and back in, so it turns about the holder's forward axis
                // in the world, whatever the arm and item transforms did to the model's own axes.
                double yaw = Math.toRadians(FishLook.swingYaw);
                var axis = new org.joml.Matrix3f(pose.normal()).invert().transform(new org.joml.Vector3f((float) -Math.sin(yaw), 0, (float) Math.cos(yaw)));
                if (axis.lengthSquared() > 1e-8f) pose.rotate(new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(FishLook.swingZ), axis.normalize()));
            } else {
                pose.rotate(com.mojang.math.Axis.ZP.rotationDegrees(FishLook.swingZ));
                pose.rotate(com.mojang.math.Axis.XP.rotationDegrees(FishLook.swingX));
            }
            pose.translate(-0.5f, -0.72f, -0.5f);
        }
        if (holylois.boombox.BoomboxPulse.capture) { holylois.boombox.BoomboxPulse.capture = false; holylois.boombox.BoomboxPulse.capture(pose); }
        float scale = FishLook.current;
        if (scale == 1) return;
        pose.translate(0.5f, 0.5f + FishLook.lift, 0.5f);
        if (FishLook.carry) pose.rotate(com.mojang.math.Axis.ZP.rotationDegrees(FishLook.CARRY_TURN));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5f, -0.5f, -0.5f);
    }
}
