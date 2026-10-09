package holylois.boombox;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The speaker cones of a placed boombox push out with the music. Loudness comes from the voice chat audio the client
 * already receives for that boombox (no extra network traffic); the cones are drawn over the block model.
 */
public final class BoomboxPulse {
    private BoomboxPulse() {}
    private record Level(float value, long at) {}
    private static final Map<BlockPos, Level> levels = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Float> shown = new ConcurrentHashMap<>(), average = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> lastBeat = new ConcurrentHashMap<>();
    private static int beats;

    /** Audio is arriving for this boombox (then notes follow the beat instead of the random ambient notes). */
    public static boolean live(BlockPos pos) { return levels.containsKey(pos); }
    private static ItemStack cone;
    private static final ItemStackRenderState state = new ItemStackRenderState();
    private static boolean broken;

    /** Voice chat audio thread: one 20 ms frame of a sound placed at x, y, z. */
    public static void heard(double x, double y, double z, short[] audio) {
        if (audio == null || audio.length == 0) return;
        double sum = 0;
        for (short sample : audio) sum += (double) sample * sample;
        float rms = (float) Math.sqrt(sum / audio.length) / 32768f;
        levels.put(BlockPos.containing(x, y, z), new Level(Math.min(1, rms * 4), System.currentTimeMillis()));
    }

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (broken || levels.isEmpty()) return;
            try { render(context); }
            catch (RuntimeException | LinkageError error) { broken = true; org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Boombox pulse off", error); }
        });
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        if (cone == null) { cone = new ItemStack(Items.STICK); cone.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("holylois", "boombox_cone")); }
        var camera = context.levelState().cameraRenderState.pos;
        long now = System.currentTimeMillis();
        var pose = context.poseStack();
        for (var entry : levels.entrySet()) {
            var pos = entry.getKey();
            var heard = entry.getValue();
            if (now - heard.at() > 400) { levels.remove(pos); shown.remove(pos); average.remove(pos); lastBeat.remove(pos); continue; }
            var block = level.getBlockState(pos);
            if (!block.is(Boombox.BLOCK) || !block.getValue(BoomboxBlock.PLAYING) || pos.distToCenterSqr(camera) > 48 * 48) continue;
            // Fast attack, slower release, like a speaker cone.
            float last = shown.getOrDefault(pos, 0f), target = heard.value();
            float value = target > last ? last + (target - last) * 0.6f : last + (target - last) * 0.15f;
            shown.put(pos, value);
            // A note on each beat: loudness jumping well above its recent average, at most about four a second.
            float avg = average.getOrDefault(pos, target) * 0.95f + target * 0.05f;
            average.put(pos, avg);
            if (target > 0.12f && target > avg * 1.6f && now - lastBeat.getOrDefault(pos, 0L) > 240) {
                lastBeat.put(pos, now);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, pos.getX() + 0.3 + level.getRandom().nextDouble() * 0.4,
                    pos.getY() + 0.8, pos.getZ() + 0.3 + level.getRandom().nextDouble() * 0.4, (beats++ % 25) / 24.0, 0, 0);
            }
            Direction facing = block.getValue(BoomboxBlock.FACING);
            int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
            mc.getItemModelResolver().updateForTopItem(state, cone, ItemDisplayContext.NONE, level, null, 0);
            for (float side : new float[]{-3.5f, 3.5f}) {
                pose.pushPose();
                pose.translate(pos.getX() + 0.5 - camera.x, pos.getY() - camera.y, pos.getZ() + 0.5 - camera.z);
                pose.rotateDegrees(com.mojang.math.Axis.YP, 180 - facing.toYRot());
                // Cone centre in model pixels (north front): x 4.5 / 11.5, y 3.5, z 4.5 (face at 4.25); pushed out and grown by the level.
                pose.translate(side / 16f, 3.5f / 16f, (4.5f - 8f) / 16f - 0.002f - value * 0.035f);
                float grow = 1.02f + value * 0.3f;
                pose.scale(grow, grow, 1);
                state.submit(pose, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        }
    }
}
