package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import holylois.boombox.FishData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Filleting a trophy fish on the Farmer's Delight cutting board: slices by its weight (FishData.slices), four per knife cut.
 * A big fish takes several cuts and keeps losing health (a damage bar) until the last cut uses it up; taken off the board
 * it keeps the damage. Slices remember rarity, Shiny and species and give a smaller share of its effect (Legends.fillet).
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity")
public abstract class FilletMixin {
    @Shadow public abstract ItemStack getStoredItem();

    @WrapOperation(method = "lambda$processStoredItemUsingTool$0", at = @At(value = "INVOKE",
        target = "Lvectorwing/farmersdelight/common/crafting/CuttingBoardRecipe;rollResults(Lnet/minecraft/util/RandomSource;I)Ljava/util/List;"))
    private List<ItemStack> holyLoisFillets(CuttingBoardRecipe recipe, RandomSource random, int fortune, Operation<List<ItemStack>> original) {
        List<ItemStack> results = original.call(recipe, random, fortune);
        ItemStack fish = getStoredItem();
        int slices = FishData.slicesThisCut(fish);
        if (FishData.slices(fish) == 0 || results.isEmpty()) return results;
        var more = new ArrayList<ItemStack>();
        for (int i = 0; i < results.size(); i++) {
            var stack = results.get(i).copy();
            // The first result is the meat: this cut's share. Side products (bones and the like) come once per cut.
            int total = i == 0 ? slices : stack.getCount();
            if (i == 0) holylois.boombox.Legends.fillet(fish, stack);
            while (total > 0) { int part = Math.min(total, stack.getMaxStackSize()); more.add(stack.copyWithCount(part)); total -= part; }
        }
        return more;
    }

    /** Farmer's Delight says "1 remaining..." (one fish on the board): for a trophy, say how many cuts are left instead. */
    @WrapOperation(method = "lambda$processStoredItemUsingTool$0", at = @At(value = "INVOKE",
        target = "Lvectorwing/farmersdelight/common/utility/TextUtils;block(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private net.minecraft.network.chat.MutableComponent holyLoisCutsLeft(String key, Object[] args, Operation<net.minecraft.network.chat.MutableComponent> original) {
        ItemStack fish = getStoredItem();
        if (!"cutting_board.remaining_items".equals(key) || FishData.slices(fish) == 0) return original.call(key, args);
        return net.minecraft.network.chat.Component.translatable("holylois.fish.cuts_left", FishData.cuts(fish) - fish.getDamageValue());
    }

    /** Not the last cut: the fish stays on the board, one cut more damaged, instead of being used up. */
    @WrapOperation(method = "lambda$processStoredItemUsingTool$0", at = @At(value = "INVOKE",
        target = "Lvectorwing/farmersdelight/refabricated/inventory/ItemStackHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack holyLoisCutAgain(vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler inventory, int slot, int amount, boolean simulate, Operation<ItemStack> original) {
        ItemStack fish = getStoredItem();
        int cuts = FishData.cuts(fish);
        if (FishData.slices(fish) == 0 || fish.getDamageValue() + 1 >= cuts) return original.call(inventory, slot, amount, simulate);
        fish.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, cuts);
        fish.setDamageValue(fish.getDamageValue() + 1);
        holylois.boombox.FishTraits.wear(fish, (cuts - fish.getDamageValue()) / (float) cuts);
        var self = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        self.setChanged();
        if (self.getLevel() != null) self.getLevel().sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        return ItemStack.EMPTY;
    }
}
