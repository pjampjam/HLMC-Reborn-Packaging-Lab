package holylois.mixins;

import com.fibermc.essentialcommands.teleportation.QueuedTeleport;
import com.fibermc.essentialcommands.teleportation.QueuedPlayerTeleport;
import com.fibermc.essentialcommands.types.MinecraftLocation;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Validate at execution too: a warmup or a request may predate the fight. */
@Mixin(targets = "com.fibermc.essentialcommands.teleportation.PlayerTeleporter", remap = false)
public abstract class CombatTeleportMixin {
    @Inject(method = "requestTeleport(Lcom/fibermc/essentialcommands/teleportation/QueuedTeleport;)V", at = @At("HEAD"), cancellable = true)
    private static void holyLoisQueuedCombat(QueuedTeleport teleport, CallbackInfo callback) {
        if (refuse(teleport)) callback.cancel();
    }
    @Inject(method = "teleport(Lcom/fibermc/essentialcommands/teleportation/QueuedTeleport;)V", at = @At("HEAD"), cancellable = true)
    private static void holyLoisFinishCombat(QueuedTeleport teleport, CallbackInfo callback) {
        if (refuse(teleport)) { teleport.complete(); callback.cancel(); }
    }
    private static boolean refuse(QueuedTeleport teleport) {
        if (holylois.CombatTag.refuseTeleport(teleport.getPlayerData().getPlayer())) return true;
        if (teleport instanceof QueuedPlayerTeleport
            && holylois.CombatTag.teleportWait(((CombatPlayerDestinationMixin) teleport).holyLoisTarget().getUUID()) > 0) {
            teleport.getPlayerData().getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.literal("Teleport cancelled: that player is in combat."));
            return true;
        }
        return false;
    }

    @Inject(method = "execTeleport", at = @At("HEAD"), cancellable = true)
    private static void holyLoisFinalCombat(ServerPlayer player, MinecraftLocation location, MutableComponent name, CallbackInfo callback) {
        if (holylois.CombatTag.refuseTeleport(player)) callback.cancel();
    }
}
