package holylois;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;

/**
 * Server leaderboards for the Tab list, read from the vanilla stats files (saved by every autosave).
 * The Tab page rotates through the categories below.
 */
public final class Leaderboards {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Path STATS = Path.of("world", "players", "stats");
    private static final Path USERS = Path.of("usercache.json");

    record Category(String title, String unit, ToLongFunction<JsonObject> value) {}
    record Entry(String name, long value) {}

    static final List<Category> CATEGORIES = List.of(
        new Category("Most played", "h", s -> custom(s, "play_time") / 72000),
        new Category("Diamonds mined", "", s -> mined(s, "diamond_ore") + mined(s, "deepslate_diamond_ore")),
        new Category("Mob kills", "", s -> custom(s, "mob_kills")),
        new Category("Ancient debris dug", "", s -> mined(s, "ancient_debris")),
        new Category("Blocks mined", "", s -> sum(s, "minecraft:mined")),
        new Category("Emeralds mined", "", s -> mined(s, "emerald_ore") + mined(s, "deepslate_emerald_ore")),
        new Category("Distance travelled", "km", Leaderboards::distanceKm),
        new Category("Villager trades", "", s -> custom(s, "traded_with_villager")),
        new Category("Fish caught", "", s -> custom(s, "fish_caught")),
        new Category("Animals bred", "", s -> custom(s, "animals_bred")),
        new Category("Gold mined", "", s -> mined(s, "gold_ore") + mined(s, "deepslate_gold_ore") + mined(s, "nether_gold_ore")),
        new Category("Most deaths", "", s -> custom(s, "deaths")));

    private static volatile Map<Category, List<Entry>> boards = Map.of();
    private static final AtomicBoolean refreshing = new AtomicBoolean();
    // Per player: [category counter, last tick the leaderboard page was drawn].
    private static final Map<UUID, int[]> views = new HashMap<>();

    private Leaderboards() {}

    /**
     * Styled Player List renders only the visible footer page, once a second. When the leaderboard page comes
     * back after a gap, that player's next category is shown, so every visit to the page shows a new board.
     */
    static synchronized Category view(UUID player, int tick) {
        int[] state = views.computeIfAbsent(player, id -> new int[] {Math.floorMod(id.hashCode(), CATEGORIES.size()), tick});
        if (tick - state[1] > 60) state[0]++;
        state[1] = tick;
        return CATEGORIES.get(Math.floorMod(state[0], CATEGORIES.size()));
    }

    static synchronized void forget(UUID player) { views.remove(player); }

    static String row(Category category, int rank) {
        var list = boards.getOrDefault(category, List.of());
        if (rank < 1 || rank > list.size()) return rank == 1 ? "nobody yet" : "-";
        var entry = list.get(rank - 1);
        return entry.name() + "  " + String.format("%,d", entry.value()) + (category.unit().isEmpty() ? "" : " " + category.unit());
    }

    /** Off-thread refresh; a slow disk never stalls a tick. */
    static void refreshAsync() {
        if (!refreshing.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("holylois-leaderboards").start(() -> {
            try { boards = compute(names(), readStats()); }
            catch (Exception error) { LOG.warn("Leaderboard refresh failed", error); }
            finally { refreshing.set(false); }
        });
    }

    static Map<Category, List<Entry>> compute(Map<String, String> names, Map<String, JsonObject> stats) {
        var result = new HashMap<Category, List<Entry>>();
        for (var category : CATEGORIES) {
            var entries = new ArrayList<Entry>();
            for (var stat : stats.entrySet()) {
                String name = names.get(stat.getKey());
                if (name == null) continue;
                long value = category.value().applyAsLong(stat.getValue());
                if (value > 0) entries.add(new Entry(name, value));
            }
            entries.sort(Comparator.comparingLong(Entry::value).reversed().thenComparing(Entry::name));
            result.put(category, List.copyOf(entries.subList(0, Math.min(3, entries.size()))));
        }
        return result;
    }

    private static Map<String, JsonObject> readStats() throws Exception {
        var result = new HashMap<String, JsonObject>();
        if (!Files.isDirectory(STATS)) return result;
        try (var files = Files.list(STATS)) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (root.has("stats")) {
                        String uuid = file.getFileName().toString().replace(".json", "");
                        var stats = root.getAsJsonObject("stats");
                        withoutAfk(stats, uuid);
                        result.put(uuid, stats);
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    /** Plays AFK time out of the stats copy: play_time minus the AFK ledger. */
    private static void withoutAfk(JsonObject stats, String uuid) {
        try {
            long afk = Afk.seconds(UUID.fromString(uuid));
            var custom = stats.getAsJsonObject("minecraft:custom");
            if (afk > 0 && custom != null && custom.has("minecraft:play_time"))
                custom.addProperty("minecraft:play_time", Afk.effective(custom.get("minecraft:play_time").getAsLong(), afk));
        } catch (IllegalArgumentException ignored) { /* not a player file */ }
    }

    private static Map<String, String> names() throws Exception {
        var result = new HashMap<String, String>();
        if (!Files.exists(USERS)) return result;
        for (var element : JsonParser.parseString(Files.readString(USERS)).getAsJsonArray()) {
            var user = element.getAsJsonObject();
            if (user.has("uuid") && user.has("name")) result.put(user.get("uuid").getAsString(), user.get("name").getAsString());
        }
        return result;
    }

    static long custom(JsonObject stats, String key) { return get(stats, "minecraft:custom", "minecraft:" + key); }
    static long mined(JsonObject stats, String block) { return get(stats, "minecraft:mined", "minecraft:" + block); }

    private static long get(JsonObject stats, String group, String key) {
        var values = stats.getAsJsonObject(group);
        return values != null && values.has(key) ? values.get(key).getAsLong() : 0;
    }

    private static long sum(JsonObject stats, String group) {
        var values = stats.getAsJsonObject(group);
        if (values == null) return 0;
        long total = 0;
        for (var value : values.entrySet()) total += value.getValue().getAsLong();
        return total;
    }

    private static long distanceKm(JsonObject stats) {
        var values = stats.getAsJsonObject("minecraft:custom");
        if (values == null) return 0;
        long centimetres = 0;
        for (var value : values.entrySet())
            if (value.getKey().endsWith("_one_cm") && !value.getKey().contains("fall")) centimetres += value.getValue().getAsLong();
        return centimetres / 100_000;
    }
}
