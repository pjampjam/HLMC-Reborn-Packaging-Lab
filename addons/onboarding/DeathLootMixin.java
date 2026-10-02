package holylois.mixins;

import io.github.flemmli97.flan.utils.IOwnedItem;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends only Flan-identified player death drops, once. Vanilla saves age and tags. */
@Mixin(value=ItemEntity.class, remap=false)
public abstract class DeathLootMixin {
    @Shadow private int age;
    @Inject(method="tick", at=@At("HEAD"), remap=false)
    private void holyLoisDeathGrace(CallbackInfo callback) {
        ItemEntity item = (ItemEntity)(Object)this;
        if (item.level().isClientSide()) return;
        if (((IOwnedItem)item).flan$getDeathPlayer() != null
            && item.addTag("holylois:death_grace")) {
            // 6000 is vanilla's expiry. Negative age also persists across saves/restarts.
            age = Math.min(age, -30000);
        }
    }
}

