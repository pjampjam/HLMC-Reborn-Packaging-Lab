package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * /redeem CODE: the secret code of the day from the holylois.com gold block easter egg. One code per Riga day for everybody, one redeem
 * per player per day, and the prize is rolled from the player and the date so trying again cannot change it. The code is
 * "HL-XXXX-XXXX" from HMAC-SHA256(secret, "holylois-daily:" + Riga date), the same function the website runs (docs/DAILY-CODE.md there).
 * Against alt accounts a redeem needs 2 hours of active play in total and 20 active minutes on the code's day (AFK time never counts).
 * The secret lives in config/holylois-daily.secret; without it the command says the code is not active.
 */
final class Redeem {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Path SECRET = Path.of("config", "holylois-daily.secret");
    static final class State {
        public int version = 1; public Map<String, String> last = new HashMap<>();
        /** Active (online, not AFK) seconds per player on activeDay; reset when the Riga day changes. */
        public String activeDay = ""; public Map<String, Long> activeToday = new HashMap<>();
    }
    static final ZoneId ZONE = ZoneId.of("Europe/Riga");
    static final long TOTAL_ACTIVE_SECONDS = 2 * 3600, TODAY_ACTIVE_SECONDS = 20 * 60;

    private final DailyRewards daily;
    private State state = new State();
    private Path file;
    private final Map<UUID, long[]> misses = new HashMap<>(); // {count, window start}
    private boolean activeDirty;

