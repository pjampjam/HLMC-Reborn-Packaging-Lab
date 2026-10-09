package holylois.auth.mixins;

import holylois.auth.PanoramaFade;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entering the world from the loading screen or the login form: the last frame fades out instead of cutting (PanoramaFade). */
@Mixin(Gui.class)
public abstract class EntryFadeMixin {
    @Shadow public abstract Screen screen();

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void holyLoisFadeIntoWorld(Screen next, CallbackInfo info) {
        if (next == null && PanoramaFade.holdsClose(screen(), (Gui) (Object) this)) info.cancel();
    }
}
