package holylois.boombox.mixins;

import holylois.boombox.ChestReplay;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fresh Animations' chest close animation only advances while the chest is on screen; see ChestReplay. */
@Mixin(ChestRenderer.class)
public abstract class ChestReplayMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("HEAD"))
    private void holyLoisNoReplay(BlockEntity chest, ChestRenderState state, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo info) {
        ChestReplay.seen(chest, partial);
    }
}
