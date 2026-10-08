package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import holylois.boombox.PartySupport;
import holylois.boombox.RuneforgedAcquisition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;

/** Process already-owned stones before a drag, rather than waiting for the next native scan. */
@Mixin(AbstractContainerMenu.class)
public abstract class RuneforgedInventoryClickMixin {
    @WrapMethod(method = "clicked")
    private void holyLoisAcquireOwnedStones(int slot, int button, ContainerInput type, Player player,
                                          Operation<Void> original) {
        if (player instanceof ServerPlayer serverPlayer && PartySupport.ready(serverPlayer)) {
            var menu = (AbstractContainerMenu)(Object)this;
            RuneforgedAcquisition.acquired(serverPlayer, menu.getCarried());
            var inventory = serverPlayer.getInventory();
            for (int i = 0; i < 36; i++) RuneforgedAcquisition.acquired(serverPlayer, inventory.getItem(i));
        }
        original.call(slot, button, type, player);
    }
}
