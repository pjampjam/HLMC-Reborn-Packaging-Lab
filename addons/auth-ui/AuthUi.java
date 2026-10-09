package holylois.auth;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Holy Lois look for the login screen and reward card: a copy of extras Ui (the launcher/website palette, App.xaml tokens),
 * kept here because auth-ui loads without extras.
 */
public final class AuthUi {
    private AuthUi() {}
    // Owner palette 2026-10-09: near-black like the cinematic bars, chat yellow (#FFFF55) as the accent, chat green/red.
    public static final int CANVAS = 0xFF050506, SURFACE = 0xFF0C0C0E, RAISED = 0xFF16171A, CONTROL = 0xFF1C1D21,
        CONTROL_HOVER = 0xFF2B2C32, LINE = 0xFF2C2E34, LINE_STRONG = 0xFF6E717A, TEXT = 0xFFFFFFFF, MUTED = 0xFFAAAAAA,
        GOLD = 0xFFFFFF55, GOLD_SOFT = 0xFF2E2E14, ON_GOLD = 0xFF000000, GREEN = 0xFF55FF55, SUCCESS = 0xFF55FF55,
        DANGER = 0xFFFF5555, DANGER_FILL = 0xFFC2362F, HEALTH = 0xFFFF4545, FOOD = 0xFFFFAA00,
        // Yes/no buttons: the standard success green and danger red (Bootstrap 5), white labels at 4.5:1 (owner round 5).
        CONFIRM_BUTTON = 0xFF198754, DANGER_BUTTON = 0xFFDC3545;

    /** Same colour with alpha a (0..1). */
    /** Blends two ARGB colours (t = 0 gives a, 1 gives b). */
    public static int mix(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) r |= Math.round(((a >>> shift) & 255) * (1 - t) + ((b >>> shift) & 255) * t) << shift;
        return r;
    }

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

    public enum Kind { NORMAL, PRIMARY, CONFIRM, DANGER }

    /** Button: control fill; gold for a highlight, success green for yes-actions, danger red for cancel and destructive ones. */
    public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean hover, boolean focus, boolean active, Kind kind) {
        int solid = switch (kind) { case PRIMARY -> GOLD; case CONFIRM -> CONFIRM_BUTTON; case DANGER -> DANGER_BUTTON; default -> 0; };
        int fill = solid != 0 ? (hover ? mix(solid, 0xFFFFFFFF, kind == Kind.PRIMARY ? 0.45f : 0.15f) : solid) : hover ? CONTROL_HOVER : CONTROL;
        int border = focus ? TEXT : solid != 0 ? solid : hover ? LINE_STRONG : LINE;
        int text = kind == Kind.PRIMARY ? ON_GOLD : solid != 0 ? 0xFFFFFFFF : TEXT;
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
