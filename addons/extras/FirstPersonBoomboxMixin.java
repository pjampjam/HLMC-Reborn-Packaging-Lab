package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** The boombox in your own hand pulses with the music in first person too, like the placed and third-person ones. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonBoomboxMixin {
    @WrapMethod(method = "submitArmWithItem")
    private void holyLoisBoomboxCones(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float frame, float pitch,
        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original) {
        if (stack.getItem() != holylois.boombox.Boombox.ITEM) { original.call(player, hands, frame, pitch, hand, swing, stack, equip, pose, collector, light); return; }
        holylois.boombox.BoomboxPulse.capture = true;
        try { original.call(player, hands, frame, pitch, hand, swing, stack, equip, pose, collector, light); }
        finally { holylois.boombox.BoomboxPulse.capture = false; }
        holylois.boombox.BoomboxPulse.submitFirstPerson(pose, collector, light);
    }
}
