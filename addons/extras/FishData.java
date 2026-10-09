package holylois.boombox;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

/**
 * What a weighed fish is (custom_data holylois_fish written by Legends.weigh): rarity, size, weight. Safe on both sides;
 * the client-only drawing lives in FishLook (a client class here once crashed the server's cutting board).
 */
public final class FishData {
    private FishData() {}

    static CompoundTag tag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return data.copyTag().getCompound(Legends.FISH_KEY).orElse(null);
    }

    public static String rarity(ItemStack stack) {
        var fish = tag(stack);
        return fish == null ? "" : fish.getStringOr("rarity", "");
    }

    public static boolean shiny(ItemStack stack) {
        var fish = tag(stack);
        return fish != null && fish.getBooleanOr("shiny", false);
    }

    /** Size 0..1 by roll (Mythic 1.5..2.5); older trophies have none, then by rarity. */
    static double size(CompoundTag fish) {
        double size = fish.getDoubleOr("size", -1);
        if (size >= 0) return size;
        return switch (fish.getStringOr("rarity", "")) { case "rare" -> 0.8; case "epic" -> 0.93; case "legendary" -> 0.98; case "mythic" -> 2; default -> -1; };
    }

    /** Size 0..1 gives 1x to 1.7x; a Mythic (size 1.5..2.5) is absurd, capped at 3.5x. */
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
        double size = size(fish);
        return size < 0.75 ? 1 : (int) Math.min(8, 1 + Math.round(size * 3));
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
