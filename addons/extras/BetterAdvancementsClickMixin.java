package holylois.boombox.mixins;

import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Better Advancements 0.6.0.78 switches tabs only when the click has no modifier flags. Num Lock and Caps Lock count
 * as modifiers, so with either on the tabs never changed (upstream issue #260). Check for a left click instead.
 * 26.3 numbers mouse buttons from 1 (vanilla widgets test button() == 1), so left click is 1, not 0.
 */
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementsScreen", remap = false)
public abstract class BetterAdvancementsClickMixin {
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonEvent;modifiers()I"), remap = false)
    private int holyLoisLeftClickOnly(MouseButtonEvent event) {
        return event.button() == 1 ? 0 : 1;
    }
}
