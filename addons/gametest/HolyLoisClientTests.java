package holylois.gametest;

import holylois.boombox.DeathLootGone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs inside a real game with the full client mod set plus our add-ons (gradlew :dev:runClientGameTest). Every test runs
 * even if an earlier one fails; screenshots land in run/gametest/screenshots.
 */
public final class HolyLoisClientTests implements FabricClientGameTest {
    interface Test { void run(ClientGameTestContext context, TestSingleplayerContext world) throws Exception; }

    /**
     * Ends with an immediate exit instead of closing the world: with the full pack, Fabric's test world close deadlocks
     * (client waits for the integrated server, which waits for the test phase). Exit code 0 = all passed, 1 = a failure.
     */
    @Override public void runTest(ClientGameTestContext context) {
        List<String> failed = new ArrayList<>();
        try {
            context.takeScreenshot("01-title");
            var world = context.worldBuilder().create();
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("02-world");
            run(failed, "inventory opens", context, world, HolyLoisClientTests::inventoryOpens);
            run(failed, "boombox and water", context, world, HolyLoisClientTests::boomboxKeepsWaterSources);
            run(failed, "chest lid stays shut", context, world, HolyLoisClientTests::chestLidStaysShut);
            run(failed, "death marker cleanup", context, world, HolyLoisClientTests::deathMarkerGoesWhenLootIsGone);
        } catch (Throwable setup) {
            failed.add("setup: " + setup);
            org.slf4j.LoggerFactory.getLogger("HolyLoisTest").error("Test world setup failed", setup);
        } finally {
            log(failed.isEmpty() ? "ALL HOLY LOIS CLIENT TESTS PASSED" : "HOLY LOIS CLIENT TESTS FAILED: " + failed);
            org.apache.logging.log4j.LogManager.shutdown();
            Runtime.getRuntime().halt(failed.isEmpty() ? 0 : 1);
        }
    }

    private static void run(List<String> failed, String name, ClientGameTestContext context, TestSingleplayerContext world, Test test) {
        try {
            test.run(context, world);
            log("TEST OK: " + name);
        } catch (Throwable failure) {
            failed.add(name);
            org.slf4j.LoggerFactory.getLogger("HolyLoisTest").error("TEST FAILED: " + name, failure);
            context.runOnClient(client -> { if (client.player != null) client.player.closeContainer(); });
        }
    }

    private static void inventoryOpens(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.takeScreenshot("03-inventory");
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(5);
    }

    /**
     * A boombox placed into flowing water must not become a water source (it only waterlogs in a real source), and
     * breaking either kind must leave the same number of sources as before. Goes through BlockItem.place like a player.
     */
    private static void boomboxKeepsWaterSources(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("fill 19 -62 19 27 -61 21 stone");
        server.runCommand("fill 20 -61 20 26 -61 20 air");
        server.runCommand("setblock 20 -61 20 water");
        context.waitTicks(80);
        int before = server.computeOnServer(s -> sources(s.overworld()));
        check(before == 1, "trench has exactly one water source before (" + before + ")");
        BlockPos flowing = new BlockPos(23, -61, 20), source = new BlockPos(20, -61, 20);
        server.runOnServer(s -> {
            check(!s.overworld().getFluidState(flowing).isSource() && !s.overworld().getFluidState(flowing).isEmpty(), "target cell holds flowing water");
            place(s, flowing);
            place(s, source);
            check(!s.overworld().getBlockState(flowing).getValue(BlockStateProperties.WATERLOGGED), "boombox in flowing water is not waterlogged");
            check(s.overworld().getBlockState(source).getValue(BlockStateProperties.WATERLOGGED), "boombox in a water source is waterlogged");
        });
        context.waitTicks(80);
        int placed = server.computeOnServer(s -> sources(s.overworld()));
        check(placed == 1, "no extra water source while boomboxes sit in water (" + placed + ")");
        server.runOnServer(s -> { s.overworld().destroyBlock(flowing, false); s.overworld().destroyBlock(source, false); });
        context.waitTicks(80);
        int after = server.computeOnServer(s -> sources(s.overworld()));
        check(after == 1, "breaking the boomboxes leaves exactly the original water source (" + after + ")");
    }

