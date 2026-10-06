package holylois.boombox.mixins;

import holylois.boombox.ExploredClaims;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.common.mods.pac.highlight.ClaimsHighlighter", remap = false)
public abstract class MiniClaimFogMixin {
    @Inject(method = "getColors", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisExploredColors(ResourceKey<Level> dimension, int x, int z, CallbackInfoReturnable<int[]> callback) {
        if (!ExploredClaims.visible(dimension, x, z)) callback.setReturnValue(null);
    }
    @Inject(method = "chunkIsHighlit", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisExploredHighlight(ResourceKey<Level> dimension, int x, int z, CallbackInfoReturnable<Boolean> callback) {
        if (!ExploredClaims.visible(dimension, x, z)) callback.setReturnValue(false);
    }
}
