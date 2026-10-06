package holylois.boombox.mixins;

import holylois.boombox.ClaimAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop an untrusted player moving claimed mobs by body collision. */
@Mixin(Entity.class)
public abstract class ClaimMobPushMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisProtectedMobPush(Entity other, CallbackInfo callback) {
        var self = (Entity)(Object)this;
        ServerPlayer player = self instanceof ServerPlayer p ? p : other instanceof ServerPlayer p ? p : null;
        Mob mob = self instanceof Mob m ? m : other instanceof Mob m ? m : null;
        if (player != null && mob != null && mob.level() instanceof ServerLevel level
            && !ClaimAccess.canUse(player, level, mob.blockPosition())) callback.cancel();
    }
}
