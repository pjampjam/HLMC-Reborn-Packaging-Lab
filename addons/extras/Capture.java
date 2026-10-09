package holylois.boombox;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Operator capture tools for the title panorama and website shots (client commands, operators only):
 * /capture ultra      switch to the capture profile (render distance 32, far Distant Horizons, shaders on) and back;
 *                     the player's own values are kept in config/holylois-capture.json until restored.
 * /capture panorama [seconds]  six 90-degree faces in vanilla's title order (panorama_0..5: front, right, back, left, up,
 *                     down), HUD hidden, the shader given [seconds] (default 3) to settle on each face, saved as square crops
 *                     of the window to screenshots/holylois-panorama-DATE. Keep the mouse still while it runs.
 */
final class Capture {
    private Capture() {}
    private static final Path SAVED = Path.of("config", "holylois-capture.json");
    static final class Saved { int renderDistance; boolean shaders; boolean dh; }

    private static int face = -1, wait, settle;
    private static float yaw;
    private static Path folder;
    private static int oldFov; private static double oldFovEffect; private static boolean oldBob, hudWasHidden;

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommands.literal("capture")
            .requires(source -> allowed())
            .executes(c -> { say("/capture ultra: capture profile on/off. /capture panorama [seconds]: six faces for the title panorama."); return 1; })
            .then(ClientCommands.literal("ultra").executes(c -> ultra()))
            .then(ClientCommands.literal("panorama").executes(c -> panorama(3))
                .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, 20)).executes(c -> panorama(IntegerArgumentType.getInteger(c, "seconds")))))));
        ClientTickEvents.END_CLIENT_TICK.register(Capture::tick);
    }

    private static boolean allowed() {
        var mc = Minecraft.getInstance();
        return mc.player != null && (mc.hasSingleplayerServer() || mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    private static void say(String text) { var p = Minecraft.getInstance().player; if (p != null) p.sendSystemMessage(Component.literal(text).withStyle(s -> s.withColor(Ui.GOLD & 0xFFFFFF))); }

    private static int ultra() {
        var options = Minecraft.getInstance().options;
        var gson = new Gson();
        try {
            if (Files.exists(SAVED)) {
                var saved = gson.fromJson(Files.readString(SAVED), Saved.class);
                options.renderDistance().set(saved.renderDistance);
                if (FabricLoader.getInstance().isModLoaded("distanthorizons")) Dh.restore();
                if (FabricLoader.getInstance().isModLoaded("iris")) Shaders.set(saved.shaders);
                options.save();
                Files.delete(SAVED);
                say("Capture profile off: your own settings are back.");
                return 1;
            }
            var saved = new Saved();
            saved.renderDistance = options.renderDistance().get();
            saved.shaders = FabricLoader.getInstance().isModLoaded("iris") && Shaders.on();
            Files.createDirectories(SAVED.getParent());
            Files.writeString(SAVED, gson.toJson(saved));
            options.renderDistance().set(32);
            if (FabricLoader.getInstance().isModLoaded("distanthorizons")) Dh.far();
            if (FabricLoader.getInstance().isModLoaded("iris")) Shaders.set(true);
            options.save();
            say("Capture profile on: render distance 32, far Distant Horizons, shaders on. Pick the shader quality in the Iris menu once. /capture ultra again to restore.");
            return 1;
        } catch (Exception error) {
            say("Capture profile failed: " + error.getMessage());
            return 0;
        }
    }

    private static int panorama(int seconds) {
        var mc = Minecraft.getInstance();
        if (face >= 0 || mc.player == null) return 0;
        folder = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve("holylois-panorama-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        settle = seconds * 20;
        yaw = mc.player.getYRot();
        oldFov = mc.options.fov().get(); oldFovEffect = mc.options.fovEffectScale().get(); oldBob = mc.options.bobView().get();
        hudWasHidden = mc.gui.hud.isHidden();
        mc.options.fov().set(90); mc.options.fovEffectScale().set(0.0); mc.options.bobView().set(false);
        if (!hudWasHidden) mc.gui.hud.toggle();
        face = 0; wait = settle;
        return 1;
    }

    /** Vanilla's panorama order (Minecraft.grabPanoramixScreenshot): front, right, back, left, up, down. */
    private static void aim(net.minecraft.world.entity.player.Player player, int index) {
        float y = switch (index) { case 1 -> yaw + 90; case 2 -> yaw + 180; case 3 -> yaw - 90; default -> yaw; };
        float x = index == 4 ? -90 : index == 5 ? 90 : 0;
        player.setYRot(y); player.setXRot(x); player.yRotO = y; player.xRotO = x;
    }

    private static void tick(Minecraft mc) {
        if (face < 0) return;
        if (mc.player == null) { finish(mc, "Panorama stopped."); return; }
        aim(mc.player, face);
        if (--wait > 0) return;
        int index = face;
        Path file = folder.resolve("panorama_" + index + ".png");
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                int side = Math.min(image.getWidth(), image.getHeight());
                try (var square = new NativeImage(side, side, false)) {
                    image.copyRect(square, (image.getWidth() - side) / 2, (image.getHeight() - side) / 2, 0, 0, side, side, false, false);
                    Files.createDirectories(file.getParent());
                    square.writeToFile(file);
                }
            } catch (Exception error) {
                org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Panorama face {} not saved", index, error);
            }
        });
        if (++face > 5) finish(mc, "Panorama saved: " + folder + " (6 faces, " + mc.getWindow().getHeight() + " px each).");
        else wait = settle;
    }

    private static void finish(Minecraft mc, String message) {
        face = -1;
        mc.options.fov().set(oldFov); mc.options.fovEffectScale().set(oldFovEffect); mc.options.bobView().set(oldBob);
        if (!hudWasHidden && mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        if (mc.player != null) aim(mc.player, 0);
        say(message);
    }

    // Separate classes so Iris and Distant Horizons types load only when those mods are present.
    private static final class Shaders {
        static boolean on() { return net.irisshaders.iris.api.v0.IrisApi.getInstance().getConfig().areShadersEnabled(); }
        static void set(boolean value) { if (on() != value) net.irisshaders.iris.api.v0.IrisApi.getInstance().getConfig().setShadersEnabledAndApply(value); }
    }

    private static final class Dh {
        static void far() {
            var graphics = com.seibel.distanthorizons.api.DhApi.Delayed.configs.graphics();
            graphics.renderingEnabled().setValue(true);
            graphics.chunkRenderDistance().setValue(Math.min(graphics.chunkRenderDistance().getMaxValue(), 512));
        }
        static void restore() {
            var graphics = com.seibel.distanthorizons.api.DhApi.Delayed.configs.graphics();
            graphics.chunkRenderDistance().clearValue();
            graphics.renderingEnabled().clearValue();
        }
    }
}
