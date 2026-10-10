package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import holylois.boombox.DeathSlots;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Picking your own death loot back up: into its old slot when that is empty, otherwise the normal pickup. */
@Mixin(ItemEntity.class)
public abstract class DeathSlotsPickupMixin {
    @WrapOperation(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean holyLoisBackToSlot(Inventory inventory, ItemStack stack, Operation<Boolean> original, Player player) {
        if (DeathSlots.restore((ItemEntity) (Object) this, player, stack)) return true;
        return original.call(inventory, stack);
    }
}
