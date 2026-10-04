package holylois.boombox.mixins;

import holylois.boombox.ToolSwap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inventory Profiles Next treats blacklisted items as "never refill". Tools are on that list for it, because Holy Lois swaps
 * them itself (ToolSwap): unenchanted spares only, cheapest first, with one soft chime.
 */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.autorefill.AutoRefillHandler", remap = false)
public abstract class IpnToolSkipMixin {
    @Inject(method = "isBlackListed", at = @At("HEAD"), cancellable = true, remap = false)
    private void holyLoisToolsAreOurs(org.anti_ad.mc.ipnext.item.ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (ToolSwap.managed(stack.getItemType().getItem())) callback.setReturnValue(true);
    }
}
