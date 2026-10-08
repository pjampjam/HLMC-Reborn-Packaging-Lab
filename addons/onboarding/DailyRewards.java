package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * One small gift on the first login of each Riga day. Every 7th day in a row brings a Holy Lootbox, and each
 * finished week raises the lootbox tier (up to 3). Missing a day starts the streak again.
 */
public final class DailyRewards {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final ZoneId RIGA = ZoneId.of("Europe/Riga");
    static final String LOOTBOX_KEY = "holylois_lootbox";
    private static final Identifier PRESENT = Identifier.fromNamespaceAndPath("mcwholidays", "yellow_present");

    public static final class Entry { public String last = ""; public int streak, best, lootboxes; }
    public static final class State { public int version = 1; public Map<String, Entry> players = new HashMap<>(); }

    record Gift(String item, int count, String label) {}
    // Days 1 to 6. Counts grow by half per finished week (tier), so long streaks feel better without being overpowered.
    static final List<List<Gift>> DAYS = List.of(
        List.of(new Gift("minecraft:cooked_beef", 8, "cooked beef"), new Gift("minecraft:bread", 12, "bread"), new Gift("farmersdelight:hamburger", 3, "hamburgers")),
        List.of(new Gift("minecraft:coal", 16, "coal"), new Gift("minecraft:torch", 32, "torches"), new Gift("minecraft:iron_ingot", 6, "iron ingots")),
        List.of(new Gift("farmersdelight:beef_stew", 3, "beef stew"), new Gift("minecraft:golden_carrot", 6, "golden carrots"), new Gift("farmersdelight:salmon_roll", 4, "salmon rolls")),
        List.of(new Gift("minecraft:experience_bottle", 8, "bottles o' enchanting"), new Gift("minecraft:lapis_lazuli", 12, "lapis lazuli"), new Gift("minecraft:book", 6, "books")),
        List.of(new Gift("minecraft:gold_ingot", 5, "gold ingots"), new Gift("minecraft:emerald", 3, "emeralds"), new Gift("minecraft:copper_ingot", 16, "copper ingots")),
        List.of(new Gift("minecraft:firework_rocket", 16, "firework rockets"), new Gift("minecraft:golden_apple", 1, "golden apple"), new Gift("minecraft:ender_pearl", 3, "ender pearls")));

    private State state = new State();
    private Path file;
    private final Random random = new Random();

    void load(Path worldRoot) {
        file = worldRoot.resolve("holylois/daily.json");
        try {
            if (Files.exists(file)) {
                var loaded = JSON.fromJson(Files.readString(file), State.class);
                if (loaded != null && loaded.players != null) state = loaded;
            }
        } catch (Exception error) { LOG.warn("Cannot read daily rewards; starting fresh in memory", error); }
    }

    static LocalDate today() { return LocalDate.now(RIGA); }

    /** Streak after a login on {@code today}, given the last claim day. Same day returns -1 (nothing new). */
    static int nextStreak(String last, int streak, LocalDate today) {
        if (last.equals(today.toString())) return -1;
        return last.equals(today.minusDays(1).toString()) ? streak + 1 : 1;
    }

    static int dayOfWeek(int streak) { return (streak - 1) % 7 + 1; }
    static int tier(int streak) { return Math.min(3, 1 + (streak - 1) / 7); }

