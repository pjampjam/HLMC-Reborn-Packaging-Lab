package holylois.mixins;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AudioPlayer /audioplayer url goes through holylois.AudioUrlGuard (address check, cooldown, log, bounded download). */
@Pseudo
@Mixin(targets = "de.maxhenkel.audioplayer.audioloader.importer.UrlImporter", remap = false)
public abstract class AudioUrlMixin {
    @Shadow @Final private String urlString;

    @Inject(method = "onPreprocess", at = @At("HEAD"), remap = false)
    private void holyLoisCheckUrl(ServerPlayer player, CallbackInfoReturnable<?> callback) {
        holylois.AudioUrlGuard.check(player, urlString);
    }

    @Inject(method = "onProcess", at = @At("HEAD"), cancellable = true, remap = false)
    private void holyLoisBoundedDownload(ServerPlayer player, CallbackInfoReturnable<byte[]> callback) throws Exception {
        long max = 20_000_000;
        try { max = de.maxhenkel.audioplayer.AudioPlayerMod.SERVER_CONFIG.maxUploadSize.get(); } catch (RuntimeException | LinkageError ignored) { }
        callback.setReturnValue(holylois.AudioUrlGuard.download(urlString, max));
    }
}