    Redeem(DailyRewards daily) { this.daily = daily; }

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("holylois/redeem.json");
        try {
            if (Files.exists(file)) {
                var loaded = JSON.fromJson(Files.readString(file), State.class);
                if (loaded != null && loaded.last != null) state = loaded;
            }
        } catch (Exception error) { LOG.warn("Cannot read redeemed codes; starting empty in memory", error); }
        LOG.info("Holy Lois secret code: {}", secret() == null ? "off (no config/holylois-daily.secret)" : "on");
    }

    static String secret() {
        try { return Files.exists(SECRET) ? Files.readString(SECRET).strip() : null; }
        catch (Exception error) { return null; }
    }

    /** The code for a Riga date, identical to the website's. */
    static String code(String secret, String day) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(("holylois-daily:" + day).getBytes(StandardCharsets.UTF_8));
            var chars = new StringBuilder();
            for (int i = 0; i < 8; i++) chars.append(ALPHABET.charAt((sig[i] & 0xff) % 32));
            return "HL-" + chars.substring(0, 4) + "-" + chars.substring(4);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    /** Upper case, no spaces or dashes, Crockford look-alikes folded (O to 0, I and L to 1). */
    static String normalize(String input) {
        return input.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "").replace('O', '0').replace('I', '1').replace('L', '1');
    }

    /** Does the typed text match today's code, or yesterday's until 01:00 Riga time? */
    static boolean matches(String secret, String typed, LocalDateTime nowRiga) {
        String want = normalize(code(secret, nowRiga.toLocalDate().toString()));
        if (normalize(typed).equals(want)) return true;
        return nowRiga.getHour() < 1 && normalize(typed).equals(normalize(code(secret, nowRiga.toLocalDate().minusDays(1).toString())));
    }

    /** Null when the player may redeem, otherwise the reason (alt protection: real play, every day). */
    static String eligibility(long totalActiveSeconds, long todayActiveSeconds) {
        if (totalActiveSeconds < TOTAL_ACTIVE_SECONDS)
            return "Codes unlock after 2 hours of active play on Holy Lois. You have " + duration(totalActiveSeconds) + " so far.";
        if (todayActiveSeconds < TODAY_ACTIVE_SECONDS)
            return "Play " + duration(TODAY_ACTIVE_SECONDS - todayActiveSeconds) + " more today, then redeem again. AFK time does not count.";
        return null;
    }

    static String duration(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + " min";
    }

    /** Once per second: count active time on the current Riga day. */
    void tick(MinecraftServer server) {
        if (file == null) return;
        String day = LocalDate.now(ZONE).toString();
        if (!day.equals(state.activeDay)) { state.activeDay = day; state.activeToday.clear(); activeDirty = true; }
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (!Afk.afkNow(player)) { state.activeToday.merge(player.getUUID().toString(), 1L, Long::sum); activeDirty = true; }
        if (activeDirty && server.getTickCount() % 1200 == 0) { save(); activeDirty = false; }
    }

    record Prize(String key, int weight) {}
    static final List<Prize> PRIZES = List.of(new Prize("coins", 500), new Prize("lootbox", 250), new Prize("diamonds", 130), new Prize("rune", 115), new Prize("legendary", 5));

    /** Deterministic for a player and a day: relogging or retrying cannot reroll. */
    static String roll(String secret, UUID player, String day) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(("prize:" + player + ":" + day).getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) seed = (seed << 8) | (sig[i] & 0xff);
            int total = PRIZES.stream().mapToInt(Prize::weight).sum(), pick = new Random(seed).nextInt(total);
            for (var prize : PRIZES) { pick -= prize.weight(); if (pick < 0) return prize.key(); }
            return "coins";
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("redeem")
            .then(Commands.argument("code", StringArgumentType.greedyString())
                .executes(c -> redeem(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "code")))));
    }

    private int redeem(ServerPlayer player, String typed) {
        var server = player.level().getServer();
        String secret = secret();
        if (secret == null || file == null) { say(player, "The secret code is not active right now.", ChatFormatting.GRAY); return 0; }
        var now = LocalDateTime.now(ZONE);
        String day = now.toLocalDate().toString();
        var id = player.getUUID();
        long epoch = now.atZone(ZONE).toEpochSecond();
        long[] miss = misses.computeIfAbsent(id, k -> new long[] {0, epoch});
        if (epoch - miss[1] > 3600) { miss[0] = 0; miss[1] = epoch; }
        if (miss[0] >= 5) { say(player, "Too many wrong codes. Try again in an hour.", ChatFormatting.RED); return 0; }
        if (!matches(secret, typed, now)) {
            miss[0]++;
            say(player, "That is not today's code.", ChatFormatting.RED);
            return 0;
        }
        if (day.equals(state.last.get(id.toString()))) { say(player, "You already used today's code. A new one comes tomorrow.", ChatFormatting.YELLOW); return 0; }
        long played = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
        String blocked = eligibility(Afk.effectiveTicks(id, played) / 20,
            day.equals(state.activeDay) ? state.activeToday.getOrDefault(id.toString(), 0L) : 0L);
        if (blocked != null) { say(player, blocked, ChatFormatting.YELLOW); return 0; }
        state.last.put(id.toString(), day);
        save();
        String name = player.getGameProfile().name();
        String prize = roll(secret, id, day);
        String text = give(server, player, prize, name);
        DailyRewards.celebrate(player, "legendary".equals(prize) ? 3 : 1);
        player.sendSystemMessage(Component.literal("✦ Secret code: ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
        if ("legendary".equals(prize))
            server.getPlayerList().broadcastSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" found the secret code and won a legendary weapon!").withStyle(ChatFormatting.GRAY)), false);
        LOG.info("{} redeemed the secret code of {}: {}", name, day, text);
        return 1;
    }

    private String give(MinecraftServer server, ServerPlayer player, String prize, String name) {
        var random = new Random(player.getUUID().getLeastSignificantBits() ^ LocalDate.now(ZONE).toEpochDay());
        switch (prize) {
            case "lootbox" -> {
                DailyRewards.give(player, DailyRewards.lootbox(1, name));
                return "a Holy Lootbox! Right-click it to open.";
            }
            case "diamonds" -> {
                DailyRewards.give(player, new ItemStack(Items.DIAMOND, 3));
                return "3 diamonds";
            }
            case "rune" -> {
                for (String item : List.of("runeforged:edict_stone", "runeforged:ascension_stone", "runeforged:infusion_gem", "runeforged:celestial_fragment")) {
                    var id = Identifier.parse(item);
                    if (BuiltInRegistries.ITEM.containsKey(id) && random.nextInt(3) == 0) {
                        DailyRewards.give(player, new ItemStack(BuiltInRegistries.ITEM.getValue(id), 1));
                        return "a Runeforged find: " + new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString();
                    }
                }
                DailyRewards.give(player, new ItemStack(Items.EMERALD, 6));
                return "6 emeralds";
            }
            case "legendary" -> {
                var weapon = daily.special(server, 3, name, true);
                if (weapon != null) { DailyRewards.give(player, weapon); return "a legendary " + weapon.getHoverName().getString() + "!"; }
                DailyRewards.give(player, new ItemStack(Items.DIAMOND, 8));
                return "8 diamonds";
            }
            default -> {
                long coins = 100 + random.nextInt(31) * 10;
                if (Economy.available()) { Economy.deposit(server, player.getUUID(), coins); return coins + " coins"; }
                DailyRewards.give(player, new ItemStack(Items.GOLD_INGOT, 5));
                return "5 gold ingots";
            }
        }
    }

    private static void say(ServerPlayer player, String text, ChatFormatting color) { player.sendSystemMessage(Component.literal(text).withStyle(color)); }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling("redeem.json.tmp");
            Files.writeString(temporary, JSON.toJson(state));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { LOG.warn("Cannot save redeemed codes", error); }
    }
}
