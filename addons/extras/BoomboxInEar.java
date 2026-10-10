package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Music right in your ears when you stand next to a playing boombox or AudioPlayer disc (owner 2026-10-10): within NEAR blocks
 * the sound sits just in front of your face (centred, full volume), from FAR blocks it is the normal 3D sound, and in between it
 * glides smoothly. Voices and every other voice chat sound stay positional. Runs on the voice chat audio thread.
 */
public final class BoomboxInEar {
    private BoomboxInEar() {}
    static final double NEAR = 3, FAR = 8, AHEAD = 0.3;

    /** Music categories: Holy Lois boomboxes and AudioPlayer music discs (goat horns and note blocks stay 3D). */
    static boolean music(String category) { return "boombox".equals(category) || "music_discs".equals(category); }

    /** How far the sound moves into your head: 1 at NEAR or closer, 0 at FAR or further, smoothstep between. */
    static double weight(double distance) {
        double t = Math.clamp((FAR - distance) / (FAR - NEAR), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static Vec3 blend(Vec3 position, String category) {
        if (position == null || !music(category)) return position;
        try {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return position;
            var camera = mc.gameRenderer.mainCamera();
            var eye = camera.position();
            double w = weight(position.distanceTo(eye));
            if (w <= 0) return position;
            var look = camera.forwardVector();
            // Not exactly at the ear: a point just ahead keeps voice chat's panning centred instead of undefined.
            var ear = eye.add(look.x() * AHEAD, look.y() * AHEAD, look.z() * AHEAD);
            return position.lerp(ear, w);
        } catch (RuntimeException ignored) {
            return position;
        }
    }
}
