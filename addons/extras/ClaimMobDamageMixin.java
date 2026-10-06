package holylois.boombox.mixins;

import holylois.boombox.ClaimAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enforce claim access for any damage attributed to a player, including projectiles. */
@Mixin(LivingEntity.class)
public abstract class ClaimMobDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisProtectedMobDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if ((Object)this instanceof Mob mob && source.getEntity() instanceof ServerPlayer player
            && !ClaimAccess.canUse(player, level, mob.blockPosition())) callback.setReturnValue(false);
    }
}
