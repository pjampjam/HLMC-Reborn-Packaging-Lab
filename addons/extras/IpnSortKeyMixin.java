package holylois.boombox.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R both sorts (Inventory Profiles Next) and shows a recipe (REI). A sort started from the keyboard while the cursor is on an
 * item is skipped, so R on an item only shows its recipe and R on an empty slot sorts. IPN's sort buttons still work.
 */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.GeneralInventoryActions", remap = false)
public abstract class IpnSortKeyMixin {
    @Inject(method = {"doSort(Lnet/minecraft/world/inventory/AbstractContainerMenu;ZZ)V",
                      "doSortInColumns(Lnet/minecraft/world/inventory/AbstractContainerMenu;ZZ)V",
                      "doSortInRows(Lnet/minecraft/world/inventory/AbstractContainerMenu;ZZ)V"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void holyLoisRecipeFirst(AbstractContainerMenu menu, boolean a, boolean b, CallbackInfo callback) {
        var mc = Minecraft.getInstance();
        var mouse = mc.mouseHandler;
        if (mouse.isLeftPressed() || mouse.isRightPressed() || mouse.isMiddlePressed()) return;
        if (mc.gui.screen() instanceof ContainerHoverAccessor screen) {
            var slot = screen.holyLoisHoveredSlot();
            if (slot != null && slot.hasItem()) callback.cancel();
        }
    }
}
