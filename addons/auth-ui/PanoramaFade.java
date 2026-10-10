package holylois.auth;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The way into the world (owner round 5): title, connecting, loading and the login form all show the same blurred panorama;
 * once signed in (or once loading ends with a remembered session), the last frame is captured and fades out over the world
 * instead of cutting to it. The cube map itself cannot be drawn with alpha, so the captured frame stands in for it.
 */
public final class PanoramaFade {
    private PanoramaFade() {}
    private static final Identifier ID = Identifier.fromNamespaceAndPath("holylois", "dynamic/entry_fade");
    /** Milliseconds for the fade. */
    static final long LENGTH = 1100;
    private static DynamicTexture texture;
    private static int imageWidth, imageHeight;
    private static long started;
    private static boolean capturing;

    /** The world was entered on this connection: a later loading screen (respawn, portal) closes without the fade (owner 2026-10-10). */
    private static boolean entered;

    static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> entered = false);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> entered = false);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("holylois", "entry_fade"), (g, delta) -> {
            if (texture == null) return;
            float t = (System.currentTimeMillis() - started) / (float) LENGTH;
            if (t >= 1) { release(); return; }
            // Ease out: the world shows through quickly, the last of the backdrop melts away.
            float alpha = (1 - t) * (1 - t);
            int a = Math.round(alpha * 255) << 24 | 0xFFFFFF;
            g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), imageWidth, imageHeight, imageWidth, imageHeight, a);
        });
    }

    private static boolean closing;
    private static long captureAt;

    /**
     * Gui.setScreen(null) while the loading screen or the login form is up and a world is there: keep the screen until its
     * last frame is captured, then close it and fade the capture. True while the close is held back.
     */
    public static boolean holdsClose(net.minecraft.client.gui.screens.Screen current, net.minecraft.client.gui.Gui gui) {
        var mc = Minecraft.getInstance();
        if (closing || mc.level == null || mc.player == null) return false;
        boolean loading = current instanceof net.minecraft.client.gui.screens.LevelLoadingScreen;
        if (!loading && !(current instanceof HolyLoisAuthScreen)) return false;
        if (loading && entered && !capturing) return false;
        if (capturing) {
            // A capture that never came back must not keep the screen forever.
            if (System.currentTimeMillis() - captureAt < 1500) return true;
            capturing = false;
            return false;
        }
        capturing = true; captureAt = System.currentTimeMillis(); entered = true;
        Runnable close = () -> { closing = true; try { if (gui.screen() == current) gui.setScreen(null); } finally { closing = false; } };
        try {
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> mc.execute(() -> {
                // Gone already (or another screen took over, like the login form): nothing to fade.
                if (!capturing || gui.screen() != current) { capturing = false; image.close(); return; }
                capturing = false;
                release();
                imageWidth = image.getWidth(); imageHeight = image.getHeight();
                texture = new DynamicTexture(() -> "holylois entry fade", image);
                mc.getTextureManager().register(ID, texture);
                started = System.currentTimeMillis();
                close.run();
            }));
        } catch (RuntimeException | LinkageError error) {
            capturing = false;
            return false;
        }
        return true;
    }

    /** Test hook: fading right now. */
    public static boolean fading() { return texture != null; }

    private static void release() {
        if (texture == null) return;
        Minecraft.getInstance().getTextureManager().release(ID);
        texture = null;
    }
}
