package holylois.boombox;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/**
 * Moving shine for Legendary and Mythic names (client only). The server marks the text with a shine font (FishData.shine),
 * which draws exactly like the default font; here each glyph gets its colour from its screen position and the clock, so the
 * same name shimmers in chat, tooltips, death messages and anywhere else text is drawn.
 */
public final class ShineText {
    private ShineText() {}

    /** 0 = not shining, 1 = Legendary (gold), 2 = Mythic (red to gold, faster, with a small wave). */
    static int kind(Style style) {
        if (!(style.getFont() instanceof FontDescription.Resource resource)) return 0;
        var id = resource.id();
        return id.equals(FishData.SHINE_MYTHIC) ? 2 : id.equals(FishData.SHINE_LEGENDARY) ? 1 : 0;
    }

    private static float seconds() { return (net.minecraft.util.Util.getMillis() % 1_000_000L) / 1000f; }

    /** Glyph colour at x (keeps the alpha of the colour the game chose). */
    public static int color(Style style, float x, int color) {
        int kind = kind(style);
        if (kind == 0) return color;
        boolean mythic = kind == 2;
        float t = seconds();
        int from = mythic ? 0xFF2E2E : 0xFFB52E, to = mythic ? 0xFFB000 : 0xFFE24D;
        int rgb = mix(from, to, 0.5f + 0.5f * (float) Math.sin(x * 0.09f - t * (mythic ? 4.5f : 3f)));
        // A white glint sweeps from left to right every 2.4 s (Mythic every 1.6 s).
        float period = mythic ? 1.6f : 2.4f, at = (t % period) / period * 300 - 40;
        float glint = Math.max(0, 1 - Math.abs(Math.floorMod((int) x, 300) - at) / 10f);
        rgb = mix(rgb, 0xFFFFFF, glint * 0.85f);
        return (color & 0xFF000000) | rgb;
    }

    /** Mythic letters bob a little, each a beat after the one before. */
    public static float wave(Style style, float x) {
        return kind(style) == 2 ? 0.6f * (float) Math.sin(x * 0.35f + seconds() * 9f) : 0;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
