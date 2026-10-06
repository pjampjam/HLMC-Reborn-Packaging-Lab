package holylois.mixins;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A player cannot use an untagged friend to teleport into an active fight. */
@Mixin(targets = "com.fibermc.essentialcommands.teleportation.QueuedPlayerTeleport", remap = false)
public interface CombatPlayerDestinationMixin {
    @Accessor("targetPlayer") ServerPlayer holyLoisTarget();
}
