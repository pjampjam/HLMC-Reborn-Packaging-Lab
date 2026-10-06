package holylois.boombox.mixins;

import holylois.boombox.ClaimFeedback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.pac.common.server.player.localization.AdaptiveLocalizer", remap = false)
public abstract class ClaimFeedbackMixin {
    @Inject(method = "getFor(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisPlainClaimMessage(ServerPlayer player, String key, Object[] args, CallbackInfoReturnable<MutableComponent> callback) {
        var text = ClaimFeedback.message(player, key);
        if (text != null) callback.setReturnValue(text);
    }
    @Inject(method = "getFor(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisPlainClaimComponent(ServerPlayer player, Component original, CallbackInfoReturnable<Component> callback) {
        if (original.getContents() instanceof TranslatableContents translated) {
            var text = ClaimFeedback.message(player, translated.getKey());
            if (text != null) callback.setReturnValue(text.setStyle(original.getStyle()));
        }
    }
}
