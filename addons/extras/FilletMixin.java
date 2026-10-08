package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import holylois.boombox.FishLook;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.ArrayList;
import java.util.List;

/** Filleting a trophy fish on the Farmer's Delight cutting board gives more of everything, by its size (Mythic: up to 8x). */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity")
public abstract class FilletMixin {
    @Shadow public abstract ItemStack getStoredItem();

    @WrapOperation(method = "lambda$processStoredItemUsingTool$0", at = @At(value = "INVOKE",
        target = "Lvectorwing/farmersdelight/common/crafting/CuttingBoardRecipe;rollResults(Lnet/minecraft/util/RandomSource;I)Ljava/util/List;"))
    private List<ItemStack> holyLoisFillets(CuttingBoardRecipe recipe, RandomSource random, int fortune, Operation<List<ItemStack>> original) {
        List<ItemStack> results = original.call(recipe, random, fortune);
        int times = FishLook.fillets(getStoredItem());
        if (times <= 1) return results;
        var more = new ArrayList<ItemStack>();
        for (var stack : results) {
            int total = stack.getCount() * times;
            while (total > 0) { int part = Math.min(total, stack.getMaxStackSize()); more.add(stack.copyWithCount(part)); total -= part; }
        }
        return more;
    }
}
