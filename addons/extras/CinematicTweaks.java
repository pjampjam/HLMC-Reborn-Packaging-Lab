package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ji AFK Cinematic 2.3.2 tweaks (owner 2026-10-10), used by the Cinematic*Mixin classes: the camera keeps clear of blocks on
 * every side, indoors and underground it films you instead of cave walls, and while fishing it frames you and the bobber.
 */
public final class CinematicTweaks {
    private CinematicTweaks() {}
    /** Half size of the space the camera needs: wider than the near plane, so walls never show through. */
    static final double CLEARANCE = 0.3;
    /** Set when the mod starts a fishing cinematic (CinematicManagerMixin). */
    public static volatile boolean fishing;
    /** Waiting for a bite, or the 3 s after reeling in: the camera draws Ji's fishing shot (you and the bobber). */
    public static volatile boolean fishingWait;
    /** The fishing shot's clock while waiting (ticks into the current angle). */
    public static int waitTicks;
    /** Ticks the fishing framing stays after reeling in. */
    public static int afterReel;
    /** Ji's fishing shot could not be driven (mod changed): the plain shot sequence plays instead. */
    public static boolean fishingShotBroken;
    /** Ji's five fishing angles, in an order that alternates sides: front-left low, front-right low, centre, left high, right high. */
    private static final int[] ANGLES = {0, 3, 2, 1, 4};
    private static int angle;

    /** Starts Ji's fishing shot on the bobber and you, with the next of its angles. */
    public static void nextFishingAngle() {
        if (fishingShotBroken) return;
        try {
            var field = Class.forName("com.ji.afkcinematic.cinematic.CameraController").getDeclaredField("FISHING_SHOT");
            field.setAccessible(true);
            Object shot = field.get(null);
            shot.getClass().getMethod("start").invoke(shot);
            var preset = shot.getClass().getDeclaredField("preset");
            preset.setAccessible(true);
            Object[] presets = preset.getType().getEnumConstants();
            preset.set(shot, presets[ANGLES[angle++ % ANGLES.length] % presets.length]);
            waitTicks = 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fishingShotBroken = true;
            org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Fishing camera angles off", error);
        }
    }

    /** The fishing angle in use right now (gametest). */
    public static String fishingAngle() {
        try {
            var field = Class.forName("com.ji.afkcinematic.cinematic.CameraController").getDeclaredField("FISHING_SHOT");
            field.setAccessible(true);
            Object shot = field.get(null);
            var preset = shot.getClass().getDeclaredField("preset");
            preset.setAccessible(true);
            return String.valueOf(preset.get(shot));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { return "none"; }
    }

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
