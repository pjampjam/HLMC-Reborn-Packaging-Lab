package holylois.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vein mining is always shapeless on Holy Lois: tunnel, staircase and 3x3 shapes are ignored. */
@Pseudo
@Mixin(targets = "com.iamkaf.liteminer.LiteminerPlayerState", remap = false)
public abstract class LiteminerShapeMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void holyLoisShapeless(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(0);
    }
}
