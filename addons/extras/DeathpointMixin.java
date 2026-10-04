package holylois.boombox.mixins;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Xaero's Minimap: no death marker for a death in PvP (the server sends holylois:pvp_death right before it). */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.waypoint.DeathpointHandler", remap = false)
public abstract class DeathpointMixin {
    @Inject(method = "createDeathpoint(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void holyLoisNoPvpDeathpoint(Player player, CallbackInfo callback) {
        if (System.currentTimeMillis() < holylois.boombox.BoomboxClient.skipDeathpointUntil) {
            holylois.boombox.BoomboxClient.skipDeathpointUntil = 0;
            callback.cancel();
        }
    }
}
