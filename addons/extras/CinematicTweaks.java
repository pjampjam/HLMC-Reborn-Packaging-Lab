package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ji AFK Cinematic 2.3.2 tweaks (owner 2026-10-10), used by the Cinematic*Mixin classes: the camera keeps clear of blocks on
 * every side, indoors and underground it films you instead of cave walls, and while fishing it stays on you until you reel in.
 */
public final class CinematicTweaks {
    private CinematicTweaks() {}
    /** Half size of the space the camera needs: wider than the near plane, so walls never show through. */
    static final double CLEARANCE = 0.3;
    /** Set when the mod starts a fishing cinematic (CinematicManagerMixin). */
    public static volatile boolean fishing;

    /** Moves a camera spot towards the anchor until a small box around it is free of blocks. */
    public static Vec3 clear(Vec3 anchor, Vec3 spot) {
        var level = Minecraft.getInstance().level;
        if (level == null || anchor == null || spot == null) return spot;
        Vec3 at = spot;
        for (int step = 0; step < 12; step++) {
            if (level.noCollision(new AABB(at.x - CLEARANCE, at.y - CLEARANCE, at.z - CLEARANCE, at.x + CLEARANCE, at.y + CLEARANCE, at.z + CLEARANCE))) return at;
            at = at.lerp(anchor, 0.2);
        }
        return at;
    }

    /** No sky above and little daylight reaching you: a cave, a mine or a house. Wide scenery shots would only show walls. */
    public static boolean enclosed() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        var pos = BlockPos.containing(mc.player.getEyePosition());
        return !mc.level.canSeeSky(pos) && mc.level.getBrightness(LightLayer.SKY, pos) <= 7;
    }

    /** Character shots only (100 %) while fishing or enclosed; otherwise the player's own setting. */
    public static int characterPercentage(int configured) { return fishing || enclosed() ? 100 : configured; }

    /** Ji's CinematicManager.getState(), looked up once; null when the mod is missing or changed. */
    private static java.lang.reflect.Method state;
    private static boolean stateMissing;

    /** The cinematic camera is running right now (Ji AFK Cinematic state CINEMATIC_ACTIVE). */
    public static boolean playing() {
        if (stateMissing) return false;
        try {
            if (state == null) state = Class.forName("com.ji.afkcinematic.cinematic.CinematicManager").getMethod("getState");
            return "CINEMATIC_ACTIVE".equals(String.valueOf(state.invoke(null)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            stateMissing = true;
            return false;
        }
    }

    /** The local player's bobber is out (cast, waiting or a fish on it). */
    public static boolean hookOut() {
        var player = Minecraft.getInstance().player;
        return player != null && player.fishing != null;
    }
}
