package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ultimatearpg.item.ArpgDataComponents;
import holylois.boombox.PartySupport;
import holylois.boombox.RuneforgedAcquisition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Chest provenance must not prevent a real transfer into an otherwise matching stack. */
@Mixin(ChestMenu.class)
public abstract class RuneforgedChestTransferMixin extends AbstractContainerMenu {
    protected RuneforgedChestTransferMixin(MenuType<?> type, int id) { super(type, id); }

    @WrapMethod(method = "quickMoveStack")
    private ItemStack holyLoisAcquireOnTransfer(Player player, int index, Operation<ItemStack> original) {
        if (!(player instanceof ServerPlayer serverPlayer) || !PartySupport.ready(serverPlayer)
            || index < 0 || index >= slots.size() - 36) return original.call(player, index);
        ItemStack stack = getSlot(index).getItem();
        String source = RuneforgedAcquisition.source(stack);
        if (source == null) return original.call(player, index);
        ItemStack receipt = stack.copy();
        int before = stack.getCount();
        stack.remove(ArpgDataComponents.ADVANCEMENT_SOURCE);
        try {
            return original.call(player, index);
        } finally {
            int moved = before - stack.getCount();
            // Failed/partial transfers leave the remaining chest loot's provenance intact.
            if (!stack.isEmpty()) stack.set(ArpgDataComponents.ADVANCEMENT_SOURCE, source);
            if (moved > 0) {
                receipt.setCount(moved);
                RuneforgedAcquisition.acquired(serverPlayer, receipt);
            }
        }
    }
}
