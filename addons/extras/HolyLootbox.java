package holylois.boombox;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The day-7 Holy Lootbox as its own item (owner round 5): the website's gold hero block with the engraved logo and its glint
 * (textures from make-lootbox.py). Opening stays on the server (onboarding DailyRewards, by its tier tag), so older lootboxes
 * made from presents still open.
 */
public final class HolyLootbox {
    private HolyLootbox() {}
    public static final Identifier ID = Identifier.fromNamespaceAndPath("holylois", "holy_lootbox");
    public static Item ITEM;

    static void register() {
        var key = ResourceKey.create(Registries.ITEM, ID);
        ITEM = Registry.register(BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.EPIC)));
    }
}
