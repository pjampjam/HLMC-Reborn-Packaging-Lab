package holylois.boombox.mixins;

import holylois.boombox.ToolSwap;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The same key press that just sorted the inventory (IpnSortKeyMixin) is swallowed here, so REI does not open a recipe too. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.gui.ScreenOverlayImpl", remap = false)
public abstract class ReiSortGuardMixin {
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void holyLoisSortUsedThisKey(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (System.nanoTime() - ToolSwap.sortRanAt < 50_000_000L) callback.setReturnValue(true);
    }
}
