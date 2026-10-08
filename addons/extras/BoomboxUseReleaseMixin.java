package holylois.boombox.mixins;

import holylois.boombox.BoomboxUseGuard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear on the actual input release, including a quick release/repress between ticks. */
@Mixin(KeyMapping.class)
public abstract class BoomboxUseReleaseMixin {
    @Inject(method="setDown",at=@At("HEAD"),require=1)
    private void holylois$releaseUse(boolean down,CallbackInfo ci){
        var mc=Minecraft.getInstance();
        if(!down && mc!=null && mc.options!=null && (Object)this==mc.options.keyUse)BoomboxUseGuard.released();
    }
}
