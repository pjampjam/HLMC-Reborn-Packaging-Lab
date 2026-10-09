package holylois.mixins;

import holylois.ServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Celebration fireworks (events, daily rewards, codes) only look and sound like fireworks: they never hurt anyone. */
@Mixin(FireworkRocketEntity.class)
public abstract class CelebrationFireworkMixin {
    @Inject(method = "dealExplosionDamage", at = @At("HEAD"), cancellable = true)
    private void holyLoisHarmless(ServerLevel level, CallbackInfo callback) {
        if (((FireworkRocketEntity) (Object) this).entityTags().contains(ServerEvents.CELEBRATION_TAG)) callback.cancel();
    }
}
