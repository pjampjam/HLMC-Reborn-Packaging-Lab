package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Server moments: time and weather stand still while nobody is online, every 100th in-game day is celebrated,
 * and Latvian holidays bring a greeting, fireworks and sometimes a gift. New Year gets a countdown at midnight Riga time.
 */
public final class ServerEvents {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    /** Rockets with this tag explode harmlessly (CelebrationFireworkMixin). */
    public static final String CELEBRATION_TAG = "holylois_celebration";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    record Holiday(String key, String greeting, int[] colors, boolean lootbox, String gift, int giftCount, String giftLabel) {}

    /** Holiday for a Riga date, or null. Midsummer covers both Līgo evening and Jāņi day. */
    static Holiday holiday(LocalDate date) {
        String md = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return switch (md) {
            case "01-01" -> new Holiday("newyear", "Laimīgu Jauno gadu! Happy New Year from Holy Lois!", new int[] {0xF4C542, 0xFFFFFF, 0x6EC1FF}, true, null, 0, null);
            case "06-23", "06-24" -> new Holiday("ligo", "Līgo, līgo! Happy Midsummer! Here is some Jāņu siers.", new int[] {0x4CAF50, 0xF4C542, 0xFFFFFF}, false, "minecraft:golden_carrot", 8, "Jāņu siers (golden carrots, close enough)");
            case "10-31" -> new Holiday("halloween", "Happy Halloween! Something spooky for you.", new int[] {0xFF8C1A, 0x6A2C91, 0x111111}, false, "minecraft:pumpkin_pie", 6, "pumpkin pies");
            case "11-18" -> new Holiday("independence", "Daudz laimes, Latvija! Happy Latvian Independence Day!", new int[] {0x9E3039, 0xFFFFFF, 0x9E3039}, false, "minecraft:firework_rocket", 16, "firework rockets");
            case "12-24", "12-25", "12-26" -> new Holiday("christmas", "Priecīgus Ziemassvētkus! Merry Christmas from Holy Lois!", new int[] {0xC2362F, 0x34D27B, 0xFFFFFF}, true, null, 0, null);
            default -> null;
        };
    }

    public static final class State { public Map<String, Set<String>> gifted = new HashMap<>(); public long lastDay = -1; }
    private State state = new State();
    private Path file;
    private boolean countdownSent, newYearFired, blueMapRendering;

    void load(Path worldRoot) {
        file = worldRoot.resolve("holylois/events.json");
        try {
            if (Files.exists(file)) {
                var loaded = JSON.fromJson(Files.readString(file), State.class);
                if (loaded != null && loaded.gifted != null) state = loaded;
            }
        } catch (Exception error) { LOG.warn("Cannot read events state", error); }
    }

    static long day(MinecraftServer server) { return server.overworld().getOverworldClockTime() / 24000L; }

