package holylois.boombox;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * A carried boombox hangs from its handle and swings like a pendulum (owner round 4): it lags behind when you start, stop or
 * turn, sways a little with your steps and settles when you stand. Mostly side to side; simulated per tick, drawn between ticks.
 */
public final class HeldSwing {
    private HeldSwing() {}
    /** Spring (pull back to hanging), damping, and how hard sideways/forward acceleration, turning and steps push it. */
    static final float SPRING = 0.1f, DAMPING = 0.12f, SIDE = 70, FORWARD = 45, TURN = 0.3f, STEP = 0.55f;

    private static final class Pendulum {
        float side, sideSpeed, lastSide, tilt, tiltSpeed, lastTilt, lastYaw;
        double lastVx, lastVz;
        boolean started;
    }
    private static final Map<Integer, Pendulum> SWINGS = new HashMap<>();

    static void register() { ClientTickEvents.END_CLIENT_TICK.register(HeldSwing::tick); }

    static boolean holds(Player player) {
        return player.getMainHandItem().is(Boombox.ITEM) || player.getOffhandItem().is(Boombox.ITEM);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) { SWINGS.clear(); return; }
        if (mc.isPaused()) return;
        var seen = new HashSet<Integer>();
        for (var player : mc.level.players()) {
            if (!holds(player)) continue;
            seen.add(player.getId());
            var p = SWINGS.computeIfAbsent(player.getId(), id -> new Pendulum());
            double vx = player.getX() - player.xo, vz = player.getZ() - player.zo;
            float yaw = player.yBodyRot;
            if (!p.started) { p.started = true; p.lastVx = vx; p.lastVz = vz; p.lastYaw = yaw; }
            double ax = vx - p.lastVx, az = vz - p.lastVz, rad = Math.toRadians(yaw);
            p.lastVx = vx; p.lastVz = vz;
            // Body frame (Minecraft yaw): forward is (-sin, cos), the body's right is (-cos, -sin).
            double forward = -ax * Math.sin(rad) + az * Math.cos(rad), right = -ax * Math.cos(rad) - az * Math.sin(rad);
            float turn = Mth.wrapDegrees(yaw - p.lastYaw); p.lastYaw = yaw;
            float walk = Math.min(1, player.walkAnimation.speed()), phase = player.walkAnimation.position() * 0.6662f;
            p.lastSide = p.side; p.lastTilt = p.tilt;
            p.sideSpeed += -SPRING * p.side - DAMPING * p.sideSpeed - (float) right * SIDE + turn * TURN + Mth.sin(phase) * walk * STEP;
            p.tiltSpeed += -SPRING * p.tilt - DAMPING * p.tiltSpeed - (float) forward * FORWARD;
            p.side = Mth.clamp(p.side + p.sideSpeed, -32, 32);
            p.tilt = Mth.clamp(p.tilt + p.tiltSpeed, -20, 20);
        }
        SWINGS.keySet().retainAll(seen);
    }

    /** Current swing of a player's boombox, degrees (tests). */
    public static float side(int id) { var p = SWINGS.get(id); return p == null ? 0 : p.side; }
    public static float tilt(int id) { var p = SWINGS.get(id); return p == null ? 0 : p.tilt; }

    /** Sets FishLook.swingZ/swingX (degrees) for the boombox about to be drawn in this entity's hand. */
    public static void apply(LivingEntityRenderState state) {
        var p = state instanceof AvatarRenderState avatar ? SWINGS.get(avatar.id) : null;
        if (p == null) { FishLook.swingZ = 0; FishLook.swingX = 0; return; }
        float t = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        FishLook.swingZ = Mth.lerp(t, p.lastSide, p.side);
        FishLook.swingX = Mth.lerp(t, p.lastTilt, p.tilt);
    }
}
