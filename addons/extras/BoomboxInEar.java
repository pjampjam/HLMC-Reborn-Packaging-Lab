package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * While the cinematic camera runs, the boombox and music disc music you hear plays right in your ears instead of from its spot in
 * the world (owner 2026-10-10: the camera flies around you, so 3D sound would jump with every shot). It fades in over two
 * seconds when the cinematic starts and back out when it ends. Voices and every other voice chat sound stay positional.
 * The fade level is moved on the client tick; blend() runs on the voice chat audio thread.
 */
public final class BoomboxInEar {
    private BoomboxInEar() {}
    static final double AHEAD = 0.3;
    /** Ticks for the fade in or out. */
    static final int FADE_TICKS = 40;
    private static volatile float level;

    /** Music categories: Holy Lois boomboxes and AudioPlayer music discs (goat horns and note blocks stay 3D). */
    static boolean music(String category) { return "boombox".equals(category) || "music_discs".equals(category); }

    /** One client tick: the fade level walks towards 1 while the cinematic plays and back to 0 after it. */
    static float step(float current, boolean cinematic) {
        float next = current + (cinematic ? 1f : -1f) / FADE_TICKS;
        return Math.clamp(next, 0f, 1f);
    }

    /** How far the sound moves into your head for a fade level: smoothstep, so it starts and lands gently. */
    static double weight(float fade) {
        double t = Math.clamp(fade, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> level = step(level, CinematicTweaks.playing()));
    }

    public static Vec3 blend(Vec3 position, String category) {
        if (position == null || !music(category)) return position;
        double w = weight(level);
        if (w <= 0) return position;
        try {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return position;
            // Voice chat listens from the camera, so "in your ears" means at the camera, wherever the shot flies.
            var camera = mc.gameRenderer.mainCamera();
            var eye = camera.position();
            var look = camera.forwardVector();
            // Not exactly at the ear: a point just ahead keeps voice chat's panning centred instead of undefined.
            var ear = eye.add(look.x() * AHEAD, look.y() * AHEAD, look.z() * AHEAD);
            return position.lerp(ear, w);
        } catch (RuntimeException ignored) {
            return position;
        }
    }
}
