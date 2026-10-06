package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import holylois.boombox.RenderedRodTip;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandLayer.class)
public abstract class RodHandPoseMixin {
    @WrapMethod(method = "submitArmWithItem")
    private void holyLoisRodPose(ArmedEntityRenderState state, ItemStackRenderState model, ItemStack stack, HumanoidArm arm,
        PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original) {
        boolean localBody = state instanceof holylois.boombox.LocalPlayerRenderState access && access.holyLoisLocalPlayer();
        boolean previous = RenderedRodTip.begin(stack, arm, localBody);
        try { original.call(state, model, stack, arm, pose, collector, light); }
        finally { RenderedRodTip.end(previous); }
    }
}
