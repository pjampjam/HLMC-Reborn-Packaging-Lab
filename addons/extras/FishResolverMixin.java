package holylois.boombox.mixins;

import holylois.boombox.FishLook;
import holylois.boombox.FishScaled;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every held, dropped, framed or cutting-board item passes here: weighed fish remember their size for drawing. */
@Mixin(ItemModelResolver.class)
public abstract class FishResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void holyLoisFishSize(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo info) {
        float scale = FishLook.scale(stack, context, owner);
        // On the ground a big fish grows around its middle: lift it so its belly stays on the block (fish sprites start ~0.2 up).
        ((FishScaled) state).holyLois$setFish(scale, context == ItemDisplayContext.GROUND ? 0.3f * (scale - 1) : 0);
        int tint = FishLook.tint(stack);
        if (tint != 0) ((FishScaled) state).holyLois$tint(tint);
    }
}
