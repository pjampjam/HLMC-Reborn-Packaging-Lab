package holylois.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.ArrayList;

/** Xaero's Minimap only (loaded when xaerominimap is present): removes death markers once the server says the loot is gone. */
final class XaeroDeathpoints {
    private XaeroDeathpoints() {}

    /** Number of markers removed, or -1 while the minimap session is not ready yet (just after joining). */
    static int remove(Identifier dimension, BlockPos pos) {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        var root = session == null ? null : session.getWorldManager().getCurrentRootContainer();
        if (root == null) return -1;
        int removed = 0;
        for (MinimapWorld world : root.getAllWorldsIterable()) {
            var dim = world.getDimId();
            if (!dimension.equals(dim != null ? dim.identifier() : world.getContainer().getEquivalentDimId())) continue;
            int before = removed;
            for (var set : world.getIterableWaypointSets()) {
                var gone = new ArrayList<Waypoint>();
                for (Waypoint point : set.getWaypoints())
                    if ((point.getPurpose() == WaypointPurpose.DEATH || point.getPurpose() == WaypointPurpose.OLD_DEATH)
                        && Math.abs(point.getX() - pos.getX()) <= 2 && Math.abs(point.getZ() - pos.getZ()) <= 2
                        && (!point.isYIncluded() || Math.abs(point.getY() - pos.getY()) <= 3)) gone.add(point);
                set.removeAll(gone);
                removed += gone.size();
            }
            if (removed > before) {
                try { session.getWorldManagerIO().saveWorld(world); }
                catch (Exception ignored) { /* Xaero saves again on its own later; the marker is already gone in memory. */ }
            }
        }
        return removed;
    }
}
