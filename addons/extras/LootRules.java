package holylois.boombox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Plain rules behind legends and fish weights, kept free of Minecraft classes so LegendsTest can run them on the server's JVM.
 * The two config files are read with Gson into the public fields below; missing fields keep these defaults.
 */
public final class LootRules {
    private LootRules() {}

    /** config/holylois-legends.json */
    public static final class LegendsConfig {
        /** Chance that one legend item is added when a matching chest (or crate) table rolls. */
        public double chestChance = 0.012;
        /** Loot tables that count as structure chests: "namespace:path" globs, * matches anything. */
        public List<String> chestTables = new ArrayList<>(List.of("*:chests/*"));
        /** Loot crates fished up (Fishing Loot Crates): they can hold the fishing-only legend items, at crateChance. */
        public List<String> crateTables = new ArrayList<>(List.of("*:*crate*"));
        public double crateChance = 0.05;
        /** Chance that a vanilla fishing treasure catch becomes a Holy Lois treasure instead. */
        public double treasureChance = 0.3;
        /** Weights of the Holy Lois treasure kinds: bottle (message in a bottle), map (buried treasure map), legend. */
        public Map<String, Integer> treasure = new LinkedHashMap<>(Map.of("bottle", 6, "map", 3, "legend", 1));
        public List<String> bottles = new ArrayList<>();
        public List<Legend> legends = new ArrayList<>();
    }

    public static final class Legend {
        public String id, name, color = "gold";
        public List<Piece> items = new ArrayList<>();
    }

    /** One legend item. item is an item id, or "treasure_map" for a real buried treasure map. where: chests, fishing or both. */
    public static final class Piece {
        public String id, item, name, where = "chests";
        public List<String> lore = new ArrayList<>();
        public Map<String, Integer> enchantments = new LinkedHashMap<>();
        public int weight = 1;
        public boolean light;

        public boolean in(String place) { return where.equals("both") || where.equals(place); }
    }

    /** config/holylois-fish.json */
    public static final class FishConfig {
        public boolean enabled = true;
        /** Loot tables whose catches get a weight (the top fishing table, so every fish passes once). */
        public List<String> tables = new ArrayList<>(List.of("minecraft:gameplay/fishing"));
        /** Items without their own range still count as fish when they are edible and in one of these tags or namespaces. */
        public List<String> tags = new ArrayList<>(List.of("minecraft:fishes"));
        public List<String> namespaces = new ArrayList<>(List.of("fishofthieves"));
        /** Item ids containing one of these words are never weighed (cooked fish, buckets). */
        public List<String> skip = new ArrayList<>(List.of("cooked", "bucket"));
        /** Lightest and heaviest weight in kg. */
        public double[] fallback = {0.3, 6.0};
        public Map<String, double[]> species = new LinkedHashMap<>();
        /** Higher means big fish are rarer. Luck (Luck of the Sea, luck potions) softens it. */
        public double curve = 2.5;
        /** Share of Legendary catches that turn Mythic (about 1 fish in 5000): far past the species' heaviest weight. */
        public double mythicChance = 1 / 60.0;
    }

    public record Rarity(String name, double from, String color, boolean trophy) {}

    /** Share of each tier with curve 2.5 and no luck: about 76%, 13%, 7%, 3% and 1%. Mythic is never rolled by size, see mythic(). */
    public static final List<Rarity> RARITIES = List.of(
        new Rarity("Common", 0, "white", false), new Rarity("Uncommon", 0.5, "green", false), new Rarity("Rare", 0.75, "aqua", true),
        new Rarity("Epic", 0.9, "light_purple", true), new Rarity("Legendary", 0.97, "gold", true));
    public static final Rarity MYTHIC = new Rarity("Mythic", 2, "red", true);

    /** A Mythic fish weighs 1.5 to 2.5 times the species' heaviest normal weight; returns that factor. */
    public static double mythic(double roll) { return 1.5 + Math.clamp(roll, 0.0, 1.0); }

    /** Turns a uniform roll into how big the fish is between its lightest (0) and heaviest (1). */
    public static double size(double roll, double curve, float luck) {
        double bent = Math.max(1.0, curve / (1 + 0.15 * Math.max(0, luck)));
        return Math.pow(Math.clamp(roll, 0.0, 1.0), bent);
    }

    public static Rarity rarity(double size) {
        Rarity found = RARITIES.getFirst();
        for (Rarity rarity : RARITIES) if (size >= rarity.from()) found = rarity;
        return found;
    }

    /** Weight in kg rounded to grams' tens, so the tooltip and the stored value agree. */
    public static double kilograms(double[] range, double size) {
        return Math.round((range[0] + (range[1] - range[0]) * size) * 100) / 100.0;
    }

    public static String kg(double kilograms) { return String.format(Locale.ROOT, "%.2f kg", kilograms); }

    /** Splits a lore text into tooltip lines of at most width characters, at spaces. */
    public static List<String> wrap(String text, int width) {
        var lines = new ArrayList<String>(); var line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) { lines.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** "namespace:path" glob, * matches any run of characters (slashes included). */
    public static boolean matches(String glob, String id) {
        var parts = new ArrayList<String>();
        for (String part : glob.split("\\*", -1)) parts.add(Pattern.quote(part));
        return Pattern.matches(String.join(".*", parts), id);
    }

    public static boolean matchesAny(List<String> globs, String id) {
        for (String glob : globs) if (matches(glob, id)) return true;
        return false;
    }

    /** Picks by weight; roll is uniform in [0, 1). Null when nothing has a weight. */
    public static <T> T pick(List<T> options, java.util.function.ToIntFunction<T> weight, double roll) {
        int total = 0;
        for (T option : options) total += Math.max(0, weight.applyAsInt(option));
        if (total <= 0) return null;
        double point = roll * total;
        for (T option : options) {
            point -= Math.max(0, weight.applyAsInt(option));
            if (point < 0) return option;
        }
        return options.getLast();
    }

    /** Every piece that can turn up in a place, with the legend it belongs to. */
    public record Found(Legend legend, Piece piece) {}

    public static List<Found> pieces(LegendsConfig config, String place) {
        var found = new ArrayList<Found>();
        for (Legend legend : config.legends) for (Piece piece : legend.items) if (piece.in(place)) found.add(new Found(legend, piece));
        return found;
    }

    /** Problems a config can have that would only show up in game; empty when it is fine. */
    public static List<String> problems(LegendsConfig config) {
        var problems = new ArrayList<String>(); var ids = new java.util.HashSet<String>();
        if (config.chestChance < 0 || config.chestChance > 0.2) problems.add("chestChance should be between 0 and 0.2");
        if (config.treasureChance < 0 || config.treasureChance > 1) problems.add("treasureChance should be between 0 and 1");
        if (config.crateChance < 0 || config.crateChance > 1) problems.add("crateChance should be between 0 and 1");
        for (Legend legend : config.legends) {
            if (legend.id == null || legend.name == null) { problems.add("a legend without id or name"); continue; }
            for (Piece piece : legend.items) {
                if (piece.id == null || piece.item == null || piece.name == null) { problems.add(legend.id + ": an item without id, item or name"); continue; }
                if (!ids.add(piece.id)) problems.add("item id used twice: " + piece.id);
                if (!List.of("chests", "fishing", "both").contains(piece.where)) problems.add(piece.id + ": where must be chests, fishing or both");
                for (String line : piece.lore) if (line.indexOf('\u2014') >= 0) problems.add(piece.id + ": em dash in lore");
            }
        }
        for (String bottle : config.bottles) if (bottle.indexOf('\u2014') >= 0) problems.add("em dash in a bottle message");
        return problems;
    }
}
