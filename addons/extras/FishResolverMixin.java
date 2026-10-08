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
        ((FishScaled) state).holyLois$setFishScale(FishLook.scale(stack, context, owner));
    }
}
