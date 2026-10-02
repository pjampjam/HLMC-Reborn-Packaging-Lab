package holylois.auth.mixins;

import holylois.auth.HolyLoisAuthClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the routine native progress toast. Download failures use a different translation key. */
@Mixin(value=SystemToast.class, remap=false)
public abstract class AuthToastMixin {
    @Inject(method="addOrUpdate", at=@At("HEAD"), cancellable=true, remap=false)
    private static void holyLoisQuietProgress(ToastManager manager, SystemToast.SystemToastId id,
        Component title, Component message, CallbackInfo callback) {
        if (HolyLoisAuthClient.isHolyLois(Minecraft.getInstance())
            && title.getContents() instanceof TranslatableContents text
            && text.getKey().equals("download.pack.title")) callback.cancel();
    }
}
