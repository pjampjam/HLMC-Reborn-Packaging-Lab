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

    /** Boombox or disc music reached you in the last two seconds (the cinematic then starts after one idle minute). */
    public static boolean musicNearby() {
        long now = System.currentTimeMillis();
        for (var level : levels.values()) if (now - level.at() < 2000) return true;
        for (var level : heldLevels.values()) if (now - level.at() < 2000) return true;
        return false;
    }
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
        if (entity != null) heldCones(entity.getUUID(), model, pose, collector, light);
    }

    /** First person: the boombox in your own hand pulses too (FirstPersonBoomboxMixin captures its pose the same way). */
    public static void submitFirstPerson(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        var model = captured; captured = null;
        var mc = Minecraft.getInstance();
        if (broken || model == null || heldLevels.isEmpty() || mc.player == null) return;
        heldCones(mc.player.getUUID(), model, pose, collector, light);
    }
    /** Frames in which the cones of your own held boombox were drawn, from the body or the vanilla hand (gametest). */
    public static int ownCones;

    private static boolean heldCones(java.util.UUID id, PoseStack.Pose model, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        var mc = Minecraft.getInstance();
        var heard = heldLevels.get(id);
        if (heard == null || System.currentTimeMillis() - heard.at() > 400) return false;
        try {
            if (cone == null) { cone = new ItemStack(Items.STICK); cone.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("holylois", "boombox_cone")); }
            float last = heldShown.getOrDefault(id, 0f), target = heard.value();
            float value = target > last ? last + (target - last) * 0.6f : last + (target - last) * 0.15f;
            heldShown.put(id, value);
            mc.getItemModelResolver().updateForTopItem(state, cone, ItemDisplayContext.NONE, mc.level, null, 0);
            // Where the two speakers are in the world right now, and which way they face: held notes come out of them.
            var cam = mc.gameRenderer.mainCamera().position();
            var at = new net.minecraft.world.phys.Vec3[2];
            for (int i = 0; i < 2; i++) {
                var v = model.pose().transformPosition(0.5f + (i == 0 ? -3.5f : 3.5f) / 16f, 3.5f / 16f, 4.5f / 16f, new org.joml.Vector3f());
                at[i] = cam.add(v.x(), v.y(), v.z());
            }
            var front = model.pose().transformDirection(0, 0, -1, new org.joml.Vector3f()).normalize();
            heldSpeakers.put(id, new Speakers(at[0], at[1], new net.minecraft.world.phys.Vec3(front.x(), front.y(), front.z()), System.currentTimeMillis()));
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
            if (mc.player != null && id.equals(mc.player.getUUID())) ownCones++;
            return true;
        } catch (RuntimeException | LinkageError error) { broken = true; org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Boombox pulse off", error); return false; }
    }

    /**
     * One note hopping out of a speaker on the beat: thrown along (sx, sz) and up, then slowed by the note's own friction.
     * Louder music throws it further and draws it bigger. Skipped with Particles: Minimal.
     */
    private static void hop(Minecraft mc, double x, double y, double z, double sx, double sz, float loud) {
        if (mc.options.particles().get() == net.minecraft.server.level.ParticleStatus.MINIMAL) return;
        var note = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.NOTE, x, y, z, (beats++ % 25) / 24.0, 0, 0);
        if (note == null) return;
        double push = 0.07 + loud * 0.13;
        note.setParticleSpeed(sx * push, 0.07 + loud * 0.07, sz * push);
        lastScale = 0.55f + loud * 0.9f;
        note.scale(lastScale);
        note.setLifetime(12);
    }

    /** Size of the last note (gametest). */
    public static float lastScale;

    /** The two speaker cones of a held boombox in the world and the way its front faces, from the last frame it was drawn. */
    public record Speakers(net.minecraft.world.phys.Vec3 left, net.minecraft.world.phys.Vec3 right, net.minecraft.world.phys.Vec3 front, long at) {}
    public static final Map<java.util.UUID, Speakers> heldSpeakers = new ConcurrentHashMap<>();

    /** Last speaker that threw a note, so the beats alternate left and right. */
    private static final Map<Object, Boolean> lastSide = new ConcurrentHashMap<>();

    /** Notes hop out sideways from the carrying hand on each beat of a held boombox (not in your own first-person view). */
    private static void heldNotes(Minecraft mc) {
        long now = System.currentTimeMillis();
        for (var entry : heldLevels.entrySet()) {
            var id = entry.getKey();
            if (now - entry.getValue().at() > 400) { heldLevels.remove(id); heldShown.remove(id); heldFinders.remove(id); heldBeat.remove(id); lastSide.remove(id); heldSpeakers.remove(id); continue; }
            if (heldBeat.remove(id) == null || mc.level == null) continue;
            for (var player : mc.level.players()) {
                if (!player.getUUID().equals(id)) continue;
                boolean main = player.getMainHandItem().is(Boombox.ITEM);
                if (!main && !player.getOffhandItem().is(Boombox.ITEM)) break;
                // Owner 2026-10-10: notes in your own first-person view only got in the way of the hand.
                if (player == mc.player && mc.options.getCameraType().isFirstPerson()) break;
                float loud = heldShown.getOrDefault(id, entry.getValue().value());
                boolean first = !lastSide.getOrDefault(id, false); lastSide.put(id, first);
                var speakers = heldSpeakers.get(id);
                if (speakers != null && now - speakers.at() < 500) {
                    // Out of one speaker cone, left and right in turn (owner 2026-10-10): forward out of the grille and off to its side.
                    var cone = first ? speakers.left() : speakers.right();
                    var away = cone.subtract(first ? speakers.right() : speakers.left()).normalize();
                    var push = speakers.front().scale(0.8).add(away.scale(0.6));
                    hop(mc, cone.x, cone.y, cone.z, push.x, push.z, loud);
                    break;
                }
                // Not drawn this frame (out of view): about where the box hangs, on the hand's side, out from the body.
                boolean right = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == main;
                double yaw = Math.toRadians(player.yBodyRot), sideways = right ? -0.45 : 0.45;
                double rx = Math.cos(yaw), rz = Math.sin(yaw), out = right ? -1 : 1;
                hop(mc, player.getX() + rx * sideways, player.getY() + 0.35, player.getZ() + rz * sideways, rx * out, rz * out, loud);
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
            if (now - heard.at() > 400) { levels.remove(pos); shown.remove(pos); beatFinders.remove(pos); pendingBeat.remove(pos); lastSide.remove(pos); continue; }
            var block = level.getBlockState(pos);
            if (!block.is(Boombox.BLOCK) || !block.getValue(BoomboxBlock.PLAYING) || pos.distToCenterSqr(camera) > 48 * 48) continue;
            // Fast attack, slower release, like a speaker cone.
            float last = shown.getOrDefault(pos, 0f), target = heard.value();
            float value = target > last ? last + (target - last) * 0.6f : last + (target - last) * 0.15f;
            shown.put(pos, value);
            Direction facing = block.getValue(BoomboxBlock.FACING);
            // On each beat found by the audio thread a note hops out of one speaker, left and right in turn: sideways and a bit forward.
            if (pendingBeat.remove(pos) != null) {
                boolean left = !lastSide.getOrDefault(pos, false); lastSide.put(pos, left);
                Direction out = left ? facing.getCounterClockWise() : facing.getClockWise();
                double cx = pos.getX() + 0.5 + facing.getStepX() * 0.26 + out.getStepX() * 3.5 / 16;
                double cz = pos.getZ() + 0.5 + facing.getStepZ() * 0.26 + out.getStepZ() * 3.5 / 16;
                hop(mc, cx, pos.getY() + 3.5 / 16, cz, out.getStepX() + facing.getStepX() * 0.4, out.getStepZ() + facing.getStepZ() * 0.4, value);
            }
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