    private static void place(net.minecraft.server.MinecraftServer server, BlockPos target) {
        var player = server.getPlayerList().getPlayers().getFirst();
        var item = (BlockItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("holylois", "boombox"));
        BlockPos below = target.below();
        var hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        var result = item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item), hit));
        check(result.consumesAction() && server.overworld().getBlockState(target).is(item.getBlock()), "boombox placed at " + target.toShortString());
    }

    private static int sources(ServerLevel level) {
        int count = 0;
        for (int x = 19; x <= 27; x++) for (int z = 19; z <= 21; z++) for (int y = -62; y <= -60; y++)
            if (level.getFluidState(new BlockPos(x, y, z)).isSource()) count++;
        return count;
    }

    /**
     * Reported: a closed chest replays its closing animation after you look away and back. Opens and closes a chest
     * like a player, turns around, turns back and watches the lid tick by tick (plus screenshots for render-only cases).
     */
    private static void chestLidStaysShut(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        BlockPos base = context.computeOnClient(client -> client.player.blockPosition());
        BlockPos chest = base.offset(3, 0, 0);
        world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " chest[facing=west]");
        context.waitTicks(20);
        context.getInput().lookAt(chest);
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitForScreen(ContainerScreen.class);
        context.waitTicks(20);
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(60);
        float settled = context.computeOnClient(client -> lid(client, chest));
        check(settled == 0f, "lid closed after the chest was closed (" + settled + ")");
        context.takeScreenshot("06-chest-closed");
        context.getInput().lookAt(base.offset(-3, 0, 0));
        context.waitTicks(60);
        context.getInput().lookAt(chest);
        float most = 0;
        for (int tick = 0; tick < 30; tick++) {
            context.waitTick();
            most = Math.max(most, context.computeOnClient(client -> lid(client, chest)));
            if (tick == 2 || tick == 6) context.takeScreenshot("07-chest-after-look-back-" + tick);
        }
        check(most == 0f, "lid does not move again after looking away and back (max openness " + most + ")");
        // Players usually turn away right after closing, while the lid is still moving: check that on screen too.
        double quick = lookBackMovement(context, chest, base, 3, "quick");
        double later = lookBackMovement(context, chest, base, 80, "later");
        log("chest lid movement on screen after looking back: turned away right after closing " + quick + ", long after " + later);
        check(quick < 2.0 && later < 2.0, "lid does not visibly move after looking back (" + quick + ", " + later + ")");
    }

    /** Opens and closes the chest, turns away after delay ticks, turns back; returns screen change at the chest between look-back and settled. */
    private static double lookBackMovement(ClientGameTestContext context, BlockPos chest, BlockPos base, int delay, String name) throws Exception {
        context.getInput().lookAt(chest);
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitForScreen(ContainerScreen.class);
        context.waitTicks(20);
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(delay);
        context.getInput().lookAt(base.offset(-(chest.getX() - base.getX()), 0, -(chest.getZ() - base.getZ())));
        context.waitTicks(60);
        context.getInput().lookAt(chest);
        context.waitTicks(1);
        var early = javax.imageio.ImageIO.read(context.takeScreenshot("08-fa-chest-" + name + "-back").toFile());
        context.waitTicks(40);
        var settled = javax.imageio.ImageIO.read(context.takeScreenshot("09-fa-chest-" + name + "-settled").toFile());
        return centreDifference(early, settled);
    }

    /** Mean per-channel difference over the middle fifth of the screen (where the chest is when looked at). */
    static double centreDifference(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b) {
        int w = a.getWidth(), h = a.getHeight(), x0 = w * 2 / 5, y0 = h * 2 / 5, x1 = w * 3 / 5, y1 = h * 3 / 5;
        long total = 0, count = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            for (int shift = 0; shift <= 16; shift += 8) total += Math.abs((p >> shift & 255) - (q >> shift & 255));
            count += 3;
        }
        return Math.round(total * 100.0 / count) / 100.0;
    }

    private static float lid(net.minecraft.client.Minecraft client, BlockPos pos) {
        return client.level.getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest.getOpenNess(1f) : -1f;
    }

    /** Client half of the death marker cleanup: Xaero makes a death marker, the server says the loot is gone, it disappears. */
    private static void deathMarkerGoesWhenLootIsGone(ClientGameTestContext context, TestSingleplayerContext world) {
        // Die far from spawn: Xaero deletes a death marker you stand on (delete_reached_deathpoints), and respawn is at spawn.
        world.getServer().runCommand("tp @a 200 -60 200");
        world.getConnection().waitForChunksRender();
        world.getServer().runCommand("kill @a");
        context.waitForScreen(DeathScreen.class);
        context.waitTicks(40);
        context.takeScreenshot("04-death-screen");
        context.clickScreenButton("deathScreen.respawn");
        context.waitFor(client -> client.player != null && client.player.isAlive(), 600);
        world.getConnection().waitForChunksRender();
        context.waitTicks(120); // the client hands the packet to Xaero after its session settles
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
