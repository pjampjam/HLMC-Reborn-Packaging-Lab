package holylois.boombox.mixins;

import holylois.boombox.PlacedRelics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class RelicBlockChangeMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"), require = 1)
    private void holyLoisForgetRemoved(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && (Object) this instanceof ServerLevel level) PlacedRelics.changed(level, pos);
    }
}
