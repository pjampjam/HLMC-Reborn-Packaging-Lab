package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import holylois.auth.AuthStatus;
import holylois.auth.AuthPolicy;
import xyz.nikitacartes.easyauth.EasyAuth;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import xyz.nikitacartes.easyauth.interfaces.PlayerAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;

/** Server-only onboarding for the installed Minecraft 26.3 / EasyAuth 3.4.4. */
public final class HolyLois implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private State state;
    private Path stateFile;
    private int rtpRadius = 1800;
    private long rulesStamp = -1;
    private final Path rulesFile = Path.of("config", "holylois-server.json");
    public static final class Rules { public int rtpRadius = 1800; }
    private final Map<UUID,Integer> authenticatedSamples = new HashMap<>();
    private final Map<UUID,Integer> failures = new HashMap<>();
    private final Set<UUID> welcomed = new HashSet<>();
    private final Set<UUID> randomRespawns = new HashSet<>();
    private final Map<UUID,Integer> placingUntil = new HashMap<>();
    private final Map<UUID,Integer> protectedUntil = new HashMap<>();
    private final Map<UUID,Integer> sentMode = new HashMap<>();
    private final Discoveries discoveries = new Discoveries();
    private final DailyRewards daily = new DailyRewards();
    private final ServerEvents events = new ServerEvents();
    public static final class State {
        public int version = 1;
        public Set<UUID> completed = new HashSet<>();
        public Set<UUID> pending = new HashSet<>();
    }
    @Override public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(AuthStatus.TYPE, AuthStatus.CODEC);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("placeholder-api")) TabPlaceholders.register();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> BotWallCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(this::load);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
            player instanceof net.minecraft.server.level.ServerPlayer sp && openLootbox(sp, sp.getItemInHand(hand))
                ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS);
        // A lootbox is a present block item: open it instead of placing it.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
            player instanceof net.minecraft.server.level.ServerPlayer sp && openLootbox(sp, sp.getItemInHand(hand))
                ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive) -> {
            // Vanilla clears the new player's spawn config if their bed/anchor is unusable.
            if (AuthPolicy.randomRespawn(alive, newPlayer.getRespawnConfig() != null))
                randomRespawns.add(newPlayer.getUUID());
            if (!alive) deathNotice(oldPlayer, newPlayer);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                UUID id = player.getUUID();
                if (state != null && state.pending.contains(id) && failures.getOrDefault(id,0) < 3) return false;
                if (randomRespawns.contains(id)) return false;
                return player.level().getServer().getTickCount() >= protectedUntil.getOrDefault(id,0);
            }
            return true;
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            var id = handler.player.getUUID();
            if (!state.completed.contains(id) && state.pending.add(id)) save();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> {
            authenticatedSamples.remove(handler.player.getUUID());
            failures.remove(handler.player.getUUID());
            welcomed.remove(handler.player.getUUID());
            randomRespawns.remove(handler.player.getUUID());
            placingUntil.remove(handler.player.getUUID());
            protectedUntil.remove(handler.player.getUUID());
            sentMode.remove(handler.player.getUUID());
            Leaderboards.forget(handler.player.getUUID());
        });
        ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server,message,overlay) ->
            !(message.getContents() instanceof TranslatableContents t
                && t.getKey().startsWith("multiplayer.player.joined")));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerTickEvents.END_SERVER_TICK.register(this::interfaceTick);
    }
    private void load(MinecraftServer server) {
        loadRules();
        LOG.info("Holy Lois name day: {}", NameDays.join(NameDays.today()));
        Leaderboards.refreshAsync();
        stateFile = server.getWorldPath(LevelResource.ROOT).resolve("holylois/onboarding.json");
        discoveries.load(server, server.getWorldPath(LevelResource.ROOT));
        daily.load(server.getWorldPath(LevelResource.ROOT));
        events.load(server.getWorldPath(LevelResource.ROOT));
        try {
            if (Files.exists(stateFile)) {
                state = JSON.fromJson(Files.readString(stateFile),State.class);
                if (state == null || state.version != 1 || state.completed == null || state.pending == null)
                    throw new IllegalStateException("Invalid onboarding state; refusing to reset returning players.");
            } else {
                state = new State();
                var playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
                if (Files.isDirectory(playerData)) try (var files = Files.list(playerData)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".dat")).forEach(p -> {
                        String name = p.getFileName().toString();
                        try {state.completed.add(UUID.fromString(name.substring(0,name.length()-4)));}
                        catch (IllegalArgumentException ignored) {}
                    });
                }
                save();
            }
            LOG.info("First-join RTP ready: {} returning players protected, {} pending. Radius {} around 0,0.",state.completed.size(),state.pending.size(),rtpRadius);
        } catch (Exception e) {throw new IllegalStateException("Cannot load Holy Lois onboarding state",e);}
    }
    private void loadRules() {
        try {
            if (!Files.exists(rulesFile)) {
                Files.createDirectories(rulesFile.getParent());
                Files.writeString(rulesFile, JSON.toJson(new Rules()), StandardOpenOption.CREATE_NEW);
            }
            long stamp = Files.getLastModifiedTime(rulesFile).toMillis();
            if (stamp == rulesStamp) return;
            Rules rules = JSON.fromJson(Files.readString(rulesFile), Rules.class);
            if (rules == null || rules.rtpRadius < 250 || rules.rtpRadius > 10000)
                throw new IllegalArgumentException("RTP radius must be between 250 and 10000 blocks.");
            rtpRadius = rules.rtpRadius; rulesStamp = stamp;
            LOG.info("Holy Lois random placement radius: {} blocks", rtpRadius);
        } catch (Exception error) { LOG.warn("Keeping the last valid random placement radius", error); }
    }
    private void save() {
        try {
            Files.createDirectories(stateFile.getParent());
            var temporary = stateFile.resolveSibling("onboarding.json.tmp");
            Files.writeString(temporary,JSON.toJson(state),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary,stateFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {throw new IllegalStateException("Cannot persist Holy Lois onboarding state",e);}
    }
    static boolean ready(boolean pending, boolean authenticated, int samples, int failedAttempts) {
        return pending && authenticated && samples >= 3 && failedAttempts < 3;
    }
    private void tick(MinecraftServer server) {
        if (state == null || server.getTickCount()%20 != 0) return;
        if (server.getTickCount()%200 == 0) loadRules();
        if (server.getTickCount()%2400 == 0) Leaderboards.refreshAsync();
        safely("events", () -> events.tick(server));
        for (var player : server.getPlayerList().getPlayers()) {
            var id = player.getUUID();
            boolean auth = ((PlayerAuth)player).easyAuth$isAuthenticated();
            int samples = auth ? authenticatedSamples.merge(id,1,Integer::sum) : 0;
            if (!auth) authenticatedSamples.remove(id);
            if (!state.pending.contains(id)) {
                if (auth && samples >= 3) welcome(player,false);
                if (auth && samples >= 3 && server.getTickCount()%40 == 0) safely("discoveries", () -> discoveries.check(server,player));
                if (auth && samples >= 3 && server.getTickCount()%100 == 0) safely("achievements", () -> Achievements.check(server,player,server.getTickCount()));
                continue;
            }
            if (!ready(state.pending.contains(id),auth,samples,failures.getOrDefault(id,0))) continue;
            // Never move a returning character or teleport before EasyAuth restores their real location.
            if (player.level().dimension() != Level.OVERWORLD) continue;
            String name = player.getGameProfile().name();
            if (!name.matches("[A-Za-z0-9_]{3,16}")) continue;
            try {
                // Vanilla spreadplayers finds a safe surface, rejecting fire and liquid.
                // The maintenance job expands this radius only after disk verification.
                int result = server.getCommands().getDispatcher().execute(
                    "spreadplayers 0 0 0 " + rtpRadius + " false " + name,
                    server.createCommandSourceStack().withLevel(server.overworld()).withSuppressedOutput());
                if (result < 1) throw new IllegalStateException("spreadplayers did not teleport the player");
                state.pending.remove(id);
                state.completed.add(id);
                arrivalProtection(server,id);
                save();
                welcome(player,true);
                LOG.info("First-join RTP completed for {} at {},{},{}",name,player.getBlockX(),player.getBlockY(),player.getBlockZ());
            } catch (Exception e) {
                int count = failures.merge(id,1,Integer::sum);
                LOG.warn("First-join RTP attempt {} failed for {}",count,name,e);
                if (count >= 3) player.sendSystemMessage(Component.literal("Automatic placement failed. You can use /rtp; contact pjampjam if it keeps failing."));
            }
        }
    }
    private void arrivalProtection(MinecraftServer server, UUID id) {
        placingUntil.put(id,server.getTickCount()+30);
        protectedUntil.put(id,server.getTickCount()+100);
    }
    private void interfaceTick(MinecraftServer server) {
        if (state == null) return;
        int now = server.getTickCount();
        for (var player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            var auth = (PlayerAuth)player;
            boolean authenticated = auth.easyAuth$isAuthenticated();
            if (authenticated && randomRespawns.contains(id)) {
                try {
                    int result = server.getCommands().getDispatcher().execute(
                        "spreadplayers 0 0 0 " + rtpRadius + " false " + player.getGameProfile().name(),
                        server.createCommandSourceStack().withLevel(server.overworld()).withSuppressedOutput());
                    if (result < 1) throw new IllegalStateException("No safe respawn location");
                    randomRespawns.remove(id);
                    arrivalProtection(server,id);
                } catch (Exception error) {
                    randomRespawns.remove(id);
                    player.sendSystemMessage(Component.literal("Random respawn failed. Use /rtp or contact pjampjam."));
                    LOG.warn("Random respawn failed for {}",player.getGameProfile().name(),error);
                }
            }
            var entry = auth.easyAuth$getPlayerEntryV1();
            boolean registered = entry != null && !entry.password.isEmpty();
            boolean placing = (state.pending.contains(id) && failures.getOrDefault(id,0) < 3)
                || randomRespawns.contains(id) || now < placingUntil.getOrDefault(id,0);
            int mode = AuthPolicy.mode(authenticated,registered,placing);
            if (ServerPlayNetworking.canSend(player,AuthStatus.TYPE)
                && (sentMode.getOrDefault(id,-1) != mode || now % 20 == 0)) {
                ServerPlayNetworking.send(player,new AuthStatus(mode,(int)EasyAuth.extendedConfig.minPasswordLength));
                sentMode.put(id,mode);
            }
        }
        protectedUntil.entrySet().removeIf(entry -> now >= entry.getValue());
        placingUntil.entrySet().removeIf(entry -> now >= entry.getValue());
    }
    static String deathText(int x, int y, int z, String dimension) {
        return "You died at " + x + ", " + y + ", " + z + dimension + ".";
    }
    private void deathNotice(net.minecraft.server.level.ServerPlayer oldPlayer, net.minecraft.server.level.ServerPlayer newPlayer) {
        var level = oldPlayer.level();
        String dimension = level.dimension() == Level.NETHER ? " in the Nether" : level.dimension() == Level.END ? " in the End" : " in the Overworld";
        var pos = oldPlayer.blockPosition();
        var message = Component.literal("☠ ").withStyle(ChatFormatting.RED)
            .append(Component.literal(deathText(pos.getX(), pos.getY(), pos.getZ(), dimension)).withStyle(ChatFormatting.WHITE));
        if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY))
            message.append(Component.literal("\nYour drops are protected for 30 minutes while the area is loaded. The death point on your minimap disappears when you get there.")
                .withStyle(ChatFormatting.GRAY));
        newPlayer.sendSystemMessage(message);
    }
    static String greeting(boolean firstJoin, String name) {
        return firstJoin ? "Welcome to Holy Lois: Reborn, " + name + "!" : "Welcome back, " + name + "!";
    }
    private void welcome(net.minecraft.server.level.ServerPlayer player, boolean firstJoin) {
        if (player.level().getServer().getTickCount() < placingUntil.getOrDefault(player.getUUID(),0)) return;
        if (!welcomed.add(player.getUUID())) return;
        int online = player.level().getServer().getPlayerList().getPlayerCount();
        var message = Component.literal(greeting(firstJoin, player.getGameProfile().name()))
            .withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD);
        var nameDay = NameDays.today();
        var own = NameDays.celebrating(nameDay, player.getGameProfile().name());
        if (own.isPresent()) message.append(Component.literal("\nDaudz laimes vārda dienā, " + own.get() + "! Happy name day!")
            .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withItalic(false)));
        else if (!nameDay.isEmpty()) message.append(Component.literal("\nToday is the Latvian name day of " + NameDays.join(nameDay) + ".")
            .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(false).withItalic(false)));
        if (online > 1) message.append(Component.literal("\n" + (online - 1) + (online == 2 ? " friend is" : " friends are") + " online. Hold Tab to see who.")
            .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(false)));
        message.append(Component.literal("\n/home set base | /rtp | /tpa NAME | /ah auction house | /daily coins")
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(false)))
            .append(Component.literal("\nV: voice | Caps Lock: talk | Tab: server stats")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(false)));
        player.sendSystemMessage(message);
        var server = player.level().getServer();
        safely("daily gifts", () -> { int streak = daily.claim(server, player); if (streak > 0) Achievements.streak(server, player, streak); });
        safely("holiday events", () -> events.welcome(server, player));
    }
    // Extra features must never stop the server tick: a failure is logged once a minute and the game goes on.
    private final Map<String,Long> lastFailure = new HashMap<>();
    private void safely(String feature, Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) {
            long now = System.currentTimeMillis();
            if (now - lastFailure.getOrDefault(feature, 0L) > 60_000) { lastFailure.put(feature, now); LOG.error("Holy Lois {} failed; the server keeps running", feature, error); }
        }
    }
    private boolean openLootbox(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (DailyRewards.lootboxTier(stack) <= 0) return false;
        var server = player.level().getServer();
        if (daily.open(server, player, stack)) Achievements.award(server, player, "daily/unboxed", "done");
        return true;
    }
}
