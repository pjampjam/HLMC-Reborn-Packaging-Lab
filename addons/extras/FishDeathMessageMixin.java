package holylois.boombox.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import holylois.boombox.FishData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** "slain by X using [Mythic Cod]": a trophy fish counts as a named weapon in death messages (its name keeps the shine). */
@Mixin(DamageSource.class)
public abstract class FishDeathMessageMixin {
    @WrapOperation(method = "getLocalizedDeathMessage", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z"))
    private boolean holyLoisTrophyWeapon(ItemStack stack, DataComponentType<?> type, Operation<Boolean> original) {
        return original.call(stack, type) || type == DataComponents.CUSTOM_NAME && FishData.weighed(stack);
    }
}
