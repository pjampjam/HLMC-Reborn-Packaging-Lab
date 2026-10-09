package holylois.boombox;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
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
    private static final Map<BlockPos, Float> shown = new ConcurrentHashMap<>();
    private static int beats;

    /** Audio is arriving for this boombox (then notes follow the beat instead of the random ambient notes). */
    public static boolean live(BlockPos pos) { return levels.containsKey(pos); }
    private static ItemStack cone;
    private static final ItemStackRenderState state = new ItemStackRenderState();
    private static boolean broken;

    /** Beats found on the audio thread (BeatFinder), waiting for the next frame to show a note. */
    private static final Map<BlockPos, BeatFinder> beatFinders = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Boolean> pendingBeat = new ConcurrentHashMap<>();

    /** Voice chat audio thread: one 20 ms frame of a sound placed at x, y, z. */
    public static void heard(double x, double y, double z, short[] audio) {
        if (audio == null || audio.length == 0) return;
        var pos = BlockPos.containing(x, y, z);
        var finder = beatFinders.computeIfAbsent(pos, p -> new BeatFinder());
        long now = System.currentTimeMillis();
        if (finder.frame(audio, now)) pendingBeat.put(pos, true);
        levels.put(pos, new Level(Math.min(1, finder.rms * 4), now));
    }

    /** Held boomboxes stream through an entity channel: the same loudness and beats, keyed by the holder. */
    private static final Map<java.util.UUID, Level> heldLevels = new ConcurrentHashMap<>();
    private static final Map<java.util.UUID, Float> heldShown = new ConcurrentHashMap<>();
    private static final Map<java.util.UUID, BeatFinder> heldFinders = new ConcurrentHashMap<>();
    private static final Map<java.util.UUID, Boolean> heldBeat = new ConcurrentHashMap<>();

    /** Voice chat audio thread: one 20 ms frame of a sound coming from an entity (a player carrying a boombox). */
    public static void heardHeld(java.util.UUID holder, short[] audio) {
        if (audio == null || audio.length == 0) return;
        var finder = heldFinders.computeIfAbsent(holder, p -> new BeatFinder());
        long now = System.currentTimeMillis();
        if (finder.frame(audio, now)) heldBeat.put(holder, true);
        heldLevels.put(holder, new Level(Math.min(1, finder.rms * 4), now));
    }

    /** Set while a held boombox is drawn; FishLayerMixin then hands over the finished model pose (0..1 model space). */
    public static boolean capture;
    private static PoseStack.Pose captured;
    public static void capture(PoseStack.Pose pose) { captured = pose.copy(); }

    /** After the held boombox model: its cones, pushed out by the music, in the model's own pose. */
    public static void submitHeld(net.minecraft.client.renderer.entity.state.ArmedEntityRenderState holder, PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        var model = captured; captured = null;
        var mc = Minecraft.getInstance();
        if (broken || model == null || heldLevels.isEmpty() || mc.level == null
            || !(holder instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar)) return;
        var entity = mc.level.getEntity(avatar.id);
        if (entity == null) return;
        var heard = heldLevels.get(entity.getUUID());
        if (heard == null || System.currentTimeMillis() - heard.at() > 400) return;
        try {
            if (cone == null) { cone = new ItemStack(Items.STICK); cone.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("holylois", "boombox_cone")); }
            float last = heldShown.getOrDefault(entity.getUUID(), 0f), target = heard.value();
            float value = target > last ? last + (target - last) * 0.6f : last + (target - last) * 0.15f;
            heldShown.put(entity.getUUID(), value);
            mc.getItemModelResolver().updateForTopItem(state, cone, ItemDisplayContext.NONE, mc.level, null, 0);
            for (float side : new float[]{-3.5f, 3.5f}) {
                pose.pushPose();
                pose.last().set(model);
                // Same cone spot as the placed block, in the model's 0..1 space (north front).
                pose.translate(0.5f + side / 16f, 3.5f / 16f, 4.5f / 16f - 0.002f - value * 0.035f);
                float grow = 1.02f + value * 0.3f;
                pose.scale(grow, grow, 1);
                state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        } catch (RuntimeException | LinkageError error) { broken = true; org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Boombox pulse off", error); }
    }

    /** A note at the carrying hand on each beat of a held boombox. */
    private static void heldNotes(Minecraft mc) {
        long now = System.currentTimeMillis();
        for (var entry : heldLevels.entrySet()) {
            var id = entry.getKey();
            if (now - entry.getValue().at() > 400) { heldLevels.remove(id); heldShown.remove(id); heldFinders.remove(id); heldBeat.remove(id); continue; }
            if (heldBeat.remove(id) == null || mc.level == null) continue;
            for (var player : mc.level.players()) {
                if (!player.getUUID().equals(id)) continue;
                boolean main = player.getMainHandItem().is(Boombox.ITEM);
                if (!main && !player.getOffhandItem().is(Boombox.ITEM)) break;
                // The hand side of the body: right for a right-handed main hand.
                boolean right = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == main;
                double yaw = Math.toRadians(player.yBodyRot), sideways = right ? -0.4 : 0.4;
                double x = player.getX() + Math.cos(yaw) * sideways, z = player.getZ() + Math.sin(yaw) * sideways;
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, x, player.getY() + 0.75, z, (beats++ % 25) / 24.0, 0, 0);
                break;
            }
        }
    }

    static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> { if (!heldLevels.isEmpty()) heldNotes(mc); });
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
            if (now - heard.at() > 400) { levels.remove(pos); shown.remove(pos); beatFinders.remove(pos); pendingBeat.remove(pos); continue; }
            var block = level.getBlockState(pos);
            if (!block.is(Boombox.BLOCK) || !block.getValue(BoomboxBlock.PLAYING) || pos.distToCenterSqr(camera) > 48 * 48) continue;
            // Fast attack, slower release, like a speaker cone.
            float last = shown.getOrDefault(pos, 0f), target = heard.value();
            float value = target > last ? last + (target - last) * 0.6f : last + (target - last) * 0.15f;
            shown.put(pos, value);
            // A note on each beat found by the audio thread.
            if (pendingBeat.remove(pos) != null) {
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
