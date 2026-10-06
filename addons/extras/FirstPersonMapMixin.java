package holylois.boombox.mixins;

import dev.tr7zw.firstperson.api.FirstPersonAPI;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use the live map renderer for maps while FirstPerson keeps the rest of the body visible. */
@Mixin(targets = "dev.tr7zw.firstperson.LogicHandler", remap = false)
public abstract class FirstPersonMapMixin {
    @Inject(method = "showVanillaHands(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisReadableMap(ItemStack main, ItemStack off, CallbackInfoReturnable<Boolean> callback) {
        if (FirstPersonAPI.isEnabled() && (map(main) || map(off) || consuming())) callback.setReturnValue(true);
    }
    @Inject(method = "hideArmsAndItems(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisSingleMapHands(net.minecraft.world.entity.LivingEntity player, ItemStack main, ItemStack off, CallbackInfoReturnable<Boolean> callback) {
        if (FirstPersonAPI.isEnabled() && (map(main) || map(off) || consuming())) callback.setReturnValue(true);
    }
    private static boolean consuming() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) return false;
        var animation = player.getUseItem().getUseAnimation();
        return animation == net.minecraft.world.item.ItemUseAnimation.EAT || animation == net.minecraft.world.item.ItemUseAnimation.DRINK;
    }
    private static boolean map(ItemStack stack) { return stack.is(Items.MAP) || stack.is(Items.FILLED_MAP); }
}
