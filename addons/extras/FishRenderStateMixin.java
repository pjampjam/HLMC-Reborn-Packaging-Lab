package holylois.boombox.mixins;

import holylois.boombox.FishLook;
import holylois.boombox.FishScaled;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class FishRenderStateMixin implements FishScaled {
    @Unique private float holyLoisFishScale = 1;

    @Override public float holyLois$fishScale() { return holyLoisFishScale; }
    @Override public void holyLois$setFishScale(float scale) { holyLoisFishScale = scale; }

    @Inject(method = "clear", at = @At("HEAD"))
    private void holyLoisResetFish(CallbackInfo info) { holyLoisFishScale = 1; }

    @Inject(method = "submit", at = @At("HEAD"))
    private void holyLoisFishStart(CallbackInfo info) { FishLook.current = holyLoisFishScale; }

    @Inject(method = "submit", at = @At("RETURN"))
    private void holyLoisFishEnd(CallbackInfo info) { FishLook.current = 1; }
}
