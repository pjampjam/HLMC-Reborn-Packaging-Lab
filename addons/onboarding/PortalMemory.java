package holylois;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Going back through the portal you just arrived at takes you to the portal you came from, not to whichever portal vanilla
 * finds nearest (up to 128 blocks away). Remembered per player (also across logouts) until the next restart; a destroyed portal falls back to vanilla.
 */
public final class PortalMemory {
    record Link(ResourceKey<Level> from, BlockPos fromPortal, ResourceKey<Level> to, Vec3 arrival) {}
    private static final Map<UUID, Link> LAST = new ConcurrentHashMap<>();
    private static final double SAME_PORTAL = 16 * 16;

    public static TeleportTransition route(ServerPlayer player, ServerLevel level, BlockPos entered, TeleportTransition vanilla) {
        var link = LAST.get(player.getUUID());
        var target = vanilla.newLevel();
        if (link != null && link.to() == level.dimension() && link.from() == target.dimension()
                && entered.distToCenterSqr(link.arrival()) <= SAME_PORTAL && target.getBlockState(link.fromPortal()).is(Blocks.NETHER_PORTAL)) {
            var back = Vec3.atBottomCenterOf(link.fromPortal());
            LAST.put(player.getUUID(), new Link(level.dimension(), entered.immutable(), target.dimension(), back));
            return vanilla.withPosition(back);
        }
        LAST.put(player.getUUID(), new Link(level.dimension(), entered.immutable(), target.dimension(), vanilla.position()));
        return vanilla;
    }

}
