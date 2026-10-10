package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import holylois.boombox.DeathSlots;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Death drop of the main inventory: each dropped stack remembers its slot (see DeathSlots). */
@Mixin(Inventory.class)
public abstract class DeathSlotsMixin {
    @WrapOperation(method = "dropAll()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;createItemStackToDrop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity holyLoisRememberSlot(Player player, ItemStack stack, boolean randomly, boolean thrower, Operation<ItemEntity> original,
        @Local(ordinal = 0) int index) {
        var item = original.call(player, stack, randomly, thrower);
        DeathSlots.dropped(player, index, item);
        return item;
    }
}
