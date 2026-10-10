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

    /**
     * Owner 2026-10-10: open a chest, get teleported away while the chunk stays loaded, come back: the lid is still open, because
     * the server's "lid closed" event only reaches players near the chest. The chest you opened is remembered; once your container
     * has closed and nobody else stands close enough to be using it, its lid is closed on your screen (visual only).
     */
    private static net.minecraft.core.BlockPos opened;
    /** The chest's screen really opened after the click (the server answers a few ticks later). */
    private static boolean armed;
    private static boolean menuWasOpen;
    private static long closedAt, usedAt;

    static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && level.getBlockEntity(hit.getBlockPos()) instanceof LidBlockEntity) { opened = hit.getBlockPos().immutable(); armed = false; usedAt = Util.getMillis(); }
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ChestReplay::tick);
    }

    private static void tick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || mc.level == null) { opened = null; armed = false; menuWasOpen = false; return; }
        boolean open = mc.player.containerMenu != mc.player.inventoryMenu;
        if (menuWasOpen && !open) closedAt = Util.getMillis();
        if (open && opened != null) armed = true;
        menuWasOpen = open;
        if (opened != null && !armed && Util.getMillis() - usedAt > 5000) opened = null;  // the chest never opened (locked, blocked)
        if (opened == null || !armed || open || Util.getMillis() - closedAt < 1000) return;
        if (!(mc.level.getBlockEntity(opened) instanceof LidBlockEntity)) return;  // not loaded: try again when it is
        var pos = opened; opened = null; armed = false;
        boolean someoneThere = mc.level.players().stream().anyMatch(other -> other != mc.player && other.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 36);
        if (someoneThere) return;
        var state = mc.level.getBlockState(pos);
        var halves = new java.util.ArrayList<net.minecraft.core.BlockPos>(java.util.List.of(pos));
        if (state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE) && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
            halves.add(pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)));
        for (var half : halves)
            if (mc.level.getBlockEntity(half) instanceof LidBlockEntity lid && lid.getOpenNess(1) > 0 && lid instanceof BlockEntity entity) entity.triggerEvent(1, 0);
    }

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
