package holylois.boombox.mixins;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/**
 * Enchantment Descriptions already explains every enchantment, including Nemo's (lang files in this mod). Nemo's own
 * tooltip added a second copy behind a "Hold Shift" line, so it is skipped.
 */
@Pseudo
@Mixin(targets = "com.nemonotfound.nemos.enchantments.events.ItemTooltipEvent", remap = false)
public abstract class NemoTooltipMixin {
    @Inject(method = "addEnchantmentDescriptionTooltips", at = @At("HEAD"), cancellable = true, remap = false)
    private static void holyLoisOneDescription(ItemStack stack, List<Component> lines, CallbackInfo callback) {
        callback.cancel();
    }
}
