package holylois.boombox.mixins;

import holylois.boombox.FishLook;
import holylois.boombox.FishScaled;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class FishRenderStateMixin implements FishScaled {
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;
    @Shadow private int activeLayerCount;
    @Unique private float holyLoisFishScale = 1, holyLoisFishLift;

    /** Tints every layer's first tint slot (fish sprites are generated items: their quads use tint 0). */
    @Override public void holyLois$tint(int argb) {
        for (int i = 0; i < activeLayerCount; i++) {
            var tints = layers[i].tintLayers();
            if (tints.isEmpty()) tints.add(argb);
            else tints.set(0, multiply(tints.getInt(0), argb));
        }
    }

    @Unique private static int multiply(int a, int b) {
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255, g = (a >> 8 & 255) * (b >> 8 & 255) / 255, bl = (a & 255) * (b & 255) / 255;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    @Override public float holyLois$fishScale() { return holyLoisFishScale; }
    @Override public float holyLois$fishLift() { return holyLoisFishLift; }
    @Override public void holyLois$setFish(float scale, float lift) { holyLoisFishScale = scale; holyLoisFishLift = lift; }

    @Inject(method = "clear", at = @At("HEAD"))
    private void holyLoisResetFish(CallbackInfo info) { holyLoisFishScale = 1; holyLoisFishLift = 0; }

    @Inject(method = "submit", at = @At("HEAD"))
    private void holyLoisFishStart(CallbackInfo info) { FishLook.current = holyLoisFishScale; FishLook.lift = holyLoisFishLift; }

    @Inject(method = "submit", at = @At("RETURN"))
    private void holyLoisFishEnd(CallbackInfo info) { FishLook.current = 1; FishLook.lift = 0; }
}
