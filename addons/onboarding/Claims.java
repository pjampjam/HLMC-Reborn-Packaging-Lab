package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;

/**
 * Land on top of Open Parties and Claims. OPAC's maxPlayerClaims is the free amount; on top of it players earn chunks by
 * playing and buy more with coins at a rising price. OPAC stores earned + bought as each player's bonus claims.
 */
final class Claims {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final class Config {
        public double hoursPerEarnedChunk = 2;
        public int maxEarned = 48;
        public long basePrice = 500;
        public double priceGrowth = 1.15;
        public int maxBought = 64;
        public double refund = 0.5;
    }
    static final class Data { public int version = 1; public Map<UUID,Integer> bought = new HashMap<>(); }

    private Config config = new Config();
    private Data data = new Data();
    private Path dataFile;
    private final Path configFile = Path.of("config", "holylois-claims.json");

    static boolean available() { return FabricLoader.getInstance().isModLoaded("openpartiesandclaims"); }

    /** Price of the next chunk after `alreadyBought` purchases. */
    static long price(Config c, int alreadyBought) { return Math.round(c.basePrice * Math.pow(c.priceGrowth, alreadyBought)); }
    static long cost(Config c, int bought, int count) { long sum = 0; for (int i = 0; i < count; i++) sum += price(c, bought + i); return sum; }
    /** Coins back for selling the last `count` bought chunks. */
    static long refund(Config c, int bought, int count) { long sum = 0; for (int i = 1; i <= count; i++) sum += Math.round(price(c, bought - i) * c.refund); return sum; }
    static int earned(Config c, long playTicks) { return (int) Math.min(c.maxEarned, playTicks / (long) (72000 * c.hoursPerEarnedChunk)); }
    static long ticksToNext(Config c, long playTicks) {
        long step = (long) (72000 * c.hoursPerEarnedChunk);
        return earned(c, playTicks) >= c.maxEarned ? -1 : step - playTicks % step;
    }

    void load(MinecraftServer server) {
        try {
            if (!Files.exists(configFile)) Files.writeString(configFile, JSON.toJson(new Config()));
            Config loaded = JSON.fromJson(Files.readString(configFile), Config.class);
            if (loaded != null && loaded.basePrice > 0 && loaded.priceGrowth >= 1 && loaded.hoursPerEarnedChunk > 0) config = loaded;
            dataFile = server.getWorldPath(LevelResource.ROOT).resolve("holylois/claims.json");
            if (Files.exists(dataFile)) {
                Data stored = JSON.fromJson(Files.readString(dataFile), Data.class);
                if (stored != null && stored.bought != null) data = stored;
            }
            LOG.info("Holy Lois claims: {} for OPAC, first extra chunk {} coins, x{} each", available() ? "ready" : "waiting", config.basePrice, config.priceGrowth);
        } catch (Exception error) { LOG.warn("Holy Lois claims: keeping defaults", error); }
    }

