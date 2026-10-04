package holylois.boombox;

import dev.tr7zw.firstperson.api.FirstPersonAPI;
import dev.tr7zw.firstperson.api.PlayerOffsetHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two small fixes for the FirstPerson body. In bed the body is switched off (the mod skips its own offset while sleeping, which
 * left the camera inside the head) and switched back on when you wake up, unless you turned it off yourself. With a wall behind
 * you the body is pulled forward so it no longer sits inside the block and renders dark. Loaded only with FirstPerson.
 */
final class FirstPersonFixes {
    private static boolean hiddenByUs;

    static void register() {
        FirstPersonAPI.registerPlayerHandler((PlayerOffsetHandler) (player, delta, base, offset) -> {
            double length = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (length < 0.01 || player.isSleeping()) return offset;
            Vec3 from = player.getPosition(delta), direction = new Vec3(offset.x / length, 0, offset.z / length);
            double room = length;
            for (double height : new double[] {0.3, 0.9, 1.5}) {
                Vec3 start = from.add(0, height, 0), end = start.add(direction.scale(length + 0.35));
                var hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.BLOCK) room = Math.min(room, Math.max(0, start.distanceTo(hit.getLocation()) - 0.35));
            }
            return room >= length ? offset : new Vec3(offset.x * room / length, offset.y, offset.z * room / length);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean asleep = mc.player != null && mc.player.isSleeping();
            if (asleep && !hiddenByUs && FirstPersonAPI.isEnabled()) { FirstPersonAPI.setEnabled(false); hiddenByUs = true; }
            else if (!asleep && hiddenByUs) { FirstPersonAPI.setEnabled(true); hiddenByUs = false; }
        });
    }
}
