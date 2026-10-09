package holylois.boombox.mixins;

import holylois.boombox.FishData;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The off-hand slot refuses a heavy fish, and anything at all while a heavy fish is held in the main hand. */
@Mixin(Slot.class)
public abstract class FishHandsSlotMixin {
    @Shadow @Final public Container container;
    @Shadow public abstract int getContainerSlot();

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisOffhandTaken(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!(container instanceof Inventory inventory) || getContainerSlot() != Inventory.SLOT_OFFHAND) return;
        if (FishData.twoHanded(stack) || FishData.twoHanded(inventory.player.getMainHandItem())) callback.setReturnValue(false);
    }
}
