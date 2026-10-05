package holylois.boombox;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Legends and fishing treasure (server side, no client code needed):
 * - Structure chests rarely hold one item of a legend: a named item with a vague inscription, marked with
 *   custom_data {holylois_legend, holylois_legend_set} so advancements find it and {holylois_light: 1b} for a held light.
 * - When vanilla fishing rolls treasure, it is sometimes a message in a bottle, a buried treasure map or a fishing-only legend item.
 *   Fished-up loot crates (crateTables) can hold the fishing-only legend items too.
 * - Every fish caught gets a size: Common stays plain (stacks as before), Uncommon gets a green name, Rare and up keep their exact
 *   weight as a trophy that does not stack. A Legendary catch is announced in chat.
 * Lore and chances live in config/holylois-legends.json, fish ranges in config/holylois-fish.json; /legends reload reads both.
 */
public final class Legends {
    static final String LEGEND_KEY = "holylois_legend", SET_KEY = "holylois_legend_set", LIGHT_KEY = "holylois_light", FISH_KEY = "holylois_fish",
        BOTTLE_KEY = "holylois_bottle";
    private static final Path CONFIG = Path.of("config", "holylois-legends.json"), FISH = Path.of("config", "holylois-fish.json");
    private static final ResourceKey<LootTable> MAP = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("holylois", "gameplay/treasure_map"));
    private static final String TREASURE = "minecraft:gameplay/fishing/treasure";
    private static final ZoneId RIGA = ZoneId.of("Europe/Riga");
    private static final Gson JSON = new Gson();
    /** Null while the file is missing or broken: that part is off. */
    static volatile LootRules.LegendsConfig legends;
    static volatile LootRules.FishConfig fish;

    private Legends() {}

    static void register() {
        Boombox.LOG.info(load());
        LootTableEvents.MODIFY_DROPS.register(Legends::drops);
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("legends")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("reload").executes(context -> {
                context.getSource().sendSystemMessage(Component.literal(load()).withStyle(ChatFormatting.GOLD));
                return 1;
            }))
            .then(Commands.literal("list").executes(context -> {
                var config = legends;
                if (config == null) { context.getSource().sendSystemMessage(Component.literal("Legends are off.")); return 0; }
                var ids = new ArrayList<String>();
                for (var legend : config.legends) for (var piece : legend.items) ids.add(piece.id + " (" + piece.where + ")");
                context.getSource().sendSystemMessage(Component.literal("Legend items: " + String.join(", ", ids)).withStyle(ChatFormatting.GOLD));
                return ids.size();
            }))
            .then(Commands.literal("give").then(Commands.argument("item", StringArgumentType.word()).executes(context -> {
                var player = context.getSource().getPlayerOrException();
                var level = (ServerLevel) player.level();
                String wanted = StringArgumentType.getString(context, "item");
                ItemStack stack = ItemStack.EMPTY;
                var config = legends;
                if (wanted.equals("bottle") && config != null) stack = bottle(config, player.getRandom());
                else if (wanted.equals("map")) stack = map(level, player.position(), player.getRandom());
                else if (wanted.equals("fish")) { stack = new ItemStack(Items.COD); if (fish != null) weigh(stack, level, player.getRandom(), 0, player.getName().getString(), fish); }
                else if (config != null) for (var found : all(config))
                    if (found.piece().id.equals(wanted)) stack = make(level, player.position(), player.getRandom(), found);
                if (stack.isEmpty()) { context.getSource().sendSystemMessage(Component.literal("Unknown item. Try /legends list, bottle, map or fish.").withStyle(ChatFormatting.RED)); return 0; }
                if (!player.getInventory().add(stack) && !stack.isEmpty())
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack));
                return 1;
            })))));
    }

    private static List<LootRules.Found> all(LootRules.LegendsConfig config) {
        var found = new ArrayList<LootRules.Found>();
        for (var legend : config.legends) for (var piece : legend.items) found.add(new LootRules.Found(legend, piece));
        return found;
    }

    /** Reads both files; returns one line for the log and for /legends reload. */
    static String load() {
        String legendLine, fishLine;
        try {
            if (Files.exists(CONFIG)) {
                var config = JSON.fromJson(Files.readString(CONFIG), LootRules.LegendsConfig.class);
                for (String problem : LootRules.problems(config)) Boombox.LOG.warn("holylois-legends.json: {}", problem);
                legends = config;
                legendLine = config.legends.size() + " legends, " + all(config).size() + " items, " + config.bottles.size() + " bottle messages";
            } else { legends = null; legendLine = "off (no " + CONFIG + ")"; }
        } catch (Exception error) { legends = null; legendLine = "off (broken file: " + error.getMessage() + ")"; Boombox.LOG.warn("Cannot read {}", CONFIG, error); }
        try {
            if (Files.exists(FISH)) {
                var config = JSON.fromJson(Files.readString(FISH), LootRules.FishConfig.class);
                fish = config;
                fishLine = config.enabled ? config.species.size() + " species" : "off";
            } else { fish = null; fishLine = "off (no " + FISH + ")"; }
        } catch (Exception error) { fish = null; fishLine = "off (broken file: " + error.getMessage() + ")"; Boombox.LOG.warn("Cannot read {}", FISH, error); }
        return "Holy Lois legends: " + legendLine + "; fish weights: " + fishLine;
    }

    private static void drops(Holder<LootTable> holder, LootContext context, List<ItemStack> drops) {
        var key = holder.unwrapKey();
        if (key.isEmpty()) return;
        String id = key.get().identifier().toString();
        if (id.startsWith("holylois:")) return;
        var random = context.getRandom();
        var level = context.getLevel();
        Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
        var config = legends;
        if (config != null && origin != null) {
            if (id.equals(TREASURE)) {
                if (random.nextDouble() < config.treasureChance) {
                    var treasure = treasure(config, level, origin, random);
                    if (!treasure.isEmpty()) { drops.clear(); drops.add(treasure); }
                }
            } else if (LootRules.matchesAny(config.crateTables, id) ? random.nextDouble() < config.crateChance
                       : LootRules.matchesAny(config.chestTables, id) && random.nextDouble() < config.chestChance) {
                String place = LootRules.matchesAny(config.crateTables, id) ? "fishing" : "chests";
                var found = LootRules.pick(LootRules.pieces(config, place), f -> f.piece().weight, random.nextDouble());
                if (found != null) {
                    var stack = make(level, origin, random, found);
                    if (!stack.isEmpty()) drops.add(stack);
                }
            }
        }
        var weights = fish;
        if (weights != null && weights.enabled && LootRules.matchesAny(weights.tables, id)) {
            var hook = context.getOptional(LootContextParams.THIS_ENTITY);
            String angler = hook instanceof FishingHook fishing && fishing.getPlayerOwner() != null ? fishing.getPlayerOwner().getName().getString() : null;
            for (var stack : drops) weigh(stack, level, random, context.getLuck(), angler, weights);
        }
    }

    private static ItemStack treasure(LootRules.LegendsConfig config, ServerLevel level, Vec3 origin, RandomSource random) {
        String kind = LootRules.pick(new ArrayList<>(config.treasure.keySet()), k -> config.treasure.get(k), random.nextDouble());
        if (kind == null) return ItemStack.EMPTY;
        return switch (kind) {
            case "bottle" -> bottle(config, random);
            case "map" -> map(level, origin, random);
            case "legend" -> {
                var found = LootRules.pick(LootRules.pieces(config, "fishing"), f -> f.piece().weight, random.nextDouble());
                yield found == null ? ItemStack.EMPTY : make(level, origin, random, found);
            }
            default -> ItemStack.EMPTY;
        };
    }

    static ItemStack bottle(LootRules.LegendsConfig config, RandomSource random) {
        if (config.bottles.isEmpty()) return ItemStack.EMPTY;
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Message in a Bottle").withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)));
        var lines = new ArrayList<Component>();
        for (String line : LootRules.wrap(config.bottles.get(random.nextInt(config.bottles.size())), 38))
            lines.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        var tag = new CompoundTag();
        tag.putBoolean(BOTTLE_KEY, true);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    /** A real buried treasure map from data/holylois/loot_table/gameplay/treasure_map.json, located from where it was found. */
    static ItemStack map(ServerLevel level, Vec3 origin, RandomSource random) {
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
        var items = level.getServer().reloadableRegistries().getLootTable(MAP).getRandomItems(params, random.nextLong());
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }

    static ItemStack make(ServerLevel level, Vec3 origin, RandomSource random, LootRules.Found found) {
        var piece = found.piece(); var legend = found.legend();
        final ItemStack stack;
        if (piece.item.equals("treasure_map")) stack = map(level, origin, random);
        else {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(piece.item));
            if (item == Items.AIR) { Boombox.LOG.warn("Legend item {} has an unknown base item {}", piece.id, piece.item); return ItemStack.EMPTY; }
            stack = new ItemStack(item);
        }
        if (stack.isEmpty()) return stack;
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var entry : piece.enchantments.entrySet()) {
            try { stack.enchant(enchantments.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(entry.getKey()))), entry.getValue()); }
            catch (RuntimeException error) { Boombox.LOG.warn("Legend item {}: unknown enchantment {}", piece.id, entry.getKey()); }
        }
        var color = color(legend.color);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(piece.name).withStyle(s -> s.withColor(color).withItalic(false)));
        var lines = new ArrayList<Component>();
        for (String text : piece.lore) for (String line : LootRules.wrap(text, 38))
            lines.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)));
        lines.add(Component.literal("✦ " + legend.name).withStyle(s -> s.withColor(color).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        if (piece.enchantments.isEmpty()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var data = stack.get(DataComponents.CUSTOM_DATA);
        var tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putString(LEGEND_KEY, piece.id);
        tag.putString(SET_KEY, legend.id);
        if (piece.light) tag.putBoolean(LIGHT_KEY, true);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    /** Gives one caught fish its size; leaves anything else (and fish already weighed or legend items) alone. */
    static void weigh(ItemStack stack, ServerLevel level, RandomSource random, float luck, String angler, LootRules.FishConfig config) {
        if (stack.isEmpty() || stack.getCount() != 1) return;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) { var tag = data.copyTag(); if (tag.contains(FISH_KEY) || tag.contains(LEGEND_KEY)) return; }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        double[] range = config.species.get(id);
        if (range == null) {
            if (!stack.has(DataComponents.FOOD) || config.skip.stream().anyMatch(id::contains)) return;
            boolean listed = config.namespaces.contains(id.substring(0, id.indexOf(':')));
            for (String tag : config.tags) listed |= stack.is(TagKey.create(Registries.ITEM, Identifier.parse(tag)));
            if (!listed) return;
            range = config.fallback;
        }
        double size = LootRules.size(random.nextDouble(), config.curve, luck);
        var rarity = LootRules.rarity(size);
        if (rarity == LootRules.RARITIES.getFirst()) return;
        var color = color(rarity.color());
        var name = stack.getHoverName().copy();
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withColor(color).withItalic(false)));
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("✦ " + rarity.name() + " catch").withStyle(s -> s.withColor(color).withItalic(false)));
        var fishTag = new CompoundTag();
        fishTag.putString("rarity", rarity.name().toLowerCase(Locale.ROOT));
        if (rarity.trophy()) {
            double kilograms = LootRules.kilograms(range, size);
            String day = LocalDate.now(RIGA).toString();
            lines.add(Component.literal("Weight: " + LootRules.kg(kilograms)).withStyle(s -> s.withColor(ChatFormatting.WHITE).withItalic(false)));
            lines.add(Component.literal((angler == null ? "Caught on " : "Caught by " + angler + ", ") + day).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)));
            fishTag.putDouble("kg", kilograms);
            fishTag.putString("species", id);
            fishTag.putString("day", day);
            if (angler != null) fishTag.putString("by", angler);
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
            if (rarity.from() >= 0.9) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (rarity.name().equals("Legendary") && angler != null)
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(angler).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" caught a Legendary ").withStyle(ChatFormatting.GRAY))
                    .append(name.withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" of " + LootRules.kg(kilograms) + "!").withStyle(ChatFormatting.GRAY)), false);
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        var tag = data == null ? new CompoundTag() : data.copyTag();
        tag.put(FISH_KEY, fishTag);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    /** A colour name as in chat (gold, aqua, red, light_purple, ...). Unknown names are gold. */
    static ChatFormatting color(String name) {
        try { return ChatFormatting.valueOf(name.toUpperCase(Locale.ROOT)); } catch (RuntimeException error) { return ChatFormatting.GOLD; }
    }
}
