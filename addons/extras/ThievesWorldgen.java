package holylois.boombox;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * Fish of Thieves keeps its fish, items and fruit, but its palm, mango and banana trees and tropical bushes do not fit the
 * vanilla look (owner 2026-10-09): removed from every biome. Only new chunks change. Its tropical island biome is switched
 * off in config/fishofthieves.json (biome.tropicalIslandBiomeGeneration = false) on the server.
 */
final class ThievesWorldgen {
    private ThievesWorldgen() {}
    static final List<String> TREES = List.of("trees_coconut", "trees_coconut_tropical_island", "trees_tropical_island", "sparse_jungle_fruit_trees",
        "coconut_tree_checked", "old_coconut_tree_checked", "banana_tree_checked", "mango_tree_checked", "mango_tree_leaf_litter_checked",
        "mango_tree_bees_02_leaf_litter_checked", "patch_tropical_bush", "sparse_jungle_patch_tropical_bush", "tropical_island_rock");

    static void register() {
        BiomeModifications.create(Identifier.fromNamespaceAndPath("holylois", "no_thieves_trees")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(), context -> {
            for (String tree : TREES)
                context.getGenerationSettings().removeFeature(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("fishofthieves", tree)));
        });
    }
}
