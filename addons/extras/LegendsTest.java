package holylois.boombox;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** Legends, fish weights and the mod check's built-ins without a running game. Args: the legends and fish config files to check. */
public final class LegendsTest {
    public static void main(String[] args) throws Exception {
        check(LootRules.matches("*:chests/*", "nova_structures:chests/tavern/bar") && LootRules.matches("*:chests/*", "minecraft:chests/simple_dungeon")
            && !LootRules.matches("*:chests/*", "minecraft:gameplay/fishing") && LootRules.matches("minecraft:gameplay/fishing", "minecraft:gameplay/fishing")
            && !LootRules.matches("minecraft:gameplay/fishing", "minecraft:gameplay/fishing/fish") && LootRules.matches("*:*crate*", "fishingloot:crates/wooden") && !LootRules.matchesAny(new LootRules.LegendsConfig().chestTables, "fishingloot:crates/wooden"), "table globs");
        check(LootRules.rarity(0).name().equals("Common") && LootRules.rarity(0.5).name().equals("Uncommon") && LootRules.rarity(0.8).name().equals("Rare")
            && LootRules.rarity(0.95).name().equals("Epic") && LootRules.rarity(1).name().equals("Legendary"), "rarity tiers");
        check(!LootRules.rarity(0.6).trophy() && LootRules.rarity(0.76).trophy(), "only Rare and up are trophies");
        check(LootRules.kilograms(new double[] {1, 3}, 0.5) == 2.0 && LootRules.kg(2.0).equals("2.00 kg") && LootRules.kg(12.345).equals("12.35 kg"), "kilograms");
        check(LootRules.size(1, 2.5, 0) == 1 && LootRules.size(0, 2.5, 0) == 0 && LootRules.size(0.5, 2.5, 3) > LootRules.size(0.5, 2.5, 0), "luck makes bigger fish");
        var counts = new int[LootRules.RARITIES.size()]; var random = new Random(7);
        for (int i = 0; i < 200_000; i++) counts[LootRules.RARITIES.indexOf(LootRules.rarity(LootRules.size(random.nextDouble(), 2.5, 0)))]++;
        double common = counts[0] / 2000.0, legendary = counts[4] / 2000.0;
        System.out.printf("Rarity share in %%: common %.1f, uncommon %.1f, rare %.1f, epic %.1f, legendary %.1f%n",
            common, counts[1] / 2000.0, counts[2] / 2000.0, counts[3] / 2000.0, legendary);
        check(common > 70 && common < 82 && legendary > 0.6 && legendary < 2, "rarity shares");
        check(LootRules.wrap("The inscription reads: ring once for the lost, twice for the found.", 38)
            .equals(List.of("The inscription reads: ring once for", "the lost, twice for the found.")), "lore wrap");
        check(LootRules.pick(List.of("a", "b"), s -> s.equals("a") ? 0 : 5, 0.0).equals("b") && LootRules.pick(List.<String>of(), s -> 1, 0.5) == null, "weighted pick");

        var mods = ModCheck.withBuiltins(ModCheck.parse("{\"mode\":\"enforce\",\"allowed\":[\"sodium\",\"holylois-extras\"]}"), List.of("java", "minecraft", "fabricloader", "mixinextras"));
        var verdict = ModCheck.check(mods, List.of("java", "minecraft", "fabricloader", "mixinextras", "sodium", "holylois-extras"));
        check(verdict.unknown().isEmpty() && verdict.missing().isEmpty(), "loader built-ins always allowed");
        check(ModCheck.check(mods, List.of("mixinextras", "sodium", "holylois-extras", "xray")).unknown().contains("xray"), "extra mods still caught");

        var gson = new Gson();
        if (args.length >= 1) {
            var legends = gson.fromJson(Files.readString(Path.of(args[0])), LootRules.LegendsConfig.class);
            check(LootRules.problems(legends).isEmpty(), "legends config: " + LootRules.problems(legends));
            check(!LootRules.pieces(legends, "chests").isEmpty() && !LootRules.pieces(legends, "fishing").isEmpty() && !legends.bottles.isEmpty(), "legends config has chest, fishing and bottle loot");
            System.out.println(args[0] + ": " + legends.legends.size() + " legends, " + LootRules.pieces(legends, "chests").size() + " chest items, "
                + LootRules.pieces(legends, "fishing").size() + " fishing items, " + legends.bottles.size() + " bottles");
        }
        if (args.length >= 2) {
            var fish = gson.fromJson(Files.readString(Path.of(args[1])), LootRules.FishConfig.class);
            for (var entry : fish.species.entrySet())
                check(entry.getValue().length == 2 && entry.getValue()[0] > 0 && entry.getValue()[1] > entry.getValue()[0], "fish range " + entry.getKey());
            check(fish.enabled && fish.fallback.length == 2 && !fish.tables.isEmpty(), "fish config");
            System.out.println(args[1] + ": " + fish.species.size() + " species");
        }
        System.out.println("LegendsTest passed");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
