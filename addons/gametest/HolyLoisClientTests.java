package holylois.gametest;

import holylois.boombox.DeathLootGone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.world.MinimapWorld;

/**
 * Runs inside a real game with the full client mod set plus our add-ons (gradlew :dev:runClientGameTest). Screenshots
 * land in run/gametest/screenshots for a look; any failed check or exception fails the run.
 */
public final class HolyLoisClientTests implements FabricClientGameTest {
    /**
     * Ends with an immediate exit instead of closing the world: with the full pack, Fabric's test world close deadlocks
     * (client waits for the integrated server, which waits for the test phase). Exit code 0 = all passed, 1 = a failure.
     */
    @Override public void runTest(ClientGameTestContext context) {
        int code = 1;
        try {
            context.takeScreenshot("01-title");
            var world = context.worldBuilder().create();
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("02-world");

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.takeScreenshot("03-inventory");
            context.setScreen(() -> null);

            deathMarkerGoesWhenLootIsGone(context, world);
            log("ALL HOLY LOIS CLIENT TESTS PASSED");
            code = 0;
        } catch (Throwable failure) {
            org.slf4j.LoggerFactory.getLogger("HolyLoisTest").error("HOLY LOIS CLIENT TESTS FAILED", failure);
        } finally {
            org.apache.logging.log4j.LogManager.shutdown();
            Runtime.getRuntime().halt(code);
        }
    }

    /** Client half of the death marker cleanup: Xaero makes a death marker, the server says the loot is gone, it disappears. */
    private static void deathMarkerGoesWhenLootIsGone(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        // Die far from spawn: Xaero deletes a death marker you stand on (delete_reached_deathpoints), and respawn is at spawn.
        world.getServer().runCommand("tp @a 200 -60 200");
        world.getConnection().waitForChunksRender();
        world.getServer().runCommand("kill @a");
        context.waitForScreen(DeathScreen.class);
        context.waitTicks(40);
        log("death markers on the death screen: " + context.computeOnClient(client -> deathMarkers()));
        context.takeScreenshot("04-death-screen");
        context.clickScreenButton("deathScreen.respawn");
        context.waitFor(client -> client.player != null && client.player.isAlive(), 600);
        log("respawned");
        world.getConnection().waitForChunksRender();
        context.waitTicks(120); // the client hands the packet to Xaero after its session settles
        log("xaero after respawn: " + context.computeOnClient(client -> describeXaero()));
        int before = context.computeOnClient(client -> deathMarkers());
        check(before >= 1, "Xaero made a death marker (found " + before + ")");
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var death = player.getLastDeathLocation().orElseThrow();
            check(DeathLootGone.send(player, death.dimension().identifier(), death.pos()), "server could send death_loot_gone");
        });
        context.waitTicks(60);
        int after = context.computeOnClient(client -> deathMarkers());
        check(after == before - 1, "death marker removed after the loot was gone (" + before + " -> " + after + ")");
        context.takeScreenshot("05-after-loot-gone");
    }

    private static String describeXaero() {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return "no minimap session";
        var manager = session.getWorldManager();
        var text = new StringBuilder("current root=" + manager.getCurrentRootContainer() + " auto world=" + manager.getAutoWorld()
            + " current world=" + manager.getCurrentWorld() + " roots:");
        for (var root : manager.getRootContainers())
            for (MinimapWorld minimapWorld : root.getAllWorldsIterable()) {
                text.append(" [").append(minimapWorld.getFullPath()).append(" dim=").append(minimapWorld.getDimId());
                for (var set : minimapWorld.getIterableWaypointSets())
                    for (Waypoint point : set.getWaypoints()) text.append(' ').append(set.getName()).append(':').append(point.getPurpose()).append('@').append(point.getX()).append(',').append(point.getZ());
                text.append(']');
            }
        return text.toString();
    }

    private static int deathMarkers() {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        var root = session == null ? null : session.getWorldManager().getCurrentRootContainer();
        if (root == null) return -1;
        int count = 0;
        for (MinimapWorld minimapWorld : root.getAllWorldsIterable())
            for (var set : minimapWorld.getIterableWaypointSets())
                for (Waypoint point : set.getWaypoints())
                    if (point.getPurpose() == WaypointPurpose.DEATH || point.getPurpose() == WaypointPurpose.OLD_DEATH) count++;
        return count;
    }

    private static void log(String text) { org.slf4j.LoggerFactory.getLogger("HolyLoisTest").info(text); }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Holy Lois client test failed: " + what);
        org.slf4j.LoggerFactory.getLogger("HolyLoisTest").info("PASS {}", what);
    }
}
