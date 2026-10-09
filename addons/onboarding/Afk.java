package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;

/**
 * AFK time does not count as playing. Essential Commands decides who is AFK (its own timer or /afk); this ledger adds one second per
 * AFK second to world/holylois/afk.json, from the release that introduced it onward (earlier time is left as it was). Playtime used for
 * achievements, land claims, leaderboards, stats and the Tab list is the vanilla play_time minus this ledger.
 */
final class Afk {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final class State { public int version = 1; public Map<String, Long> seconds = new HashMap<>(); }

    private static State state = new State();
    private static Path file;
    private static boolean dirty;

    private Afk() {}

    static boolean available() { return FabricLoader.getInstance().isModLoaded("essential_commands"); }

    static void load(Path worldRoot) {
        file = worldRoot.resolve("holylois/afk.json");
        try {
            if (Files.exists(file)) {
                var loaded = JSON.fromJson(Files.readString(file), State.class);
                if (loaded != null && loaded.seconds != null) state = loaded;
            }
        } catch (Exception error) { LOG.warn("Cannot read the AFK ledger; starting empty in memory", error); }
        LOG.info("Holy Lois AFK ledger: {} ({} players so far)", available() ? "counting" : "waiting for Essential Commands", state.seconds.size());
    }

    /** Once per second. */
    static void tick(MinecraftServer server) {
        if (file == null || !available()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (Bridge.isAfk(player)) { state.seconds.merge(player.getUUID().toString(), 1L, Long::sum); dirty = true; }
        if (dirty && server.getTickCount() % 1200 == 0) save();
    }

    /** Is the player AFK right now? False when Essential Commands is missing. */
    static boolean afkNow(ServerPlayer player) { return available() && Bridge.isAfk(player); }

    static synchronized long seconds(UUID id) { return state.seconds.getOrDefault(id.toString(), 0L); }

    /** Vanilla play_time ticks minus AFK time, never below zero. */
    static long effectiveTicks(UUID id, long vanillaTicks) { return effective(vanillaTicks, seconds(id)); }
    static long effective(long vanillaTicks, long afkSeconds) { return Math.max(0, vanillaTicks - afkSeconds * 20); }

    static synchronized void save() {
        if (file == null || !dirty) return;
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling("afk.json.tmp");
            Files.writeString(temporary, JSON.toJson(state));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (Exception error) { LOG.warn("Cannot save the AFK ledger", error); }
    }

    // Separate class so Essential Commands types load only when the mod is present.
    private static final class Bridge {
        static boolean isAfk(ServerPlayer player) {
            try {
                var data = ((com.fibermc.essentialcommands.access.ServerPlayerEntityAccess) player).ec$getPlayerData();
                return data != null && data.isAfk();
            } catch (RuntimeException | LinkageError error) { return false; }
        }
    }
}
