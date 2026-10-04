package holylois.boombox.mixins;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla hides the face between two glass blocks only when they are the same colour. Two coplanar faces of different colours
 * flicker with shaders (temporal anti-aliasing), so the shared face between any two glass blocks is skipped.
 */
@Mixin(HalfTransparentBlock.class)
public abstract class GlassSeamMixin {
    @Inject(method = "skipRendering", at = @At("RETURN"), cancellable = true, require = 1)
    private void holyLoisGlassSeam(BlockState state, BlockState adjacent, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && state.getBlock() instanceof TransparentBlock && adjacent.getBlock() instanceof TransparentBlock)
            callback.setReturnValue(true);
    }
}
