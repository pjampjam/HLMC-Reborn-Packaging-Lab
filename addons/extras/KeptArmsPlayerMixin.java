package holylois.boombox.mixins;

import holylois.boombox.KeptArms;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players: remember the arms after Not Enough Animations has posed them (applied late, priority 2000). */
@Mixin(value = PlayerModel.class, priority = 2000)
public abstract class KeptArmsPlayerMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 1)
    private void holyLoisKeepPlayerArms(AvatarRenderState state, CallbackInfo info) { KeptArms.capture(this, state); }
}
