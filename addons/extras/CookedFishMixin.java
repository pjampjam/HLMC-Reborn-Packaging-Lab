package holylois.boombox.mixins;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Furnace, smoker and campfire: a cooked trophy fish keeps its rarity, weight and effects (Legends.cooked). */
@Mixin(SingleItemRecipe.class)
public abstract class CookedFishMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void holyLoisCookedTrophy(SingleRecipeInput input, CallbackInfoReturnable<ItemStack> callback) {
        if ((Object) this instanceof AbstractCookingRecipe) holylois.boombox.Legends.cooked(input.item(), callback.getReturnValue());
    }
}