    /** Every second. */
    void tick(MinecraftServer server) {
        if (file == null) return;
        var rules = server.overworld().getGameRules();
        boolean empty = server.getPlayerList().getPlayerCount() == 0;
        // Nobody misses a day while away: time and weather pause when the server is empty.
        if (empty == rules.get(GameRules.ADVANCE_TIME)) {
            rules.set(GameRules.ADVANCE_TIME, !empty, server);
            rules.set(GameRules.ADVANCE_WEATHER, !empty, server);
            LOG.info(empty ? "Server empty: time and weather paused" : "Players online: time and weather running");
        }
        // BlueMap renders only while nobody plays, like the terrain job. Re-applied every two minutes because
        // BlueMap finishes loading after the server starts and ignores commands sent before that.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bluemap") && (empty != blueMapRendering || server.getTickCount() % 2400 == 0)) {
            try {
                server.getCommands().getDispatcher().execute(empty ? "bluemap start" : "bluemap stop", server.createCommandSourceStack().withSuppressedOutput());
                blueMapRendering = empty;
            } catch (Exception error) { /* BlueMap is still loading; the next attempt follows shortly */ }
        }
        long day = day(server);
        if (state.lastDay >= 0 && day > state.lastDay && !empty) {
            if (day % 100 == 0) {
                broadcast(server, "Day " + day + " on Holy Lois! Thanks for playing together.");
                for (var player : server.getPlayerList().getPlayers()) fireworks(player, new int[] {0xF4C542, 0xFFAD42, 0xFFFFFF}, 3);
            } else if ((day + 1) % 100 == 0) broadcast(server, "Tomorrow is day " + (day + 1) + ". Be online at sunrise for fireworks!");
        }
        if (day != state.lastDay) { state.lastDay = day; save(); }
        var now = LocalDateTime.now(DailyRewards.RIGA);
        if (now.getMonthValue() == 12 && now.getDayOfMonth() == 31 && now.getHour() == 23 && now.getMinute() == 59 && now.getSecond() >= 50 && !countdownSent) {
            countdownSent = true;
            broadcast(server, "10 seconds to the New Year in Riga!");
        }
        if (now.getMonthValue() == 1 && now.getDayOfMonth() == 1 && now.getHour() == 0 && now.getMinute() == 0 && !newYearFired) {
            newYearFired = true;
            broadcast(server, "Laimīgu Jauno gadu! Happy New Year " + now.getYear() + "!");
            for (var player : server.getPlayerList().getPlayers()) fireworks(player, new int[] {0xF4C542, 0xFFFFFF, 0x6EC1FF, 0xC2362F}, 5);
        }
    }

    /** First login of a holiday: greeting, fireworks and a once-per-year gift. */
    void welcome(MinecraftServer server, ServerPlayer player) {
        var date = DailyRewards.today();
        var holiday = holiday(date);
        if (holiday == null || file == null) return;
        String key = holiday.key() + "-" + date.getYear();
        if (!state.gifted.computeIfAbsent(key, k -> new HashSet<>()).add(player.getUUID().toString())) return;
        player.sendSystemMessage(prefix().append(Component.literal(holiday.greeting()).withStyle(ChatFormatting.YELLOW)));
        fireworks(player, holiday.colors(), 2);
        if (holiday.lootbox()) {
            DailyRewards.give(player, DailyRewards.lootbox(2, player.getGameProfile().name()));
            player.sendSystemMessage(Component.literal("✦ A holiday Holy Lootbox is in your inventory.").withStyle(ChatFormatting.GOLD));
        } else if (holiday.gift() != null) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(holiday.gift()));
            DailyRewards.give(player, new ItemStack(item, holiday.giftCount()));
            player.sendSystemMessage(Component.literal("✦ Holiday gift: " + holiday.giftCount() + " " + holiday.giftLabel() + ".").withStyle(ChatFormatting.GOLD));
        }
        save();
    }

    static net.minecraft.network.chat.MutableComponent prefix() {
        return Component.literal("[SERVER] ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    static void broadcast(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(prefix().append(Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(false))), false);
    }

    static void fireworks(ServerPlayer player, int[] colors, int count) {
        var level = player.level();
        for (int i = 0; i < count; i++) {
            var rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + i % 2, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.values()[i % FireworkExplosion.Shape.values().length], IntList.of(colors), IntList.of(0xFFFFFF), true, i % 2 == 0))));
            double angle = Math.PI * 2 * i / count;
            launch(level, player.getX() + Math.cos(angle) * 3, player.getY() + 1, player.getZ() + Math.sin(angle) * 3, rocket);
        }
    }

    /** Launches a celebration rocket: full colours and sound, no damage to anyone nearby. */
    static void launch(net.minecraft.world.level.Level level, double x, double y, double z, ItemStack rocket) {
        var entity = new FireworkRocketEntity(level, x, y, z, rocket);
        entity.addTag(CELEBRATION_TAG);
        level.addFreshEntity(entity);
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling("events.json.tmp");
            Files.writeString(temporary, JSON.toJson(state));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { LOG.warn("Cannot save events state", error); }
    }
}
