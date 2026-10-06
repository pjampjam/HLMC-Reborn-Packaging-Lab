package holylois.boombox.mixins;

import holylois.boombox.ExploredClaims;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.mods.pac.highlight.ClaimsHighlighter", remap = false)
public abstract class WorldClaimFogMixin {
    @Inject(method = "getColors", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisExploredColors(ResourceKey<Level> dimension, int x, int z, CallbackInfoReturnable<int[]> callback) {
        if (!ExploredClaims.visible(dimension, x, z)) callback.setReturnValue(null);
    }
    @Inject(method = {"getChunkHighlightBluntTooltip", "getChunkHighlightSubtleTooltip"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisExploredTooltip(ResourceKey<Level> dimension, int x, int z, CallbackInfoReturnable<Component> callback) {
        if (!ExploredClaims.visible(dimension, x, z)) callback.setReturnValue(null);
    }
}
