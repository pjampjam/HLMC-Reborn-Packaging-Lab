package holylois.boombox.mixins;

import holylois.boombox.PackLoadingBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.OptionalLong;

/** The server resource pack download reports to PackLoadingBar (under the loading bar) instead of a corner toast. */
@Mixin(targets = "net.minecraft.client.resources.server.DownloadedPackSource$3")
public abstract class PackToastMixin {
    @Inject(method = "updateToast", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisNoToast(CallbackInfo info) { info.cancel(); }

    @Inject(method = "downloadStart", at = @At("HEAD"), require = 1)
    private void holyLoisStart(OptionalLong size, CallbackInfo info) { PackLoadingBar.downloadStart(size); }

    @Inject(method = "downloadedBytes", at = @At("HEAD"), require = 1)
    private void holyLoisBytes(long bytes, CallbackInfo info) { PackLoadingBar.downloaded(bytes); }

    @Inject(method = "requestFinished", at = @At("HEAD"), require = 1)
    private void holyLoisFinished(boolean ok, CallbackInfo info) { PackLoadingBar.finished(ok); }
}
