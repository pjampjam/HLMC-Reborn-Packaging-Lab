package holylois.boombox;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fresh Animations (FA+ Objects) runs its chest lid animation on frame time, and EMF only animates a chest while it is drawn.
 * Close a chest and look away before the half-second close finishes: the animation pauses and plays when you look back
 * ("the chest closes again"). When a shut chest comes back into view after being unseen, its EMF animation values are reset.
 */
public final class ChestReplay {
    private ChestReplay() {}
    private static final boolean EMF = FabricLoader.getInstance().isModLoaded("entity_model_features");
    private static final Map<BlockEntity, Long> lastDrawn = new WeakHashMap<>();
    private static boolean broken;

    public static void seen(BlockEntity chest, float partial) {
        if (!EMF || broken) return;
        long now = Util.getMillis();
        Long last = lastDrawn.put(chest, now);
        if (last == null || now - last < 250 || !(chest instanceof LidBlockEntity lid) || lid.getOpenNess(partial) > 0) return;
        try { Emf.reset(chest); }
        catch (RuntimeException | LinkageError error) { broken = true; org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Chest animation reset off", error); }
    }

    // Separate class so EMF types load only when the mod is present.
    private static final class Emf {
        static void reset(BlockEntity chest) {
            if (chest instanceof traben.entity_model_features.utils.EMFEntity entity) entity.emf$getVariableMap().clear();
        }
    }
}
