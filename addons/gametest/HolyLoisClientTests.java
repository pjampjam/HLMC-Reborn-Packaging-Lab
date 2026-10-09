package holylois.gametest;

import net.minecraft.world.inventory.Slot;

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
            run(failed, "enter submits the login form", context, world, HolyLoisClientTests::enterSubmitsLogin);
            run(failed, "menus and daily card", context, world, HolyLoisClientTests::menusAndDailyCard);
            run(failed, "fish carry, shine and catch card", context, world, HolyLoisClientTests::fishCarryShineCard);
            run(failed, "fish fillet cuts", context, world, HolyLoisClientTests::filletCuts);
            run(failed, "arm animations", context, world, HolyLoisClientTests::armAnimations);
            run(failed, "boombox and water", context, world, HolyLoisClientTests::boomboxKeepsWaterSources);
            run(failed, "chest lid stays shut", context, world, HolyLoisClientTests::chestLidStaysShut);
            run(failed, "death marker cleanup", context, world, HolyLoisClientTests::deathMarkerGoesWhenLootIsGone);
            run(failed, "trophy fish look", context, world, HolyLoisClientTests::trophyFishLook);
            run(failed, "boombox model", context, world, HolyLoisClientTests::boomboxModel);
            run(failed, "boombox hand closeup", context, world, HolyLoisClientTests::boomboxHand);
            run(failed, "boombox held notes", context, world, HolyLoisClientTests::boomboxHeldNotes);
            run(failed, "lantern swing", context, world, HolyLoisClientTests::lanternSwing);
            run(failed, "holy lootbox", context, world, HolyLoisClientTests::holyLootbox);
            run(failed, "r over a fillet", context, world, HolyLoisClientTests::rOverFillet);
            run(failed, "offhand swap spam", context, world, HolyLoisClientTests::offhandSwapSpam);
            run(failed, "structure title", context, world, HolyLoisClientTests::structureTitle);
            run(failed, "panorama capture", context, world, HolyLoisClientTests::panoramaCapture);
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
        // HOLYLOIS_TEST_ONLY=word runs only the tests whose name contains it (quick loops while fixing one thing).
        String only = System.getenv("HOLYLOIS_TEST_ONLY");
        if (only != null && !only.isBlank() && !name.contains(only)) return;
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

    /** Test catches (/legends give): rarity, weight past the cod maximum for a Mythic, then how they look in the slots, hand and on the ground. */
    private static void trophyFishLook(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("clear @a");
        for (String rarity : new String[]{"mythic", "legendary", "epic", "rare"}) server.runCommand("execute as @p run legends give " + rarity);
        context.waitTicks(10);
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            var first = player.getInventory().getItem(0);
            var data = first.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            check(data != null, "the test fish has custom data");
            var fish = data.copyTag().getCompound("holylois_fish").orElseThrow();
            check(fish.getStringOr("rarity", "").equals("mythic"), "first test fish is Mythic (" + fish + ")");
            check(fish.getDoubleOr("kg", 0) >= 18 && fish.getDoubleOr("size", 0) >= 1.5, "a Mythic cod weighs past 1.5x the 12 kg maximum (" + fish + ")");
            check(first.get(net.minecraft.core.component.DataComponents.CONSUMABLE).onConsumeEffects().size() >= 1, "a Mythic fish has eating buffs");
            player.getInventory().setSelectedSlot(0);
        });
        context.waitTicks(20);
        context.takeScreenshot("10-fish-hotbar-and-hand");
        float scale = context.computeOnClient(client -> holylois.boombox.FishLook.scale(client.player.getMainHandItem(), net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        float own = context.computeOnClient(client -> holylois.boombox.FishLook.scale(client.player.getMainHandItem(), net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        check(scale > 2 && own > 1.2f && own < scale - 0.4f, "a Mythic fish is drawn much bigger in the hand, less in your own view (" + scale + ", " + own + ")");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitTicks(5);
        context.takeScreenshot("11-fish-inventory");
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(5);
        BlockPos spot = context.computeOnClient(client -> client.player.blockPosition().offset(-1, 0, 3));
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            var plain = new net.minecraft.world.entity.item.ItemEntity(s.overworld(), spot.getX() + 1.5, spot.getY() + 0.2, spot.getZ() + 0.5, new ItemStack(net.minecraft.world.item.Items.COD));
            plain.setDeltaMovement(Vec3.ZERO); plain.setPickUpDelay(32767); s.overworld().addFreshEntity(plain);
            for (int slot = 0; slot < 2; slot++) {
                var item = new net.minecraft.world.entity.item.ItemEntity(s.overworld(), spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5 + slot, player.getInventory().removeItemNoUpdate(slot));
                item.setDeltaMovement(Vec3.ZERO); item.setPickUpDelay(32767);
                s.overworld().addFreshEntity(item);
            }
        });
        context.getInput().lookAt(spot);
        context.waitTicks(120); // the rare catch card from /legends give fades after 5 s
        log("ground fish scale " + context.computeOnClient(client -> {
            var sb = new StringBuilder();
            for (var e : client.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.item.ItemEntity item)
                sb.append(item.position()).append(" ").append(holylois.boombox.FishLook.scale(item.getItem(), net.minecraft.world.item.ItemDisplayContext.GROUND)).append("; ");
            return sb.toString();
        }));
        context.takeScreenshot("12-fish-on-ground");
        server.runCommand("kill @e[type=item]");
    }

    /** The daily card (centred above the hotbar, star pop, item pop, fade) and every Holy Lois menu, for the look and spacing. */
    @SuppressWarnings("unchecked")
    private static void menusAndDailyCard(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        context.runOnClient(client -> {
            try {
                var field = Class.forName("holylois.auth.RewardHud").getDeclaredField("queue"); field.setAccessible(true);
                ((java.util.Deque<Object>) field.get(null)).addLast(new holylois.auth.RewardNotice("minecraft:bread", 6, 3, 100, 0));
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        });
        context.waitTicks(4);
        fakeParty(context);
        context.takeScreenshot("20-daily-card-fading-in");
        context.waitTicks(16);
        fakeParty(context);
        context.takeScreenshot("21-daily-card-star");
        context.waitTicks(40);
        fakeParty(context);
        context.takeScreenshot("22-daily-card");
        context.runOnClient(client -> {
            try {
                var field = Class.forName("holylois.auth.RewardHud").getDeclaredField("queue"); field.setAccessible(true);
                ((java.util.Deque<Object>) field.get(null)).addLast(new holylois.auth.RewardNotice("holylois:holy_lootbox", 1, 7, 0, 0));
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        });
        context.waitTicks(150);
        context.takeScreenshot("22-daily-card-day7");
        // Half way through the fade-out: the lootbox shrinks with the card instead of popping away.
        context.waitTicks(93);
        context.takeScreenshot("22-daily-card-fading-out-1");
        context.waitTicks(4);
        context.takeScreenshot("22-daily-card-fading-out-2");
        context.waitTicks(4);
        context.takeScreenshot("22-daily-card-fading-out-3");
        context.waitTicks(69);
        var homes = new holylois.boombox.HomeState(true, 3, 1, List.of(new holylois.boombox.HomeState.Home("Base", "minecraft:overworld")));
        context.runOnClient(client -> client.gui.setScreen(new holylois.boombox.HomesScreen(homes)));
        context.waitTicks(5);
        context.takeScreenshot("23-homes");
        context.runOnClient(client -> client.gui.setScreen(new holylois.boombox.PartyScreen()));
        context.waitTicks(5);
        context.takeScreenshot("24-party");
        context.runOnClient(client -> client.gui.setScreen(new holylois.boombox.RallyScreen()));
        context.waitTicks(5);
        context.takeScreenshot("25-rally");
        context.runOnClient(client -> client.gui.setScreen(new holylois.boombox.AccountScreen(new holylois.boombox.AccountNotice(2, java.util.UUID.randomUUID(), 6, false, true, ""))));
        context.waitTicks(5);
        context.takeScreenshot("26-account-password");
        context.runOnClient(client -> client.gui.setScreen(new holylois.auth.HolyLoisAuthScreen(new holylois.auth.AuthStatus(2, 6))));
        context.waitTicks(5);
        context.takeScreenshot("27-register");
        // Signed in: the form's last frame fades out over the world instead of cutting to it.
        context.runOnClient(client -> client.gui.setScreen(null));
        context.waitTicks(4);
        boolean fading = context.computeOnClient(client -> holylois.auth.PanoramaFade.fading() && client.gui.screen() == null);
        context.takeScreenshot("27-entry-fade");
        context.waitTicks(30);
        boolean done = context.computeOnClient(client -> !holylois.auth.PanoramaFade.fading());
        check(fading && done, "the login backdrop fades into the world (" + fading + ", " + done + ")");
        // The resource pack download on the loading bar, text under it, no toast.
        holylois.boombox.PackLoadingBar.downloadStart(java.util.OptionalLong.of(1000));
        holylois.boombox.PackLoadingBar.downloaded(450);
        context.waitTicks(2);
        context.takeScreenshot("28-pack-download");
        holylois.boombox.PackLoadingBar.finished(true);
        context.waitTicks(2);
        context.takeScreenshot("28-pack-applying");
        context.waitTicks(40);
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    /** A party of four (one with absorption, one in another dimension, one offline) and a rally, fresh for the next 3 s. */
    private static void fakeParty(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var me = client.player.getUUID();
            var members = List.of(
                new holylois.boombox.PartyState.Member(me, client.player.getGameProfile().name(), true, true, 20, 20, 0, 20, true),
                new holylois.boombox.PartyState.Member(java.util.UUID.randomUUID(), "Marek", true, true, 13, 20, 6, 18, false),
                new holylois.boombox.PartyState.Member(java.util.UUID.randomUUID(), "LongNameExplorer", true, false, 6, 24, 0, 9, false),
                new holylois.boombox.PartyState.Member(java.util.UUID.randomUUID(), "Sleepy", false, false, 0, 20, 0, 0, false));
            var rally = new holylois.boombox.PartyState.Rally(members.get(1).id(), "Marek", client.level.dimension().identifier().toString(), 40, -60, 25, 75);
            try {
                var hud = holylois.boombox.PartyHud.class;
                var state = hud.getDeclaredField("state"); state.setAccessible(true); state.set(null, new holylois.boombox.PartyState(java.util.UUID.randomUUID(), members, rally));
                var at = hud.getDeclaredField("receivedAt"); at.setAccessible(true); at.setLong(null, System.currentTimeMillis());
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        });
    }

    /** A Shiny Legendary (20-40 kg): catch card with sweep and sparkles, shimmering chat, carried in both arms (you and a mannequin). */
    private static void fishCarryShineCard(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runCommand("kill @e[type=mannequin]");
        server.runCommand("execute as @p run legends give shiny");
        context.waitTicks(8);
        context.takeScreenshot("30-catch-card-sweep");
        context.waitTicks(14);
        context.takeScreenshot("31-catch-card");
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            var fish = player.getInventory().getItem(0);
            check(holylois.boombox.FishData.twoHanded(fish), "a Legendary is carried in both arms (" + fish.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) + ")");
            player.getInventory().setSelectedSlot(0);
        });
        context.waitTicks(100); // the catch card fades after 5.5 s
        context.runOnClient(client -> { client.player.setXRot(0); client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
        context.waitTicks(15);
        context.takeScreenshot("32-fish-two-handed-front");
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(8);
        context.takeScreenshot("32-fish-walk");
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().pressKey(options -> options.keyJump);
        context.waitTicks(4);
        context.takeScreenshot("32-fish-jump");
        context.waitTicks(15);
        // Your own view: the fists sit beside the head, so nothing clips the camera; looking up shows the fish overhead.
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); client.player.setXRot(0); });
        context.waitTicks(5);
        context.takeScreenshot("34-fish-first-person");
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(5);
        context.takeScreenshot("34-fish-first-person-up");
        // Both hands: a torch in the off hand goes back to the inventory while the fish is held, and the fish cannot go there.
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.TORCH)));
        context.waitTicks(8);
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            check(player.getOffhandItem().isEmpty() && player.getInventory().countItem(net.minecraft.world.item.Items.TORCH) == 1, "the off hand is cleared while a heavy fish is held");
            var fish = player.getMainHandItem().copy();
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, fish);
        });
        context.waitTicks(8);
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            check(player.getOffhandItem().isEmpty(), "a heavy fish does not stay in the off hand");
            int slot = -1;
            for (int i = 0; i < 36; i++) if (holylois.boombox.FishData.twoHanded(player.getInventory().getItem(i))) slot = i;
            check(slot >= 0, "the heavy fish went back into the inventory");
            player.getInventory().setItem(0, player.getInventory().removeItemNoUpdate(slot));
            player.getInventory().setSelectedSlot(0);
        });
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); client.player.setXRot(0); });
        context.waitTicks(5);
        // Two still mannequins 3 blocks ahead: the left one faces the camera, the right one shows its side.
        var look = context.computeOnClient(client -> client.player.getLookAngle().multiply(1, 0, 1).normalize());
        var base = context.computeOnClient(client -> client.player.position());
        var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
        float facing = context.computeOnClient(client -> client.player.getYRot() + 180);
        var front = base.add(look.scale(3)).add(right.scale(-1));
        var side = base.add(look.scale(3)).add(right.scale(1));
        server.runCommand(String.format(java.util.Locale.ROOT, "summon mannequin %.2f %.2f %.2f {Tags:[\"hltest\"],Rotation:[%.1ff,0f]}", front.x, front.y, front.z, facing));
        server.runCommand(String.format(java.util.Locale.ROOT, "summon mannequin %.2f %.2f %.2f {Tags:[\"hltest\"],Rotation:[%.1ff,0f]}", side.x, side.y, side.z, facing - 90));
        server.runOnServer(s -> {
            var fish = s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0);
            for (var e : s.overworld().getAllEntities()) if (e.entityTags().contains("hltest") && e instanceof net.minecraft.world.entity.LivingEntity living)
                living.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, fish.copy());
        });
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); client.player.setXRot(-5); });
        server.runCommand("clear @a");
        context.waitTicks(20);
        context.takeScreenshot("33-fish-two-handed-mannequins");
        server.runCommand("kill @e[type=mannequin]");
    }

    /** A Legendary on the cutting board: several knife cuts, a damage bar after each, slices by weight, equal slices stack. */
    private static void filletCuts(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runCommand("kill @e[type=item]");
        server.runCommand("execute as @p run legends give legendary");
        context.waitTicks(5);
        server.runOnServer(s -> {
            try {
                var player = s.getPlayerList().getPlayers().getFirst();
                var level = s.overworld();
                var fish = player.getInventory().getItem(0).copy();
                int slices = holylois.boombox.FishData.slices(fish), cuts = holylois.boombox.FishData.cuts(fish);
                check(slices >= 15 && cuts >= 4, "a 20-40 kg Legendary gives 15+ slices over 4+ cuts (" + slices + ", " + cuts + ")");
                BlockPos pos = player.blockPosition().offset(3, 0, 3);
                level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse("farmersdelight:cutting_board")).defaultBlockState());
                var board = level.getBlockEntity(pos);
                board.getClass().getMethod("addItem", ItemStack.class).invoke(board, fish);
                var knife = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("farmersdelight:iron_knife")));
                var cut = board.getClass().getMethod("processStoredItemUsingTool", ItemStack.class, net.minecraft.world.entity.player.Player.class);
                var stored = board.getClass().getMethod("getStoredItem");
                int used = 0;
                while (used < cuts + 3 && !((ItemStack) stored.invoke(board)).isEmpty()) {
                    cut.invoke(board, knife, player);
                    used++;
                    var left = (ItemStack) stored.invoke(board);
                    if (used == 1) check(!left.isEmpty() && left.getDamageValue() == 1 && left.isDamaged(), "after the first cut the fish is still there, one cut damaged (" + left.getDamageValue() + ")");
                }
                check(used == cuts, "the fish is used up on the last cut (" + used + " of " + cuts + ")");
                int dropped = 0; var kinds = new java.util.HashSet<String>();
                for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3)))
                    if (holylois.boombox.FishData.fillet(item.getItem())) { dropped += item.getItem().getCount(); kinds.add(item.getItem().getComponents().toString()); }
                check(dropped == slices, "slices dropped match the weight (" + dropped + " of " + slices + ")");
                check(kinds.size() == 1, "all slices are the same, so they stack (" + kinds.size() + " kinds)");
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        });
        context.waitTicks(5);
        server.runCommand("kill @e[type=item]");
    }

    /**
     * Not Enough Animations with the FirstPerson body and Fresh Animations (KeptArms): a map held up in view, eating at the
     * mouth and a lantern, in first person (body arms, no vanilla hands) and from the front.
     */
    private static void armAnimations(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        var server = world.getServer();
        server.runCommand("gamemode survival @a");
        String[][] cases = {{"filled_map", "map"}, {"cooked_beef", "eat"}, {"lantern", "lantern"}};
        for (var c : cases) {
            server.runCommand("clear @a");
            server.runCommand("give @a " + c[0] + " 8");
            context.waitTicks(c[1].equals("eat") ? 40 : 10);
            context.runOnClient(client -> { client.player.getInventory().setSelectedSlot(0); client.player.setXRot(25); client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
            boolean eat = c[1].equals("eat");
            // Peaceful refills food, so the player is made hungry right before each bite.
            if (eat) { server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.getFoodData().setFoodLevel(6))); context.getInput().holdKey(options -> options.keyUse); }
            context.waitTicks(eat ? 12 : 6);
            context.takeScreenshot("40-" + c[1] + "-first-person");
            if (eat) { context.getInput().releaseKey(options -> options.keyUse); context.waitTicks(4); }
            context.runOnClient(client -> { client.player.setXRot(0); client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
            if (eat) { server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.getFoodData().setFoodLevel(6))); context.getInput().holdKey(options -> options.keyUse); }
            context.waitTicks(12);
            context.takeScreenshot("41-" + c[1] + "-front");
            if (eat) context.getInput().releaseKey(options -> options.keyUse);
        }
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        // Our smoothing went into Not Enough Animations' lantern and hold-up code (the mixins are optional, so check here).
        for (var owner : new String[] {"dev.tr7zw.notenoughanimations.logic.HeldItemHandler", "dev.tr7zw.notenoughanimations.animations.hands.LookAtItemAnimation"}) {
            boolean merged = java.util.Arrays.stream(Class.forName(owner).getDeclaredMethods()).anyMatch(m -> m.getName().contains("holyLoisSmooth"));
            check(merged, "smoothing merged into " + owner);
        }
        server.runCommand("clear @a");
        server.runCommand("effect clear @a");
        server.runCommand("effect give @a saturation 3 20 true");
        server.runCommand("effect give @a instant_health 1 10 true");
        context.waitTicks(40);
    }

    /** Typing a password and pressing Enter sends it, like clicking Log in (the field is cleared once sent). */
    private static void enterSubmitsLogin(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        // Like a player: click into the box first (mouse focus), type, Enter. Login, then register (Enter in the confirm box).
        for (int mode : new int[] {1, 2}) {
            var screen = context.computeOnClient(client -> { var s = new holylois.auth.HolyLoisAuthScreen(new holylois.auth.AuthStatus(mode, 6)); client.gui.setScreen(s); return s; });
            context.waitTicks(5);
            clickField(context, screen, "password");
            context.getInput().typeChars("secret123");
            if (mode == 2) { clickField(context, screen, "confirm"); context.getInput().typeChars("secret123"); }
            context.waitTicks(2);
            String typed = context.computeOnClient(client -> passwordOf(client.gui.screen()));
            String focus = context.computeOnClient(client -> String.valueOf(client.gui.screen().getFocused()));
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
            context.waitTicks(3);
            boolean same = context.computeOnClient(client -> client.gui.screen() == screen);
            String notice = String.valueOf(field(screen, "notice"));
            log("auth form " + mode + ": typed=" + typed.length() + " focus=" + focus + " same=" + same + " notice=" + notice);
            context.runOnClient(client -> client.gui.setScreen(null));
            check(typed.length() == 9, "the password box takes typed text (" + typed.length() + ")");
            check(same && notice.startsWith("Checking"), "Enter in a focused box sent form " + mode + " (notice: " + notice + ")");
        }
        // Esc opens the leave prompt (26.3 scancode 41, not GLFW 256).
        context.runOnClient(client -> client.gui.setScreen(new holylois.auth.HolyLoisAuthScreen(new holylois.auth.AuthStatus(1, 6))));
        context.waitTicks(3);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        boolean quitting = context.computeOnClient(client -> client.gui.screen() instanceof holylois.auth.HolyLoisAuthScreen s && (boolean) field(s, "quitting"));
        context.runOnClient(client -> client.gui.setScreen(null));
        check(quitting, "Esc on the login form opens the leave prompt");
    }

    private static void clickField(ClientGameTestContext context, Object screen, String name) {
        var box = (net.minecraft.client.gui.components.EditBox) field(screen, name);
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos((box.getX() + 20) * scale, (box.getY() + 4) * scale);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
    }

    private static Object field(Object owner, String name) {
        // A Class reads a static field.
        for (Class<?> type = owner instanceof Class<?> c ? c : owner.getClass(); type != null; type = type.getSuperclass()) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner instanceof Class<?> ? null : owner); }
            catch (NoSuchFieldException next) { continue; }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalStateException("no field " + name + " on " + owner);
    }

    private static String passwordOf(Object screen) {
        try {
            var field = screen.getClass().getDeclaredField("password"); field.setAccessible(true);
            var box = (net.minecraft.client.gui.components.EditBox) field.get(screen);
            return box == null ? "" : box.getValue();
        } catch (ReflectiveOperationException error) { return "no field on " + screen; }
    }

    /** The 1.9.0 boombox model from the front, placed like a player would, then turned to face the camera. */
    private static void boomboxModel(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos target = context.computeOnClient(client -> client.player.blockPosition().offset(-2, 0, 0));
        world.getServer().runOnServer(s -> {
            place(s, target);
            var level = s.overworld();
            level.setBlockAndUpdate(target, level.getBlockState(target).setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST));
        });
        context.getInput().lookAt(target);
        context.waitTicks(20);
        context.takeScreenshot("13-boombox-model");
        // Held: the flat icon in the hotbar, the 3D model carried by its handle in both views.
        world.getServer().runCommand("clear @a");
        world.getServer().runCommand("give @a holylois:boombox");
        // With FirstPerson your own body carries it: look down at the hand.
        context.runOnClient(client -> client.player.setXRot(65));
        context.waitTicks(20);
        context.takeScreenshot("14-boombox-held-first-person");
        // Side view on an armor stand three blocks ahead: the speakers should face out, away from the leg.
        var ahead = context.computeOnClient(client -> client.player.position().add(client.player.getLookAngle().multiply(1, 0, 1).normalize().scale(3)));
        float facing = context.computeOnClient(client -> client.player.getYRot() + 90);
        world.getServer().runCommand(String.format(java.util.Locale.ROOT,
            "summon armor_stand %.2f %.2f %.2f {ShowArms:1b,NoBasePlate:1b,Rotation:[%.1ff,0f],equipment:{mainhand:{id:\"holylois:boombox\",count:1}}}",
            ahead.x, ahead.y, ahead.z, facing));
        context.runOnClient(client -> client.player.setXRot(10));
        context.waitTicks(20);
        context.takeScreenshot("14-boombox-held-side");
        world.getServer().runCommand("kill @e[type=armor_stand]");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(20);
        context.takeScreenshot("15-boombox-held-third-person");
        // Walk, stop and turn: the arm stays at the hip and the box swings from its handle, then settles.
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        context.takeScreenshot("15-boombox-walk");
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(3);
        float afterStop = context.computeOnClient(client -> holylois.boombox.HeldSwing.side(client.player.getId()));
        float tiltStop = context.computeOnClient(client -> holylois.boombox.HeldSwing.tilt(client.player.getId()));
        context.takeScreenshot("15-boombox-stop");
        context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 70));
        context.waitTicks(4);
        float afterTurn = context.computeOnClient(client -> holylois.boombox.HeldSwing.side(client.player.getId()));
        context.takeScreenshot("15-boombox-turn");
        log("boombox swing: stop side " + afterStop + " tilt " + tiltStop + ", turn side " + afterTurn);
        check(Math.abs(tiltStop) > 2 && Math.abs(afterTurn) > 2, "the carried boombox swings when you stop and turn (" + tiltStop + ", " + afterTurn + ")");
        context.runOnClient(client -> client.player.setYRot(client.player.getYRot() - 70));
        context.waitTicks(40);
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        world.getServer().runCommand("clear @a");
        // Mannequins 3 blocks ahead: one faces the camera, one shows its side (hand on the handle, speakers out).
        var look = context.computeOnClient(client -> client.player.getLookAngle().multiply(1, 0, 1).normalize());
        var base = context.computeOnClient(client -> client.player.position());
        var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
        float faceCamera = context.computeOnClient(client -> client.player.getYRot() + 180);
        var front = base.add(look.scale(3)).add(right.scale(-1));
        var side = base.add(look.scale(3)).add(right.scale(1));
        world.getServer().runCommand(String.format(java.util.Locale.ROOT, "summon mannequin %.2f %.2f %.2f {Tags:[\"hlbox\"],Rotation:[%.1ff,0f],equipment:{mainhand:{id:\"holylois:boombox\",count:1}}}", front.x, front.y, front.z, faceCamera));
        world.getServer().runCommand(String.format(java.util.Locale.ROOT, "summon mannequin %.2f %.2f %.2f {Tags:[\"hlbox\"],Rotation:[%.1ff,0f],equipment:{mainhand:{id:\"holylois:boombox\",count:1}}}", side.x, side.y, side.z, faceCamera + 90));
        context.runOnClient(client -> client.player.setXRot(15));
        context.waitTicks(20);
        context.takeScreenshot("16-boombox-held-mannequins");
        world.getServer().runCommand("kill @e[tag=hlbox]");
    }

    /**
     * R over a fillet in the inventory shows its recipe and never sorts: nothing moves, nothing sticks to the cursor, and the
     * client and the server agree on every slot afterwards (owner round 4: a sort plus a ghost slice on the cursor).
     */
    private static void rOverFillet(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            var fish = new ItemStack(net.minecraft.world.item.Items.COD);
            var tag = new net.minecraft.nbt.CompoundTag(); var inner = new net.minecraft.nbt.CompoundTag();
            inner.putString("rarity", "legendary"); inner.putString("species", "minecraft:cod"); inner.putDouble("kg", 25);
            tag.put("holylois_fish", inner);
            fish.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            var slice = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("farmersdelight:cod_slice")), 5);
            holylois.boombox.Legends.fillet(fish, slice);
            player.getInventory().setItem(20, slice);
            player.getInventory().setItem(9, new ItemStack(net.minecraft.world.item.Items.DIRT, 3));
            player.getInventory().setItem(30, new ItemStack(net.minecraft.world.item.Items.STONE, 7));
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        // Hover the slice (inventory slot 20 is menu slot 20 in the player's inventory screen).
        var pos = context.computeOnClient(client -> {
            var screen = (InventoryScreen) client.gui.screen();
            var slot = screen.getMenu().getSlot(20);
            int left = (int) field(screen, "leftPos"), top = (int) field(screen, "topPos");
            double scale = client.getWindow().getGuiScale();
            return new double[] {(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTicks(3);
        String hovered = context.computeOnClient(client -> String.valueOf(((holylois.boombox.mixins.ContainerHoverAccessor) client.gui.screen()).holyLoisHoveredSlot()));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_R);
        context.waitTicks(10);
        context.takeScreenshot("35-r-over-fillet");
        String screenAfter = context.computeOnClient(client -> String.valueOf(client.gui.screen()));
        String clientState = context.computeOnClient(client -> inventoryState(client.player.containerMenu.getCarried(), client.player.getInventory()));
        String serverState = server.computeOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst(); return inventoryState(p.containerMenu.getCarried(), p.getInventory()); });
        log("R over fillet: hovered=" + hovered + " screen=" + screenAfter + "\n client " + clientState + "\n server " + serverState);
        // R over an empty slot sorts (Inventory Profiles Next), and still nothing ends up on the cursor.
        context.runOnClient(client -> { if (!(client.gui.screen() instanceof InventoryScreen)) client.gui.setScreen(new InventoryScreen(client.player)); });
        context.waitTicks(3);
        var empty = context.computeOnClient(client -> {
            var screen = (InventoryScreen) client.gui.screen();
            var slot = screen.getMenu().getSlot(25);
            int left = (int) field(screen, "leftPos"), top = (int) field(screen, "topPos");
            double scale = client.getWindow().getGuiScale();
            return new double[] {(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
        });
        context.getInput().setCursorPos(empty[0], empty[1]);
        context.waitTicks(3);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_R);
        context.waitTicks(20);
        String clientSorted = context.computeOnClient(client -> inventoryState(client.player.containerMenu.getCarried(), client.player.getInventory()));
        String serverSorted = server.computeOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst(); return inventoryState(p.containerMenu.getCarried(), p.getInventory()); });
        log("R over empty slot:\n client " + clientSorted + "\n server " + serverSorted);
        check(clientSorted.equals(serverSorted) && serverSorted.startsWith("cursor=empty"), "sorting leaves nothing on the cursor and client and server agree");
        context.runOnClient(client -> client.gui.setScreen(null));
        context.waitTicks(5);
        check(clientState.equals(serverState), "client and server agree after R over a fillet");
        check(serverState.contains("20=5 farmersdelight:cod_slice") && serverState.startsWith("cursor=empty"), "R over a fillet moved nothing (" + serverState + ")");
    }

    /**
     * Owner round 5 dupe: torch in the off hand, a heavy fish in the inventory, spam F over the fish. The fish must never reach
     * the off hand, there is always exactly one fish, and the client and the server agree after every press.
     */
    private static void offhandSwapSpam(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        swapSpam(context, world, false);
        swapSpam(context, world, true);
        world.getServer().runCommand("gamemode survival @a");
    }

    private static void swapSpam(ClientGameTestContext context, TestSingleplayerContext world, boolean creative) throws Exception {
        var server = world.getServer();
        server.runCommand("gamemode " + (creative ? "creative" : "survival") + " @a");
        server.runCommand("clear @a");
        server.runCommand("execute as @p run legends give legendary");
        context.waitTicks(5);
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            int from = -1;
            for (int i = 0; i < 36; i++) if (holylois.boombox.FishData.twoHanded(player.getInventory().getItem(i))) from = i;
            player.getInventory().setItem(20, player.getInventory().removeItemNoUpdate(from));
            player.getInventory().setSelectedSlot(4);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.TORCH, 3));
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(5);
        if (creative) context.runOnClient(client -> {
            var screen = client.gui.screen();
            for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB)
                if (tab.getType() == net.minecraft.world.item.CreativeModeTab.Type.INVENTORY) {
                    var select = screen.getClass().getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
                    select.setAccessible(true); select.invoke(screen, tab);
                }
        });
        context.waitTicks(3);
        var pos = context.computeOnClient(client -> {
            var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen();
            Slot target = null;
            for (var slot : screen.getMenu().slots)
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.getContainerSlot() == 20) target = slot;
            int left = (int) field(screen, "leftPos"), top = (int) field(screen, "topPos");
            double scale = client.getWindow().getGuiScale();
            return new double[] {(left + target.x + 8) * scale, (top + target.y + 8) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTicks(2);
        for (int press = 0; press < 20; press++) {
            context.getInput().pressKey(options -> options.keySwapOffhand);
            context.waitTicks(press % 3 == 0 ? 1 : 3);
        }
        context.waitTicks(10);
        String clientState = context.computeOnClient(client -> handsState(client.player));
        String serverState = server.computeOnServer(s -> handsState(s.getPlayerList().getPlayers().getFirst()));
        String mode = creative ? "creative" : "survival";
        log("F spam over a heavy fish (" + mode + "): client " + clientState + " | server " + serverState);
        context.runOnClient(client -> client.gui.setScreen(null));
        context.waitTicks(3);
        check(serverState.contains("fish=1 ") && serverState.contains("offhandFish=false"), mode + ": exactly one fish, never in the off hand (" + serverState + ")");
        check(serverState.contains("torches=3"), mode + ": the torch stack is untouched (" + serverState + ")");
        check(clientState.equals(serverState), mode + ": client and server agree after F spam");
    }

    private static String handsState(net.minecraft.world.entity.player.Player player) {
        int fish = 0, torches = 0;
        var all = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) all.add(player.getInventory().getItem(i));
        all.add(player.containerMenu.getCarried());
        for (var stack : all) {
            if (holylois.boombox.FishData.twoHanded(stack)) fish += stack.getCount();
            if (stack.is(net.minecraft.world.item.Items.TORCH)) torches += stack.getCount();
        }
        return "fish=" + fish + " torches=" + torches + " offhandFish=" + holylois.boombox.FishData.twoHanded(player.getOffhandItem());
    }

    private static String inventoryState(ItemStack carried, net.minecraft.world.entity.player.Inventory inventory) {
        var out = new StringBuilder("cursor=" + (carried.isEmpty() ? "empty" : carried.getCount() + " " + BuiltInRegistries.ITEM.getKey(carried.getItem())));
        for (int i = 0; i < 36; i++) {
            var stack = inventory.getItem(i);
            if (!stack.isEmpty()) out.append(' ').append(i).append('=').append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return out.toString();
    }

    /** Close-ups of a mannequin carrying the boombox: from the front and from its side (handle in the fist, box at the hip). */
    private static void boomboxHand(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runCommand("kill @e[type=mannequin]");
        // Zoomed in on the fist (narrow field of view, no HUD) so a pixel of offset shows.
        int fov = context.computeOnClient(client -> client.options.fov().get());
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); client.player.setXRot(0);
            client.options.fov().set(40); if (!client.gui.hud.isHidden()) client.gui.hud.toggle(); });
        var look = context.computeOnClient(client -> client.player.getLookAngle().multiply(1, 0, 1).normalize());
        var spot = context.computeOnClient(client -> client.player.position()).add(look.scale(1.3)).add(0, 0.95, 0);
        float faceCamera = context.computeOnClient(client -> client.player.getYRot() + 180);
        String[] names = {"front", "side", "back", "inside"};
        float[] turns = {0, 90, 180, 270};
        for (int i = 0; i < 4; i++) {
            server.runCommand(String.format(java.util.Locale.ROOT, "summon mannequin %.2f %.2f %.2f {NoGravity:1b,Tags:[\"hlhand\"],Rotation:[%.1ff,0f],equipment:{mainhand:{id:\"holylois:boombox\",count:1}}}", spot.x, spot.y, spot.z, faceCamera + turns[i]));
            context.waitTicks(25);
            context.takeScreenshot("17-boombox-hand-" + names[i]);
            if (i == 3) {
                // Music reaching a held boombox (voice chat entity sound): the cones push out like on the placed one.
                java.util.UUID holder = context.computeOnClient(client -> { for (var e : client.level.entitiesForRendering())
                    if (e.getClass().getSimpleName().contains("Mannequin")) return e.getUUID(); return null; });
                for (int t = 0; t <= 64; t++) { int frame = t; context.runOnClient(client -> holylois.boombox.BoomboxPulse.heardHeld(holder, music(frame))); context.waitTicks(1); }
                context.takeScreenshot("17-boombox-hand-playing");
                float pushed = context.computeOnClient(client -> ((java.util.Map<?, Float>) field(holylois.boombox.BoomboxPulse.class, "heldShown")).getOrDefault(holder, 0f));
                log("held boombox cones pushed out by " + pushed);
                check(pushed > 0.1f, "the held boombox cones move with the music (" + pushed + ")");
            }
            server.runCommand("tp @e[tag=hlhand] ~ -300 ~");
            server.runCommand("kill @e[tag=hlhand]");
        }
        context.runOnClient(client -> { client.options.fov().set(fov); if (client.gui.hud.isHidden()) client.gui.hud.toggle(); });
    }

    /** A lantern in hand swings on the boombox pendulum (HeldSwing through Not Enough Animations' 3D lantern). */
    private static void lanternSwing(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a weapon.mainhand with lantern");
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); client.player.setXRot(10); });
        context.waitTicks(20);
        context.takeScreenshot("42-lantern-still");
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        context.takeScreenshot("42-lantern-walk");
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(3);
        float tilt = context.computeOnClient(client -> holylois.boombox.HeldSwing.tilt(client.player.getId()));
        context.takeScreenshot("42-lantern-stop");
        context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 70));
        context.waitTicks(4);
        float side = context.computeOnClient(client -> holylois.boombox.HeldSwing.side(client.player.getId()));
        context.takeScreenshot("42-lantern-turn");
        log("lantern swing: stop tilt " + tilt + ", turn side " + side);
        check(Math.abs(tilt) > 2 && Math.abs(side) > 2, "a held lantern swings when you stop and turn (" + tilt + ", " + side + ")");
        boolean merged = java.util.Arrays.stream(Class.forName("dev.tr7zw.notenoughanimations.logic.HeldItemHandler").getDeclaredMethods())
            .anyMatch(m -> m.getName().contains("holyLoisSmoothSwing"));
        check(merged, "the lantern swing is ours inside Not Enough Animations");
        context.runOnClient(client -> client.player.setYRot(client.player.getYRot() - 70));
        context.waitTicks(40);
        context.takeScreenshot("42-lantern-settled");
        float rest = context.computeOnClient(client -> Math.abs(holylois.boombox.HeldSwing.side(client.player.getId())) + Math.abs(holylois.boombox.HeldSwing.tilt(client.player.getId())));
        check(rest < 3, "the lantern settles when you stand still (" + rest + ")");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        server.runCommand("clear @a");
    }

    /** The Holy Lootbox: the website's gold block with the engraved logo, in the hotbar, in hand (F5) and on the ground. */
    private static void holyLootbox(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a weapon.mainhand with holylois:holy_lootbox");
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); client.player.setXRot(15); });
        context.waitTicks(20);
        context.takeScreenshot("43-lootbox-held");
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); client.player.setXRot(30); });
        context.waitTicks(10);
        context.takeScreenshot("43-lootbox-first-person");
        var ahead = context.computeOnClient(client -> client.player.position().add(client.player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)));
        server.runCommand(String.format(java.util.Locale.ROOT, "summon item %.2f %.2f %.2f {Item:{id:\"holylois:holy_lootbox\",count:1},PickupDelay:32767}", ahead.x, ahead.y, ahead.z));
        context.runOnClient(client -> client.player.setXRot(50));
        context.waitTicks(30);
        context.takeScreenshot("43-lootbox-ground");
        server.runCommand("kill @e[type=item]");
        server.runCommand("clear @a");
        context.runOnClient(client -> client.player.setXRot(0));
    }

    /** 20 ms of a fake track at 48 kHz: a loud low kick every fourth frame over a quiet bed. */
    private static short[] music(int frame) {
        short[] audio = new short[960];
        for (int i = 0; i < audio.length; i++)
            audio[i] = (short) ((frame % 4 == 0 ? 20000 : 600) * Math.sin(2 * Math.PI * 55 * i / 48000.0));
        return audio;
    }

    /** Held boombox playing, seen from the front (F5): notes pop at the carrying hand on the beat. */
    private static void boomboxHeldNotes(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a weapon.mainhand with holylois:boombox");
        context.runOnClient(client -> { client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); client.player.setXRot(10); });
        context.waitTicks(10);
        java.util.UUID me = context.computeOnClient(client -> client.player.getUUID());
        for (int t = 0; t <= 80; t++) {
            int frame = t;
            context.runOnClient(client -> holylois.boombox.BoomboxPulse.heardHeld(me, music(frame)));
            context.waitTicks(1);
        }
        context.takeScreenshot("17-boombox-held-notes");
        int notes = context.computeOnClient(client -> (int) field(holylois.boombox.BoomboxPulse.class, "beats"));
        log("held boombox notes: " + notes);
        check(notes > 3, "a held boombox pops notes on the beat (" + notes + ")");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        server.runCommand("clear @a");
    }

    /** The client half of structure titles: the server says "you are in a pillager outpost", the title shows below Jade. */
    private static void structureTitle(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            check(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, holylois.boombox.StructureZone.TYPE), "client accepts structure zones");
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new holylois.boombox.StructureZone("minecraft:pillager_outpost", 77));
        });
        context.waitTicks(25);
        context.takeScreenshot("14-structure-title");
    }

    /** /capture panorama 1: six square faces in screenshots/holylois-panorama-*. */
    private static void panoramaCapture(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        int fovBefore = context.computeOnClient(client -> client.options.fov().get());
        context.runOnClient(client -> client.player.connection.sendCommand("capture panorama 1"));
        context.waitTicks(6 * 20 + 40);
        var root = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("screenshots"));
        java.nio.file.Path folder;
        try (var list = java.nio.file.Files.list(root)) {
            folder = list.filter(path -> path.getFileName().toString().startsWith("holylois-panorama-")).max(java.util.Comparator.naturalOrder()).orElse(null);
        }
        check(folder != null, "panorama folder made");
        for (int face = 0; face < 6; face++) {
            var image = javax.imageio.ImageIO.read(folder.resolve("panorama_" + face + ".png").toFile());
            check(image != null && image.getWidth() == image.getHeight() && image.getWidth() > 100, "panorama face " + face + " is a square image");
        }
        boolean hudBack = context.computeOnClient(client -> !client.gui.hud.isHidden() && client.options.fov().get() == fovBefore);
        check(hudBack, "HUD and field of view restored after the capture");
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
