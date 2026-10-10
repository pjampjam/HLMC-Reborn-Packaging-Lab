package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import holylois.boombox.DeathSlots;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Death drop of armor and the off-hand: the stack remembers its slot too (players only). */
@Mixin(EntityEquipment.class)
public abstract class DeathSlotsEquipmentMixin {
    @WrapOperation(method = "dropAll(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;createItemStackToDrop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity holyLoisRememberEquipmentSlot(LivingEntity entity, ItemStack stack, boolean randomly, boolean thrower, Operation<ItemEntity> original) {
        var item = original.call(entity, stack, randomly, thrower);
        if (entity instanceof Player player) DeathSlots.droppedEquipment(player, stack, item);
        return item;
    }
}
