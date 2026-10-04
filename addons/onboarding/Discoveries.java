package holylois;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Announces the first time anyone walks into a notable structure. Villages and small ruins stay quiet. */
public final class Discoveries {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLois");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Found { public String key, structure, name, player; public long time; }
    public static final class State { public int version = 1; public List<Found> found = new ArrayList<>(); }

    private static final Map<String, String> VANILLA = Map.ofEntries(
        Map.entry("ancient_city", "Ancient City"), Map.entry("trial_chambers", "Trial Chambers"),
        Map.entry("stronghold", "Stronghold"), Map.entry("mansion", "Woodland Mansion"),
        Map.entry("monument", "Ocean Monument"), Map.entry("fortress", "Nether Fortress"),
        Map.entry("bastion_remnant", "Bastion"), Map.entry("end_city", "End City"),
        Map.entry("pillager_outpost", "Pillager Outpost"), Map.entry("desert_pyramid", "Desert Temple"),
        Map.entry("jungle_pyramid", "Jungle Temple"), Map.entry("trail_ruins", "Trail Ruins"));
    // Small, very common or village-like modded structures. Anything else from a structure mod is announced.
    private static final Pattern QUIET = Pattern.compile(
        "(village|hamlet|^well_|camp|small_|firewatch|wreckage|mimic|cave_hut|underground_house|wild_ruin|witch_hut"
        + "|desert_ruins|jungle_ruins|^remnant_(?!taiga_castle|big_remnant))");

    // Small crypts count as dungeons even though other small_ structures stay quiet.
    private static final Pattern CRYPTS = Pattern.compile("^small_(creeping|undead)_crypt$");

    private final Map<String, Found> found = new HashMap<>();
    private Path file;

    /** Display name for a structure id, or null when it should not be announced. */
    static String name(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon), path = id.substring(colon + 1);
        if (namespace.equals("minecraft")) return VANILLA.get(path);
        path = path.substring(path.lastIndexOf('/') + 1);
        // Epic Dungeons are underground dungeons of every size: all of them are worth an announcement (small_ would hide them).
        if (namespace.equals("epic")) return path.equals("large_dungeon") ? "Large Plains Dungeon" : title(path);
        if (CRYPTS.matcher(path).find()) return title(path);
        if (QUIET.matcher(path).find()) return null;
        if (path.startsWith("tavern_")) return "Tavern";
        if (path.startsWith("pillager_outpost")) return "Pillager Outpost";
        if (path.startsWith("nether_skeleton_tower")) return "Skeleton Tower";
        if (path.contains("trial_dungeon")) return "Trial Dungeon";
        if (path.startsWith("surface_dungeon")) return "Surface Dungeon";
        if (path.startsWith("shrine_combat")) return "Combat Shrine";
        if (path.startsWith("shrine_biome")) return "Biome Shrine";
        if (path.startsWith("cave_chamber")) return "Cave Chamber";
        if (path.startsWith("remnant_big_remnant")) return "Big Ruin";
        if (path.equals("remnant_taiga_castle")) return "Ruined Taiga Castle";
        return title(path.replaceAll("_\\d+$", ""));
    }

    static String title(String path) {
        var out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    static String article(String name) { return "AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "an" : "a"; }

    void load(MinecraftServer server, Path worldRoot) {
        file = worldRoot.resolve("holylois/discoveries.json");
        try {
            if (Files.exists(file)) {
                var state = JSON.fromJson(Files.readString(file), State.class);
                if (state != null && state.found != null) for (var entry : state.found) found.put(entry.key, entry);
            }
            LOG.info("Holy Lois discoveries: {} structures found so far", found.size());
        } catch (Exception error) { LOG.warn("Cannot read discoveries; starting a new list in memory", error); }
    }

    /** Called every two seconds for each authenticated player. */
    void check(MinecraftServer server, ServerPlayer player) {
        if (file == null || player.isSpectator()) return;
        var level = player.level();
        var manager = level.structureManager();
        var pos = player.blockPosition();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var structure : manager.getAllStructuresAt(pos).keySet()) {
            var id = registry.getKey(structure);
            if (id == null) continue;
            String name = name(id.toString());
            if (name == null) continue;
            var start = manager.getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), structure);
            if (start == null || !start.isValid()) continue;
            String key = level.dimension().identifier() + "|" + id + "|" + start.getChunkPos().x() + "," + start.getChunkPos().z();
            if (found.containsKey(key)) continue;
            var entry = new Found();
            entry.key = key; entry.structure = id.toString(); entry.name = name;
            entry.player = player.getGameProfile().name(); entry.time = System.currentTimeMillis() / 1000;
            found.put(key, entry);
            save();
            String where = level.dimension() == Level.NETHER ? " in the Nether" : level.dimension() == Level.END ? " in the End" : "";
            server.getPlayerList().broadcastSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(entry.player).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" discovered " + article(name) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(where + "!").withStyle(ChatFormatting.GRAY)), false);
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
            Achievements.award(server, player, "explore/discoverer", "done");
            LOG.info("Discovery: {} found {} ({}) at {}", entry.player, name, id, start.getChunkPos());
        }
    }

    /** /structures: which structures the player is standing in, with their ids, so new ones can be named or announced. */
    static int here(ServerPlayer player) {
        var level = player.level();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var pos = player.blockPosition();
        var text = Component.literal("Structures here: ").withStyle(ChatFormatting.GOLD);
        int count = 0;
        for (var structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            var start = level.structureManager().getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), structure);
            var id = registry.getKey(structure);
            if (id == null || start == null || !start.isValid()) continue;
            String name = name(id.toString());
            text.append(Component.literal((count++ == 0 ? "" : ", ") + (name == null ? id + " (not announced)" : name + " (" + id + ")"))
                .withStyle(ChatFormatting.YELLOW));
        }
        if (count == 0) text.append(Component.literal("none").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(text);
        return count;
    }

    private void save() {
        try {
            var state = new State();
            state.found.addAll(found.values());
            state.found.sort(Comparator.comparingLong(f -> f.time));
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling("discoveries.json.tmp");
            Files.writeString(temporary, JSON.toJson(state));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { LOG.warn("Cannot save discoveries", error); }
    }
}
