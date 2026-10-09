package holylois.boombox;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * How a weighed fish looks on the client (client only; the data itself is FishData):
 * bigger in hand, on the ground, in frames and on the cutting board; a rarity glow and its weight on the inventory icon.
 */
public final class FishLook {
    private FishLook() {}
    /** Scale of the item layer being submitted right now (render thread only). */
    public static float current = 1, lift;
    /** The item being drawn is a fish carried in both arms (set around ItemInHandLayer.submitArmWithItem). */
    public static boolean carry;
    /** Swing of a carried boombox around its handle, degrees (set around ItemInHandLayer.submitArmWithItem). */
    public static float swingX, swingZ;

    /** Forward-back swing with the walk, a little sideways sway, and a faint idle drift. */
    public static void swing(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        float walk = Math.min(1, state.walkAnimationSpeed), phase = state.walkAnimationPos * 0.6662f;
        swingZ = 16 * (float) Math.sin(phase) * walk + 1.5f * (float) Math.sin(state.ageInTicks * 0.07f);
        swingX = 5 * (float) Math.cos(phase * 2) * walk;
    }
    /** The fish sprite runs corner to corner: turned this much around its face it lies level across the body. */
    public static final float CARRY_TURN = -45;

    /** Body (model) space, y down: lying flat on the raised hands above the head, centred on the body. */
    public static float carryHeight = -0.68f;
    public static void carryPose(com.mojang.blaze3d.vertex.PoseStack pose, Object state) {
        pose.translate(0, carryHeight, -0.05f);
        pose.rotateDegrees(com.mojang.math.Axis.ZP, 180);
        pose.rotateDegrees(com.mojang.math.Axis.XP, 90);
    }

    /** 1 for anything but a weighed fish; inventory icons stay their normal size. */
    /** Your own view grows by this much less (Mythic 3.5x looks 2x through your eyes). */
    static final float OWN_VIEW = 2.5f;

    /** Held by owner: your own fish while you look through your eyes (vanilla hand or the FirstPerson body) grows less. */
    public static float scale(ItemStack stack, ItemDisplayContext context, Object owner) {
        float scale = scale(stack, context);
        if (scale == 1 || context.firstPerson()) return scale;
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean ownView = owner != null && owner == mc.player && mc.options.getCameraType().isFirstPerson()
            && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        return ownView ? 1 + (scale - 1) / OWN_VIEW : scale;
    }

    public static float scale(ItemStack stack, ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.NONE) return 1;
        var fish = FishData.tag(stack);
        if (fish == null || fish.getBooleanOr("fillet", false)) return 1;
        double kg = fish.getDoubleOr("kg", 0);
        float scale = kg > 0 ? FishData.scaleKg(kg) : FishData.scale(fish.getStringOr("rarity", ""), fish.getDoubleOr("size", -1));
        // Frames and the cutting board: big, but kept inside the block around them.
        if (context == ItemDisplayContext.FIXED) scale = Math.min(scale, 3);
        // In your own first-person view a huge fish would cover the screen: grow it less there.
        return context.firstPerson() ? 1 + (scale - 1) / OWN_VIEW : scale;
    }

    /** The fish texture washed with its rarity colour (Mythic strongest); 0 for plain fish. */
    public static int tint(ItemStack stack) {
        String rarity = FishData.rarity(stack);
        int color = FishData.color(rarity);
        if (color == 0 || rarity.equals("uncommon")) return 0;
        float mix = switch (rarity) { case "rare" -> 0.3f; case "epic" -> 0.35f; case "legendary" -> 0.4f; default -> 0.55f; };
        int r = Math.round(255 + ((color >> 16 & 255) - 255) * mix), g = Math.round(255 + ((color >> 8 & 255) - 255) * mix), b = Math.round(255 + ((color & 255) - 255) * mix);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Epic and better fish lying on the ground give off a few sparks in their rarity colour (client only, nearby items). */
    static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 4 != 0) return;
            var random = mc.level.getRandom();
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity.distanceToSqr(mc.player) > 32 * 32) continue;
                // Shiny fish throw white sparks on top of the rarity sparks: lying on the ground or held in a hand.
                if (entity instanceof net.minecraft.world.entity.LivingEntity holder && FishData.shiny(holder.getMainHandItem()) && random.nextInt(2) == 0
                        && !(holder == mc.player && mc.options.getCameraType().isFirstPerson())) {
                    double side = holder.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -0.35 : 0.35, yaw = Math.toRadians(holder.yBodyRot);
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, holder.getX() + Math.cos(yaw) * side + (random.nextDouble() - 0.5) * 0.5,
                        holder.getY() + 0.8 + random.nextDouble() * 0.6, holder.getZ() + Math.sin(yaw) * side + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
                }
                if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item)) continue;
                if (FishData.shiny(item.getItem()) && random.nextInt(2) == 0)
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, item.getX() + (random.nextDouble() - 0.5) * 0.6,
                        item.getY() + 0.2 + random.nextDouble() * 0.4, item.getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
                String rarity = FishData.rarity(item.getItem());
                int chance = switch (rarity) { case "epic" -> 4; case "legendary" -> 2; case "mythic" -> 1; default -> 0; };
                if (chance == 0 || random.nextInt(chance) != 0) continue;
                float size = scale(item.getItem(), ItemDisplayContext.GROUND);
                mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(FishData.color(rarity) & 0xFFFFFF, rarity.equals("mythic") ? 1.2f : 0.8f),
                    item.getX() + (random.nextDouble() - 0.5) * 0.4 * size, item.getY() + 0.2 + random.nextDouble() * 0.3 * size,
                    item.getZ() + (random.nextDouble() - 0.5) * 0.4 * size, 0, 0.02, 0);
            }
        });
    }

}
