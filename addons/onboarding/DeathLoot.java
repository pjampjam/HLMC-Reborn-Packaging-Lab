package holylois;

import holylois.boombox.DeathLootGone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Death drops: tagged right after a player's inventory drops (DeathDropsMixin). Normal deaths keep the drops 30 minutes
 * and owner-only for the first 5; PvP deaths and combat logouts leave them unlocked for whoever wins the fight.
 * Normal deaths are also tracked until every drop is really gone (picked up, despawned, burned; a chunk unload does not
 * count), then the owner's client removes its minimap death marker there (holylois:death_loot_gone).
 */
public final class DeathLoot {
    private DeathLoot() {}
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String DROP_TAG = "holylois:death_drop", GRACE_TAG = "holylois:death_grace";
    public static final int OWNER_ONLY_TICKS = 6000;
    static final long KEEP_ACTIVE_MS = 30L * 24 * 3600 * 1000, KEEP_GONE_MS = 7L * 24 * 3600 * 1000;

    static final class Death {
        public String owner, dimension; public int x, y, z; public long time; public Set<String> items = new HashSet<>();
    }
    static final class State { public int version = 1; public List<Death> active = new ArrayList<>(), gone = new ArrayList<>(); }

    /** Which death each live drop entity still carries loot from; merges can put several deaths into one stack. */
    static final class Tracker {
        State state = new State();
        final Map<String, List<Death>> byItem = new HashMap<>();
        boolean dirty;

        void index() {
            byItem.clear();
            for (Death death : state.active) for (String item : death.items) byItem.computeIfAbsent(item, key -> new ArrayList<>()).add(death);
        }
        void track(UUID owner, String dimension, int x, int y, int z, long time, List<UUID> items) {
            if (items.isEmpty()) return;
            var death = new Death();
            death.owner = owner.toString(); death.dimension = dimension; death.x = x; death.y = y; death.z = z; death.time = time;
            for (UUID item : items) death.items.add(item.toString());
            state.active.add(death);
            for (String item : death.items) byItem.computeIfAbsent(item, key -> new ArrayList<>()).add(death);
            dirty = true;
        }
        /** Vanilla merged source into target: target now carries source's death loot too. */
        void merged(UUID source, UUID target) {
            var deaths = byItem.get(source.toString());
            if (deaths == null || source.equals(target)) return;
            for (Death death : List.copyOf(deaths))
                if (death.items.add(target.toString())) { byItem.computeIfAbsent(target.toString(), key -> new ArrayList<>()).add(death); dirty = true; }
        }
        /** A drop entity was destroyed. Deaths with nothing left move to gone (unless another live death of that owner lies right there). */
        void removed(UUID item) {
            var deaths = byItem.remove(item.toString());
            if (deaths == null) return;
            dirty = true;
            for (Death death : deaths) {
                death.items.remove(item.toString());
                if (!death.items.isEmpty()) continue;
                state.active.remove(death);
                boolean shared = state.active.stream().anyMatch(other -> other.owner.equals(death.owner) && other.dimension.equals(death.dimension)
                    && Math.abs(other.x - death.x) <= 4 && Math.abs(other.z - death.z) <= 4 && Math.abs(other.y - death.y) <= 4);
                if (!shared) state.gone.add(death);
            }
        }
        /** Forgets deaths whose drops sit in chunks nobody visits, and reports nobody collected for a week. */
        void prune(long now) {
            if (state.active.removeIf(death -> now - death.time > KEEP_ACTIVE_MS) | state.gone.removeIf(death -> now - death.time > KEEP_GONE_MS)) { index(); dirty = true; }
        }
    }

    static final Tracker TRACKER = new Tracker();
    private static Path file;

    public static void markDrops(ServerLevel level, ServerPlayer player) {
        try {
            boolean locked = !CombatTag.diedInPvp(player);
            var items = new ArrayList<UUID>();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3),
                    entity -> entity.tickCount == 0 && !entity.entityTags().contains(DROP_TAG))) {
                item.addTag(DROP_TAG);
                items.add(item.getUUID());
                if (locked) item.setTarget(player.getUUID());
            }
            // PvP deaths get no minimap marker (extras PvpDeath), so there is nothing to clean up later.
            BlockPos pos = player.blockPosition();
            if (locked) TRACKER.track(player.getUUID(), level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis(), items);
        } catch (RuntimeException error) {
            // Never let this stop a death: the drops simply stay vanilla.
            LOG.error("Holy Lois death drops failed", error);
        }
    }

    public static void merged(ItemEntity source, ItemEntity target) {
        if (source.entityTags().contains(DROP_TAG)) TRACKER.merged(source.getUUID(), target.getUUID());
    }

    static void unloaded(Entity entity) {
        var reason = entity.getRemovalReason();
        if (entity instanceof ItemEntity && reason != null && reason.shouldDestroy()) TRACKER.removed(entity.getUUID());
    }

    static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("holylois/death-loot.json");
        try {
            if (Files.exists(file)) {
                var loaded = JSON.fromJson(Files.readString(file), State.class);
                if (loaded != null && loaded.active != null && loaded.gone != null) TRACKER.state = loaded;
            }
        } catch (Exception error) { LOG.warn("Cannot read death loot markers; starting empty", error); }
        TRACKER.index();
    }

    /** Every second: tell owners (5 s after they joined or respawned) which death markers can go, and save changes. */
    static void tick(MinecraftServer server) {
        var tracker = TRACKER;
        if (server.getTickCount() % 1200 == 0) tracker.prune(System.currentTimeMillis());
        tracker.state.gone.removeIf(death -> {
            var owner = server.getPlayerList().getPlayer(UUID.fromString(death.owner));
            if (owner == null || owner.tickCount < 100) return false;
            DeathLootGone.send(owner, Identifier.parse(death.dimension), new BlockPos(death.x, death.y, death.z));
            tracker.dirty = true;
            return true;
        });
        if (tracker.dirty) save();
    }

    static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling("death-loot.json.tmp");
            Files.writeString(temporary, JSON.toJson(TRACKER.state));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            TRACKER.dirty = false;
        } catch (Exception error) { LOG.warn("Cannot save death loot markers", error); }
    }
}
