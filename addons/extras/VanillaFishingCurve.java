package holylois.boombox.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FishingHookRenderer.class)
public interface VanillaFishingCurve {
    @Invoker(value = "lambda$submit$1", remap = false)
    static void holyLoisDrawLine(float x, float y, float z, float width, PoseStack.Pose pose, VertexConsumer vertices) { throw new AssertionError(); }
}
