package holylois.boombox;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.OptionalLong;

/**
 * Joining the server: the resource pack download no longer pops a corner toast (owner round 4). It runs on the same white
 * bar Remove Reloading Screen shows while the pack is applied (same place: 83% down, vanilla style), with small white text
 * under it saying what is happening: downloading with its progress, then applying, or that it failed.
 */
public final class PackLoadingBar {
    private PackLoadingBar() {}
    private enum Phase { IDLE, DOWNLOADING, APPLYING, FAILED }
    private static volatile Phase phase = Phase.IDLE;
    private static volatile long total = -1, done, changedAt;

    // Download thread, through PackToastMixin.
    public static void downloadStart(OptionalLong size) { total = size.isPresent() ? size.getAsLong() : -1; done = 0; set(Phase.DOWNLOADING); }
    public static void downloaded(long bytes) { done = bytes; }
    public static void finished(boolean ok) { set(ok ? Phase.APPLYING : Phase.FAILED); }
    private static void set(Phase next) { phase = next; changedAt = System.currentTimeMillis(); }

    static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> ScreenEvents.afterExtract(screen).register((s, g, x, y, delta) -> draw(g)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("holylois", "pack_loading"), (g, delta) -> {
            if (Minecraft.getInstance().gui.screen() == null) draw(g);
        });
    }

    private static void draw(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        long now = System.currentTimeMillis(), since = now - changedAt;
        boolean reloading = mc.gui.overlay() instanceof LoadingOverlay;
        Phase shown = phase;
        // Applying ends when the reload overlay is gone; a failure shows for a few seconds.
        if (shown == Phase.APPLYING && !reloading && since > 1500) { phase = Phase.IDLE; return; }
        if (shown == Phase.FAILED && since > 6000) { phase = Phase.IDLE; return; }
        if (shown == Phase.IDLE) return;
        int w = g.guiWidth(), h = g.guiHeight(), y = (int) (h * 0.8325), half = (int) (Math.min(w * 0.75, h) * 0.5);
        int white = 0xFFFFFFFF;
        // While downloading we draw the bar ourselves; while applying, Remove Reloading Screen draws it in the same place.
        if (shown == Phase.DOWNLOADING || (shown == Phase.APPLYING && !reloading)) {
            float fraction = shown == Phase.APPLYING ? 1 : total > 0 ? Math.min(1, done / (float) total) : (now % 1600) / 1600f;
            int x0 = w / 2 - half, x1 = w / 2 + half, y0 = y - 5, y1 = y + 5;
            g.fill(x0 + 1, y0, x1 - 1, y0 + 1, white);
            g.fill(x0 + 1, y1 - 1, x1 - 1, y1, white);
            g.fill(x0, y0, x0 + 1, y1, white);
            g.fill(x1 - 1, y0, x1, y1, white);
            int filled = (int) Math.ceil((x1 - x0 - 4) * fraction);
            g.fill(x0 + 2, y0 + 2, x0 + 2 + filled, y1 - 2, white);
        }
        Component text = switch (shown) {
            case DOWNLOADING -> total > 0
                ? Component.translatable("holylois.loading.download_percent", Math.round(100f * done / total))
                : Component.translatable("holylois.loading.download");
            case APPLYING -> Component.translatable("holylois.loading.apply");
            default -> Component.translatable("holylois.loading.failed");
        };
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, y + 9);
        g.pose().scale(0.75f, 0.75f);
        g.centeredText(mc.font, text, 0, 0, shown == Phase.FAILED ? 0xFFFF5555 : white);
        g.pose().popMatrix();
    }
}
