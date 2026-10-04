package holylois.mixins;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

/**
 * Player death drops (tagged by DeathDropsMixin) last 30 minutes instead of 5, and only their owner can pick them up
 * during the first 5 minutes (vanilla's item target). Negative age and the target persist across saves and restarts.
 */
@Mixin(value=ItemEntity.class, remap=false)
public abstract class DeathLootMixin {
    @Shadow private int age;
    @Shadow private UUID target;
    @Inject(method="tick", at=@At("HEAD"), remap=false)
    private void holyLoisDeathGrace(CallbackInfo callback) {
        ItemEntity item = (ItemEntity)(Object)this;
        if (item.level().isClientSide() || !item.entityTags().contains(holylois.DeathLoot.DROP_TAG)) return;
        // 6000 is vanilla's expiry, so -30000 adds 30 minutes.
        if (item.addTag(holylois.DeathLoot.GRACE_TAG)) age = Math.min(age, -30000);
        if (target != null && age > -30000 + holylois.DeathLoot.OWNER_ONLY_TICKS) target = null;
    }
}