    private void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            var temporary = dataFile.resolveSibling("claims.json.tmp");
            Files.writeString(temporary, JSON.toJson(data));
            Files.move(temporary, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new IllegalStateException("Cannot save Holy Lois claims", error); }
    }

    private long playTicks(ServerPlayer player) { return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)); }
    int bought(UUID id) { return data.bought.getOrDefault(id, 0); }

    /** Push earned + bought into OPAC. Called on join and every few minutes. */
    void refresh(ServerPlayer player) {
        if (!available()) return;
        Bridge.setBonus(player.level().getServer(), player.getUUID(), earned(config, playTicks(player)) + bought(player.getUUID()));
    }

    /** True when a claim is within `radius` chunks of the given chunk (any owner). */
    static boolean nearClaim(MinecraftServer server, Identifier dimension, int chunkX, int chunkZ, int radius) {
        return available() && Bridge.nearClaim(server, dimension, chunkX, chunkZ, radius);
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("claims")
            .executes(c -> { info(c.getSource().getPlayerOrException()); return 1; })
            .then(Commands.literal("buy").executes(c -> buy(c.getSource().getPlayerOrException(), 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(c -> buy(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "count")))))
            .then(Commands.literal("sell").executes(c -> sell(c.getSource().getPlayerOrException(), 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(c -> sell(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "count"))))));
    }

    private static MutableComponent line(String text, ChatFormatting color) { return Component.literal(text).withStyle(color); }

    private void info(ServerPlayer player) {
        if (!available()) { player.sendSystemMessage(line("Land claims are not running right now.", ChatFormatting.RED)); return; }
        refresh(player);
        var server = player.level().getServer(); UUID id = player.getUUID();
        long ticks = playTicks(player); int earned = earned(config, ticks), bought = bought(id);
        int used = Bridge.claimCount(server, id), limit = Bridge.limit(server, player), base = Bridge.baseLimit(server, player);
        var text = Component.literal("✦ Your land").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(line("\nClaimed: " + used + " / " + limit + " chunks", ChatFormatting.WHITE).withStyle(s -> s.withBold(false)))
            .append(line("\nFree: " + base + "  |  Earned by playing: " + earned + "  |  Bought: " + bought, ChatFormatting.GRAY).withStyle(s -> s.withBold(false)));
        long next = ticksToNext(config, ticks);
        if (next > 0) text.append(line("\nNext free chunk in " + (next / 72000) + " h " + (next % 72000 / 1200) + " min of play", ChatFormatting.GRAY).withStyle(s -> s.withBold(false)));
        if (bought < config.maxBought) {
            long price = price(config, bought);
            text.append(line("\nNext chunk costs " + price + " coins ", ChatFormatting.YELLOW).withStyle(s -> s.withBold(false)))
                .append(Component.literal("[Buy 1]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                    .withClickEvent(new ClickEvent.SuggestCommand("/claims buy 1"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Each extra chunk costs " + Math.round((config.priceGrowth - 1) * 100) + "% more than the last")))));
        }
        text.append(line("\nHow to claim: open the world map (M), right-click a chunk and choose Claim, or stand in it and use /oclaims claim. "
                + "Teams: /oparties create, then /oparties invite NAME.", ChatFormatting.AQUA).withStyle(s -> s.withBold(false)));
        player.sendSystemMessage(text);
    }

    private int buy(ServerPlayer player, int count) {
        if (!available()) return 0;
        UUID id = player.getUUID(); int bought = bought(id);
        if (bought + count > config.maxBought) { player.sendSystemMessage(line("You can buy at most " + config.maxBought + " extra chunks (you have " + bought + ").", ChatFormatting.RED)); return 0; }
        long cost = cost(config, bought, count);
        if (!Economy.withdraw(player.level().getServer(), id, cost)) {
            player.sendSystemMessage(line("That costs " + cost + " coins and you have " + Economy.balance(player.level().getServer(), id) + ". Sell on /ah or claim /daily.", ChatFormatting.RED));
            return 0;
        }
        data.bought.put(id, bought + count); save(); refresh(player);
        player.sendSystemMessage(line("✦ Bought " + count + (count == 1 ? " chunk" : " chunks") + " for " + cost + " coins. Next one: " + price(config, bought + count) + ".", ChatFormatting.GREEN));
        LOG.info("{} bought {} claim chunk(s) for {} coins", player.getGameProfile().name(), count, cost);
        return 1;
    }

    private int sell(ServerPlayer player, int count) {
        if (!available()) return 0;
        var server = player.level().getServer(); UUID id = player.getUUID(); int bought = bought(id);
        if (count > bought) { player.sendSystemMessage(line("You only bought " + bought + (bought == 1 ? " chunk." : " chunks."), ChatFormatting.RED)); return 0; }
        if (Bridge.limit(server, player) - count < Bridge.claimCount(server, id)) { player.sendSystemMessage(line("Unclaim " + count + (count == 1 ? " chunk" : " chunks") + " first: you are using them.", ChatFormatting.RED)); return 0; }
        long back = refund(config, bought, count);
        data.bought.put(id, bought - count); save(); refresh(player);
        Economy.deposit(server, id, back);
        player.sendSystemMessage(line("Sold " + count + (count == 1 ? " chunk" : " chunks") + " back for " + back + " coins.", ChatFormatting.YELLOW));
        return 1;
    }

    // Separate class so OPAC types load only when the mod is present.
    private static final class Bridge {
        static xaero.pac.common.server.api.OpenPACServerAPI api(MinecraftServer server) { return xaero.pac.common.server.api.OpenPACServerAPI.get(server); }
        static void setBonus(MinecraftServer server, UUID id, int bonus) {
            var config = api(server).getPlayerConfigs().getLoadedConfig(id);
            var option = xaero.pac.common.server.player.config.api.PlayerConfigOptions.BONUS_CHUNK_CLAIMS;
            if (!Objects.equals(config.getFromEffectiveConfig(option), bonus)) config.tryToSet(option, bonus);
        }
        static boolean nearClaim(MinecraftServer server, Identifier dimension, int chunkX, int chunkZ, int radius) {
            var claims = api(server).getServerClaimsManager();
            for (int x = chunkX - radius; x <= chunkX + radius; x++)
                for (int z = chunkZ - radius; z <= chunkZ + radius; z++)
                    if (claims.get(dimension, x, z) != null) return true;
            return false;
        }
        static int claimCount(MinecraftServer server, UUID id) {
            var claims = api(server).getServerClaimsManager();
            return claims.hasPlayerInfo(id) ? claims.getPlayerInfo(id).getClaimCount() : 0;
        }
        static int limit(MinecraftServer server, ServerPlayer player) { return api(server).getServerClaimsManager().getPlayerFullClaimLimit(player); }
        static int baseLimit(MinecraftServer server, ServerPlayer player) { return api(server).getServerClaimsManager().getPlayerBaseClaimLimit(player); }
    }
}
