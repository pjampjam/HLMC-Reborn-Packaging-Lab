package holylois.boombox;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Holy Lois look for in-game screens and HUD cards: the launcher/website palette (App.xaml tokens) and small rounded
 * shapes. auth-ui keeps a copy (AuthUi) because it loads without this add-on.
 */
public final class Ui {
    private Ui() {}
    public static final int CANVAS = 0xFF111215, SURFACE = 0xFF18191C, RAISED = 0xFF202226, CONTROL = 0xFF282A2F,
        CONTROL_HOVER = 0xFF32353B, LINE = 0xFF3A3D44, LINE_STRONG = 0xFF5E626C, TEXT = 0xFFF3F0E8, MUTED = 0xFFADA99F,
        GOLD = 0xFFFFE24D, GOLD_SOFT = 0xFF3A3220, ON_GOLD = 0xFF1A1500, GREEN = 0xFF2BC46E, SUCCESS = 0xFF7ED3A0,
        DANGER = 0xFFFF928A, DANGER_FILL = 0xFFC2362F, HEALTH = 0xFFE5484D, FOOD = 0xFFE0A85A;

    /** Same colour with alpha a (0..1). */
    public static int alpha(int color, float a) { return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (color & 0xFFFFFF); }

    /** A rectangle with 1 px rounded corners; border 0 draws none. */
    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        int edge = border == 0 ? fill : border;
        g.fill(x + 1, y, x + w - 1, y + 1, edge);
        g.fill(x, y + 1, x + w, y + h - 1, fill);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, edge);
        if (border != 0) { g.fill(x, y + 1, x + 1, y + h - 1, border); g.fill(x + w - 1, y + 1, x + w, y + h - 1, border); }
    }

    /** Screen panel: surface with a line border and the title in text colour over a short gold rule. */
    public static void panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component title) {
        box(g, x, y, w, h, SURFACE, LINE);
        if (title != null) {
            g.centeredText(font, title, x + w / 2, y + 10, TEXT);
            g.fill(x + w / 2 - 12, y + 22, x + w / 2 + 12, y + 23, GOLD);
        }
    }

    /** HUD card: translucent surface, line border and a coloured accent on the left. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float opacity) {
        box(g, x, y, w, h, alpha(SURFACE, 0.92f * opacity), alpha(LINE, opacity));
        if (accent != 0) g.fill(x + 1, y + 3, x + 3, y + h - 3, alpha(accent, opacity));
    }

    public enum Kind { NORMAL, PRIMARY, DANGER }

    /** Button like the launcher's: control fill, gold for the main action, red text for destructive ones. */
    public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean hover, boolean focus, boolean active, Kind kind) {
        int fill = kind == Kind.PRIMARY ? (hover ? 0xFFFFEA7A : GOLD) : hover ? CONTROL_HOVER : CONTROL;
        int border = focus ? GOLD : kind == Kind.PRIMARY ? GOLD : hover ? LINE_STRONG : LINE;
        int text = kind == Kind.PRIMARY ? ON_GOLD : kind == Kind.DANGER ? DANGER : TEXT;
        if (!active) { fill = CONTROL; border = LINE; text = alpha(MUTED, 0.55f); }
        box(g, x, y, w, h, fill, border);
        String shown = font.plainSubstrByWidth(label.getString(), w - 10);
        g.text(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, text, false);
    }

    /** A thin rounded bar: track, then the filled part. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int fill, int track) {
        box(g, x, y, w, h, track, 0);
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 1) box(g, x, y, filled, h, fill, 0);
    }
}
