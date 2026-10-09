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
    /** Swing of a carried boombox around its handle, degrees (HeldSwing, set around ItemInHandLayer.submitArmWithItem). */
    public static float swingX, swingZ;

    /** The fish sprite runs corner to corner: turned this much around its face it lies level across the body. */
    public static final float CARRY_TURN = -45;

    /**
     * Body (model) space, y down: lying flat on the raised fists, between them. The fists are found from the arm parts as
     * they will be drawn (Fresh Animations' breathing, walk bob and jumps move the shoulders), so the fish rides along.
     */
    public static float carryRest = -0.06f;
    public static void carryPose(com.mojang.blaze3d.vertex.PoseStack pose, Object model) {
        if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) {
            var right = fist(humanoid.rightArm, -1); var left = fist(humanoid.leftArm, 1);
            pose.translate((right.x + left.x) / 2, (right.y + left.y) / 2 + carryRest, (right.z + left.z) / 2);
        } else pose.translate(0, -0.6f, 0);
        pose.rotateDegrees(com.mojang.math.Axis.ZP, 180);
        pose.rotateDegrees(com.mojang.math.Axis.XP, 90);
    }

    /** The end of an arm (its fist, 10 px down the arm and 1 px out) in model space, like ModelPart.translateAndRotate. */
    static org.joml.Vector3f fist(net.minecraft.client.model.geom.ModelPart arm, int side) {
        return new org.joml.Matrix4f().translation(arm.x / 16, arm.y / 16, arm.z / 16)
            .rotate(new org.joml.Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot)).scale(arm.xScale, arm.yScale, arm.zScale)
            .transformPosition(new org.joml.Vector3f(side / 16f, 10 / 16f, 0));
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

    /**
     * While a heavy fish is carried in both arms, the off-hand slot shows a dimmed copy of it: the hand is taken (FishHands keeps
     * it empty on the server), drawn where vanilla puts the off-hand slot.
     */
    static void registerOffhandGhost() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("holylois", "offhand_ghost"), (g, delta) -> {
                var mc = net.minecraft.client.Minecraft.getInstance();
                var player = mc.player;
                if (player == null || player.isSpectator() || mc.gui.hud.isHidden() || !player.getOffhandItem().isEmpty()) return;
                var fish = player.getMainHandItem();
                if (!FishData.twoHanded(fish)) return;
                int cx = g.guiWidth() / 2, h = g.guiHeight();
                boolean left = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
                int sx = left ? cx - 91 - 29 : cx + 91, ix = left ? cx - 91 - 26 : cx + 91 + 10;
                g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.withDefaultNamespace(left ? "hud/hotbar_offhand_left" : "hud/hotbar_offhand_right"), sx, h - 23, 29, 24);
                g.item(fish, ix, h - 19);
                g.fill(ix, h - 19, ix + 16, h - 3, 0x99101012);
            });
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
                    // A heavy fish is carried over the head: the sparks come from there, along its length.
                    boolean overhead = FishData.twoHanded(holder.getMainHandItem());
                    double side = overhead ? (random.nextDouble() - 0.5) * 1.4 : holder.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -0.35 : 0.35;
                    double yaw = Math.toRadians(holder.yBodyRot), height = overhead ? 2.05 + random.nextDouble() * 0.3 : 0.8 + random.nextDouble() * 0.6;
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, holder.getX() + Math.cos(yaw) * side + (random.nextDouble() - 0.5) * (overhead ? 0.2 : 0.5),
                        holder.getY() + height, holder.getZ() + Math.sin(yaw) * side + (random.nextDouble() - 0.5) * (overhead ? 0.2 : 0.5), 0, 0.03, 0);
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
