package holylois.mixins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tags a dying player's fresh item drops (see holylois.DeathLoot). */
@Mixin(value=Player.class, remap=false)
public abstract class DeathDropsMixin {
    @Inject(method="dropEquipment", at=@At("RETURN"), remap=false)
    private void holyLoisTagDeathDrops(ServerLevel level, CallbackInfo callback) {
        if ((Object)this instanceof ServerPlayer player) holylois.DeathLoot.markDrops(level, player);
    }
}
