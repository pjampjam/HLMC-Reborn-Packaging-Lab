package holylois.boombox;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

/**
 * How a weighed fish looks on the client (the server writes custom_data holylois_fish in Legends.weigh):
 * bigger in hand, on the ground, in frames and on the cutting board; a rarity glow and its weight on the inventory icon.
 */
public final class FishLook {
    private FishLook() {}
    /** Scale of the item layer being submitted right now (render thread only). */
    public static float current = 1;

    static CompoundTag tag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var root = data.copyTag();
        return root.getCompound(Legends.FISH_KEY).orElse(null);
    }

    public static String rarity(ItemStack stack) {
        var fish = tag(stack);
        return fish == null ? "" : fish.getStringOr("rarity", "");
    }

    /** 1 for anything but a weighed fish; inventory icons stay their normal size. */
    /** Held by owner: your own fish while you look through your eyes (vanilla hand or the FirstPerson body) grows less. */
    public static float scale(ItemStack stack, ItemDisplayContext context, Object owner) {
        float scale = scale(stack, context);
        if (scale == 1 || context.firstPerson()) return scale;
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean ownView = owner != null && owner == mc.player && mc.options.getCameraType().isFirstPerson()
            && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        return ownView ? 1 + (scale - 1) / 6 : scale;
    }

    public static float scale(ItemStack stack, ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.NONE) return 1;
        var fish = tag(stack);
        if (fish == null) return 1;
        float scale = scale(fish.getStringOr("rarity", ""), fish.getDoubleOr("size", -1));
        // In your own first-person view a huge fish would cover the screen: grow it a sixth as much there.
        return context.firstPerson() ? 1 + (scale - 1) / 6 : scale;
    }

    /** Size 0..1 gives 1x to 1.7x; a Mythic (size 1.5..2.5) is absurd, capped at 3.5x. Older trophies have no size: by rarity. */
    static float scale(String rarity, double size) {
        if (size < 0) size = switch (rarity) { case "rare" -> 0.8; case "epic" -> 0.93; case "legendary" -> 0.98; case "mythic" -> 2; default -> -1; };
        if (size < 0) return rarity.equals("uncommon") ? 1.1f : 1;
        return (float) Math.min(3.5, 1 + 0.7 * size * size);
    }

    /** Rarity colour, as in the item name (launcher palette for the light tiers). */
    public static int color(String rarity) {
        return switch (rarity) {
            case "uncommon" -> 0xFF7ED3A0;
            case "rare" -> 0xFF8DD8FF;
            case "epic" -> 0xFFD49EFF;
            case "legendary" -> 0xFFFFE24D;
            case "mythic" -> 0xFFFF4A3D;
            default -> 0;
        };
    }

    /** How many times the cutting board results are given: 1 for normal fish, 2-4 for trophies by size, up to 8 for a Mythic. */
    public static int fillets(ItemStack stack) {
        var fish = tag(stack);
        if (fish == null) return 1;
        double size = fish.getDoubleOr("size", -1);
        if (size < 0) size = switch (fish.getStringOr("rarity", "")) { case "rare" -> 0.8; case "epic" -> 0.93; case "legendary" -> 0.98; case "mythic" -> 2; default -> 0; };
        return size < 0.75 ? 1 : (int) Math.min(8, 1 + Math.round(size * 3));
    }

    /** Epic and better fish lying on the ground give off a few sparks in their rarity colour (client only, nearby items). */
    static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 4 != 0) return;
            var random = mc.level.getRandom();
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item) || item.distanceToSqr(mc.player) > 32 * 32) continue;
                String rarity = rarity(item.getItem());
                int chance = switch (rarity) { case "epic" -> 4; case "legendary" -> 2; case "mythic" -> 1; default -> 0; };
                if (chance == 0 || random.nextInt(chance) != 0) continue;
                float size = scale(item.getItem(), ItemDisplayContext.GROUND);
                mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(color(rarity) & 0xFFFFFF, rarity.equals("mythic") ? 1.2f : 0.8f),
                    item.getX() + (random.nextDouble() - 0.5) * 0.4 * size, item.getY() + 0.2 + random.nextDouble() * 0.3 * size,
                    item.getZ() + (random.nextDouble() - 0.5) * 0.4 * size, 0, 0.02, 0);
            }
        });
    }

    /** "0.85", "4.2", "12", "1.2k": fits in the corner of a slot. */
    public static String shortWeight(double kg) {
        if (kg < 10) return String.format(Locale.ROOT, kg < 1 ? "%.2f" : "%.1f", kg);
        if (kg < 1000) return String.valueOf(Math.round(kg));
        return String.format(Locale.ROOT, "%.1fk", kg / 1000);
    }

    /** The weight label for a slot, or null (Common and Uncommon fish have no weight). */
    public static String label(ItemStack stack) {
        var fish = tag(stack);
        if (fish == null || !fish.contains("kg")) return null;
        return shortWeight(fish.getDoubleOr("kg", 0));
    }
}
