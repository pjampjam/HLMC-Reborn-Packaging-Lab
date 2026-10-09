package holylois.boombox.mixins;

import holylois.boombox.BoomboxUseGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BoomboxUseGuardMixin {
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true,require=1)
    private void holylois$keepEatingPress(CallbackInfo ci){if(BoomboxUseGuard.blockFollowup())ci.cancel();}
}
