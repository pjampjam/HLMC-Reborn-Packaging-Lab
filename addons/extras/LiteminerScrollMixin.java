package holylois.boombox.mixins;

import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The mouse wheel no longer switches LiteMiner shapes while the vein mine key is held; it scrolls the hotbar as usual. */
@Pseudo
@Mixin(targets = "com.iamkaf.liteminer.rendering.HUD", remap = false)
public abstract class LiteminerScrollMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, remap = false)
    private static void holyLoisNoShapeScroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<InteractionResult> callback) {
        callback.setReturnValue(InteractionResult.PASS);
    }
}