    /**
     * Called once per session after login: today's gift card with the week's streak bar, plus a click-to-claim button
     * while EconomyCraft's /daily coins are still waiting. Returns the streak, or -1 when today's gift was already claimed.
     */
    int claim(MinecraftServer server, ServerPlayer player) {
        if (file == null) return -1;
        var entry = state.players.computeIfAbsent(player.getUUID().toString(), id -> new Entry());
        int streak = nextStreak(entry.last, entry.streak, today());
        long received = Economy.claimDaily(server, player);
        long coins = Economy.unclaimedDaily(server, player.getUUID());
        if (streak < 0) {
            if (received > 0) {
                player.sendSystemMessage(Component.literal("✦ Daily coins received: " + received).withStyle(ChatFormatting.GOLD));
                notify(player, "", 0, entry.streak, received, coins);
            }
            if (coins > 0) player.sendSystemMessage(Component.literal("✦ Your " + coins + " daily coins are waiting  ").withStyle(ChatFormatting.GOLD)
                .append(claimButton("[ Claim ]")));
            return -1;
        }
        entry.last = today().toString(); entry.streak = streak; entry.best = Math.max(entry.best, streak);
        int day = dayOfWeek(streak), tier = tier(streak);
        String noticeItem = "";
        int noticeCount = 1;
        var message = Component.literal("✦ Daily gift  ").withStyle(ChatFormatting.GOLD).append(bar(day))
            .append(Component.literal("  day " + day + "/7" + (streak > 7 ? ", " + streak + " days in a row" : "")).withStyle(ChatFormatting.GRAY));
        if (day == 7) {
            var box = lootbox(tier, player.getGameProfile().name());
            noticeItem = BuiltInRegistries.ITEM.getKey(box.getItem()).toString();
            give(player, box);
            entry.lootboxes++;
            message.append(Component.literal("\n  A ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Holy Lootbox").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" (tier " + tier + ")! Right-click it to open.").withStyle(ChatFormatting.GOLD));
            title(player, Component.literal("Holy Lootbox").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(streak + " days in a row").withStyle(ChatFormatting.YELLOW));
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.0f);
        } else {
            var options = DAYS.get(day - 1).stream().filter(g -> BuiltInRegistries.ITEM.containsKey(Identifier.parse(g.item()))).toList();
            var gift = options.get(random.nextInt(options.size()));
            int count = Math.max(1, (int)Math.round(gift.count() * (1 + 0.5 * (tier - 1))));
            noticeItem = gift.item(); noticeCount = count;
            give(player, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(gift.item())), count));
            message.append(Component.literal("\n  " + count + " " + gift.label()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(". " + (7 - day) + (7 - day == 1 ? " day" : " days") + " until your Holy Lootbox.").withStyle(ChatFormatting.GRAY));
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
        }
        if (coins > 0) message.append(Component.literal("\n  ")).append(claimButton("[ Claim " + coins + " daily coins ]"));
        if (received > 0) message.append(Component.literal("\n  " + received + " daily coins received.").withStyle(ChatFormatting.GREEN));
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, holylois.auth.RewardNotice.TYPE)) {
            player.sendSystemMessage(Component.literal("✦ Daily supplies delivered. Day " + day + "/7" + (received > 0 ? " + " + received + " coins." : ".")).withStyle(ChatFormatting.GOLD));
            if (coins > 0) player.sendSystemMessage(claimButton("[ Claim " + coins + " daily coins ]"));
            notify(player, noticeItem, noticeCount, streak, received, coins);
        } else player.sendSystemMessage(message);
        save();
        return streak;
    }
    private static void notify(ServerPlayer player, String item, int count, int streak, long coins, long waiting) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, holylois.auth.RewardNotice.TYPE))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new holylois.auth.RewardNotice(item, count, streak, coins, Math.max(0, waiting)));
    }

    /** Seven boxes for the week: claimed days gold, the rest dark gray, the lootbox day a star. */
    static MutableComponent bar(int day) {
        var bar = Component.empty();
        for (int i = 1; i <= 7; i++)
            bar.append(Component.literal(i == 7 ? "✦" : "■").withStyle(i > day ? ChatFormatting.DARK_GRAY : i == 7 ? ChatFormatting.YELLOW : ChatFormatting.GOLD));
        return bar;
    }

    private static MutableComponent claimButton(String label) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
            .withClickEvent(new ClickEvent.RunCommand("/daily"))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to claim (runs /daily)"))));
    }

    int lootboxesOpened(ServerPlayer player) {
        var entry = state.players.get(player.getUUID().toString());
        return entry == null ? 0 : entry.lootboxes;
    }

    static ItemStack lootbox(int tier, String owner) {
        var item = BuiltInRegistries.ITEM.containsKey(PRESENT) ? BuiltInRegistries.ITEM.getValue(PRESENT) : Items.CHEST;
        var stack = new ItemStack(item, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Holy Lootbox").withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Tier " + tier + " - a gift for " + owner).withStyle(s -> s.withColor(ChatFormatting.YELLOW).withItalic(false)),
            Component.literal("Right-click to open").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))));
        var tag = new CompoundTag();
        tag.putInt(LOOTBOX_KEY, tier);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    static int lootboxTier(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getIntOr(LOOTBOX_KEY, 0);
    }

    /** Opens a lootbox held by the player: -1 when the stack is not a lootbox, 1 with a named special item, else 0. */
    int open(MinecraftServer server, ServerPlayer player, ItemStack stack) {
        int tier = lootboxTier(stack);
        if (tier <= 0) return -1;
        stack.shrink(1);
        var rewards = new ArrayList<ItemStack>();
        String name = player.getGameProfile().name();
        var special = special(server, tier, name);
        if (special != null) rewards.add(special);
        int rolls = 2 + tier;
        for (int i = 0; i < rolls; i++) rewards.add(roll(tier));
        rewards.removeIf(ItemStack::isEmpty);
        var summary = new StringBuilder();
        for (var reward : rewards) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(reward.getCount() > 1 ? reward.getCount() + " " : "").append(reward.getHoverName().getString());
            give(player, reward);
        }
        celebrate(player, tier);
        player.sendSystemMessage(Component.literal("✦ Holy Lootbox: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(summary.toString()).withStyle(ChatFormatting.YELLOW)));
        if (special != null)
            server.getPlayerList().broadcastSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" opened a Holy Lootbox and found ").withStyle(ChatFormatting.GRAY))
                .append(special.getHoverName().copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), false);
        LOG.info("{} opened a tier {} Holy Lootbox: {}", name, tier, summary);
        return special != null ? 1 : 0;
    }

    record Loot(String item, int min, int max, int weight, int minTier) {}
    static final List<Loot> LOOT = List.of(
        new Loot("minecraft:diamond", 1, 3, 10, 1), new Loot("minecraft:emerald", 4, 10, 10, 1),
        new Loot("minecraft:golden_apple", 1, 2, 8, 1), new Loot("minecraft:ender_pearl", 4, 8, 8, 1),
        new Loot("minecraft:experience_bottle", 12, 24, 10, 1), new Loot("minecraft:name_tag", 1, 1, 5, 1),
        new Loot("minecraft:saddle", 1, 1, 3, 1), new Loot("minecraft:amethyst_shard", 8, 16, 5, 1),
        new Loot("farmersdelight:honey_glazed_ham_block", 1, 1, 4, 1), new Loot("farmersdelight:shepherds_pie_block", 1, 1, 4, 1),
        new Loot("runeforged:infusion_gem", 1, 2, 6, 1), new Loot("runeforged:edict_stone", 1, 1, 5, 2),
        new Loot("runeforged:ascension_stone", 1, 1, 4, 2), new Loot("runeforged:celestial_fragment", 1, 1, 3, 2),
        new Loot("minecraft:netherite_scrap", 1, 1, 3, 2), new Loot("minecraft:totem_of_undying", 1, 1, 2, 2),
        new Loot("minecraft:enchanted_golden_apple", 1, 1, 1, 3), new Loot("minecraft:netherite_upgrade_smithing_template", 1, 1, 2, 3));

    private ItemStack roll(int tier) {
        var pool = LOOT.stream().filter(l -> l.minTier() <= tier && BuiltInRegistries.ITEM.containsKey(Identifier.parse(l.item()))).toList();
        int total = pool.stream().mapToInt(Loot::weight).sum(), pick = random.nextInt(total);
        for (var loot : pool) {
            pick -= loot.weight();
            if (pick < 0) return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(loot.item())), loot.min() + random.nextInt(loot.max() - loot.min() + 1));
        }
        return ItemStack.EMPTY;
    }

    /** A named tool or weapon that carries the opener's name. Tier 1: 30%, tier 2: 45%, tier 3: 60%. */
    private ItemStack special(MinecraftServer server, int tier, String name) { return special(server, tier, name, false); }

    /** Same named tool, optionally guaranteed (the secret code's legendary prize). */
    ItemStack special(MinecraftServer server, int tier, String name, boolean force) {
        if (!force && random.nextDouble() >= 0.15 + 0.15 * tier) return null;
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        record Special(net.minecraft.world.item.Item item, String title, List<Map.Entry<ResourceKey<Enchantment>, Integer>> enchants) {}
        var options = List.of(
            new Special(Items.DIAMOND_PICKAXE, "Lucky Pickaxe", List.of(Map.entry(Enchantments.EFFICIENCY, 3), Map.entry(Enchantments.FORTUNE, 2), Map.entry(Enchantments.UNBREAKING, 2))),
            new Special(Items.DIAMOND_SWORD, "Holy Blade", List.of(Map.entry(Enchantments.SHARPNESS, 3), Map.entry(Enchantments.LOOTING, 2), Map.entry(Enchantments.UNBREAKING, 2))),
            new Special(Items.DIAMOND_AXE, "Timber Axe", List.of(Map.entry(Enchantments.EFFICIENCY, 4), Map.entry(Enchantments.UNBREAKING, 3))),
            new Special(Items.FISHING_ROD, "Trusty Rod", List.of(Map.entry(Enchantments.LUCK_OF_THE_SEA, 3), Map.entry(Enchantments.LURE, 2), Map.entry(Enchantments.UNBREAKING, 3))),
            new Special(Items.BOW, "Golden Bow", List.of(Map.entry(Enchantments.POWER, 4), Map.entry(Enchantments.INFINITY, 1))),
            new Special(Items.DIAMOND_SHOVEL, "Garden Spade", List.of(Map.entry(Enchantments.EFFICIENCY, 4), Map.entry(Enchantments.SILK_TOUCH, 1))));
        var choice = options.get(random.nextInt(options.size()));
        var stack = new ItemStack(choice.item(), 1);
        for (var enchant : choice.enchants())
            stack.enchant(enchantments.getOrThrow(enchant.getKey()), Math.min(enchant.getValue() + (tier == 3 ? 1 : 0), enchant.getKey() == Enchantments.INFINITY || enchant.getKey() == Enchantments.SILK_TOUCH ? 1 : 5));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name + "'s " + choice.title()).withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("From a Holy Lootbox, " + today()).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)))));
        return stack;
    }

    static void celebrate(ServerPlayer player, int tier) {
        var level = player.level();
        int[] colors = {0xF4C542, 0xFFAD42, 0xFFFFFF};
        for (int i = 0; i < tier; i++) {
            var rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                i % 2 == 0 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.BURST, IntList.of(colors), IntList.of(0xFFFFFF), true, true))));
            ServerEvents.launch(level, player.getX() + (i - 1) * 1.5, player.getY() + 1, player.getZ() + (i % 2) * 1.5, rocket);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
        title(player, Component.literal("✦ Holy Lootbox ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), Component.literal("Tier " + tier).withStyle(ChatFormatting.YELLOW));
    }

    static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty())
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling("daily.json.tmp");
            Files.writeString(temporary, JSON.toJson(state));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { LOG.warn("Cannot save daily rewards", error); }
    }
}
