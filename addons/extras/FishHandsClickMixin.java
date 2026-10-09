package holylois.boombox.mixins;

import holylois.boombox.FishData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory F (swap with the off hand) never moves a heavy fish into the off hand, nor anything into it while a heavy fish is
 * held (owner round 5: spamming F on a fish raced FishHands' clean-up and duplicated the fish). Vanilla's swap does not ask the
 * off-hand slot, so it is refused here, on the server and in the client's prediction alike.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class FishHandsClickMixin {
    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisNoFishInOffhand(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo info) {
        if (input != ContainerInput.SWAP || button != 40 || slotIndex < 0 || slotIndex >= slots.size()) return;
        var moving = slots.get(slotIndex).getItem();
        if (FishData.twoHanded(moving) || !moving.isEmpty() && FishData.twoHanded(player.getMainHandItem())) info.cancel();
    }
}
