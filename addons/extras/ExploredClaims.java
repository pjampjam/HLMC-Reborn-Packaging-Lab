package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.pac.client.api.OpenPACClientAPI;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Conceal other claim overlays until their terrain is present in the player's map cache. */
public final class ExploredClaims {
    private record Key(ResourceKey<Level> dimension, int x, int z) {}
    private record Result(boolean visible, long until) {}
    private static final Map<Key, Result> CACHE = new ConcurrentHashMap<>();
    private static volatile Object lastLevel;
    private static volatile boolean warned;
    public static boolean visible(ResourceKey<Level> dimension, int x, int z) {
        var client = Minecraft.getInstance();
        var player = client.player; var level = client.level;
        if (player == null || level == null) return false;
        var claim = OpenPACClientAPI.get().getClaimsManager().get(dimension.identifier(), x, z);
        if (claim == null) return false;
        if (claim.getPlayerId().equals(player.getUUID())) return true;
        if (lastLevel != level) { CACHE.clear(); lastLevel = level; }
        long now = System.currentTimeMillis();
        var key = new Key(dimension, x, z);
        var cached = CACHE.get(key);
        if (cached != null && cached.until() > now) return cached.visible();
        boolean known = false;
        try { known = Bridge.known(dimension, x, z); }
        catch (ReflectiveOperationException | LinkageError error) {
            if (!warned) { warned = true; org.slf4j.LoggerFactory.getLogger("HolyLoisExtras").warn("Cannot inspect explored claim terrain; undiscovered overlays stay hidden", error); }
        }
        if (CACHE.size() > 8192) CACHE.clear();
        CACHE.put(key, new Result(known, now + 500));
        return known;
    }
    private static final class Bridge {
        private static Method method(String type, String name, Class<?>... args) throws ReflectiveOperationException {
            return Class.forName(type).getMethod(name, args);
        }
        private static final Method SESSION, PROCESSOR, WORLD, DIMENSION, REGIONS, CAVE, LEAF, CHUNK, TILE, BLOCK;
        static {
            try {
                SESSION = method("xaero.map.WorldMapSession", "getCurrentSession");
                PROCESSOR = method("xaero.map.WorldMapSession", "getMapProcessor");
                WORLD = method("xaero.map.MapProcessor", "getMapWorld");
                DIMENSION = method("xaero.map.world.MapWorld", "getDimension", ResourceKey.class);
                REGIONS = method("xaero.map.world.MapDimension", "getLayeredMapRegions");
                CAVE = method("xaero.map.MapProcessor", "getCurrentCaveLayer");
                LEAF = method("xaero.map.region.LayeredRegionManager", "getLeaf", int.class, int.class, int.class);
                CHUNK = method("xaero.map.region.MapRegion", "getChunk", int.class, int.class);
                TILE = method("xaero.map.region.MapTileChunk", "getTile", int.class, int.class);
                BLOCK = method("xaero.map.region.MapTile", "getBlock", int.class, int.class);
            } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
        static boolean known(ResourceKey<Level> dimension, int x, int z) throws ReflectiveOperationException {
            var session = SESSION.invoke(null); if (session == null) return false;
            var processor = PROCESSOR.invoke(session);
            var world = WORLD.invoke(processor);
            var dim = DIMENSION.invoke(world, dimension); if (dim == null) return false;
            var regions = REGIONS.invoke(dim);
            var region = LEAF.invoke(regions, CAVE.invoke(processor), x >> 5, z >> 5); if (region == null) return false;
            var chunk = CHUNK.invoke(region, (x >> 2) & 7, (z >> 2) & 7); if (chunk == null) return false;
            var tile = TILE.invoke(chunk, x & 3, z & 3);
            return tile != null && BLOCK.invoke(tile, 8, 8) != null;
        }
    }
}
