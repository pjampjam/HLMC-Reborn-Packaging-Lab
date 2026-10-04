package holylois.boombox.mixins;

import holylois.boombox.CrownIcon;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game window is titled "Holy Lois: Reborn" instead of "Minecraft* 26.3 - Multiplayer (3rd-party Server)". */
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true, require = 1)
    private void holyLoisTitle(CallbackInfoReturnable<String> callback) {
        callback.setReturnValue(CrownIcon.TITLE);
    }
}
