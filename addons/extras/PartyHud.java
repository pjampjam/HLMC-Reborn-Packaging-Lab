package holylois.boombox;

import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Small optional party panel. No automatic member coordinates are received. */
public final class PartyHud {
    private static PartyState state = PartyState.empty();
    private static long receivedAt;
    private static final Path FILE = Path.of("config", "holylois-party-ui.json");
    static final class Options { boolean hud = true; boolean pings = true; }
    private static Options options = new Options();
    private static boolean warned;
    public static void register() {
        try { if (Files.exists(FILE)) options = new Gson().fromJson(Files.readString(FILE), Options.class); } catch (Exception ignored) {}
        if (options == null) options = new Options();
        ClientPlayNetworking.registerGlobalReceiver(PartyState.TYPE, (payload, context) -> { state = payload; receivedAt = System.currentTimeMillis(); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { state = PartyState.empty(); receivedAt = 0; });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommands.literal("partyhud")
            .executes(c -> { c.getSource().sendFeedback(Component.translatable("holylois.party.help")); return 1; })
            .then(ClientCommands.literal("on").executes(c -> setting(true, false)))
            .then(ClientCommands.literal("off").executes(c -> setting(false, false)))
            .then(ClientCommands.literal("pings").then(ClientCommands.literal("on").executes(c -> setting(true, true))).then(ClientCommands.literal("off").executes(c -> setting(false, true))))));
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("holylois", "party_hud"), (g, delta) -> {
            try { render(g); } catch (RuntimeException error) { if (!warned) org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Party HUD could not render", error); warned = true; }
        });
    }
    private static int setting(boolean value, boolean pings) {
        if (pings) options.pings = value; else options.hud = value;
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, new Gson().toJson(options)); }
        catch (Exception error) { Minecraft.getInstance().player.sendSystemMessage(Component.translatable("holylois.party.save_failed")); }
        return 1;
    }
    private static float safe(float v) { return Float.isFinite(v) ? Math.max(0, v) : 0; }
    private static void render(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden() || mc.gui.screen() != null || System.currentTimeMillis() - receivedAt > 3_000) return;
        int width = Math.min(202, g.guiWidth() - 12), x = g.guiWidth() - width - 6, y = 48;
        if (options.hud && !state.members().isEmpty()) {
            int visible = Math.min(Math.max(1, Math.min(6, (g.guiHeight() - 100) / 25)), state.members().size()), height = 16 + visible * 25 + (visible < state.members().size() ? 12 : 0);
            g.fill(x, y, x + width, y + height, 0xBE101216);
            g.text(mc.font, Component.translatable("holylois.party.title"), x + 6, y + 5, 0xFFFFD966); y += 16;
            for (var member : state.members().stream().limit(6).toList()) {
                g.text(mc.font, member.name(), x + 6, y, member.online() ? 0xFFF0F0F0 : 0xFF999999);
                if (!member.online()) g.text(mc.font, Component.translatable("holylois.party.offline"), x + 6, y + 10, 0xFF999999);
                else {
                    float health = safe(member.health()), maximum = Math.max(1, safe(member.maximum()));
                    int fill = Math.min(55, Math.round(55 * health / maximum));
                    g.fill(x + 6, y + 12, x + 61, y + 17, 0xFF40242A); g.fill(x + 6, y + 12, x + 6 + fill, y + 17, 0xFFE85462);
                    String hp = String.format(Locale.ROOT, "%.0f/%.0f", health, maximum);
                    if (member.absorption() > 0) hp += "+" + Math.round(safe(member.absorption()));
                    g.text(mc.font, hp, x + 66, y + 10, member.absorption() > 0 ? 0xFFFFD966 : 0xFFEAA9AF);
                    g.text(mc.font, Component.translatable("holylois.party.food", Math.max(0, Math.min(20, member.food()))), x + width - 58, y + 10, 0xFFD6B78E);
                    if (!member.sameDimension()) g.text(mc.font, "*", x + width - 10, y, 0xFFBBAADD);
                }
                y += 25;
            }
            if (visible < state.members().size()) { g.text(mc.font, "+" + (state.members().size() - visible), x + 6, y, 0xFFAAAAAA); y += 12; }
            y += 7;
        }
        var ping = state.rally();
        int seconds = ping == null ? 0 : ping.seconds() - (int)((System.currentTimeMillis() - receivedAt) / 1000);
        if (!options.pings || ping == null || seconds <= 0) return;
        g.fill(x, y, x + width, y + 40, 0xCE17241E);
        g.text(mc.font, Component.translatable("holylois.party.rally", ping.name()), x + 6, y + 5, 0xFF8EE3AA);
        boolean same = mc.level.dimension().identifier().toString().equals(ping.dimension());
        double bearing = Math.toDegrees(Math.atan2(-(ping.x() + .5 - mc.player.getX()), ping.z() + .5 - mc.player.getZ()));
        double relative = (bearing - mc.player.getYRot() + 540) % 360 - 180;
        String arrow = new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[Math.floorMod((int)Math.round(relative / 45), 8)];
        String line = same ? String.format(Locale.ROOT, "%s %.0f m  %d s", arrow, mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(ping.x() + .5, ping.y(), ping.z() + .5)), seconds) : Component.translatable("holylois.party.other_dimension").getString();
        g.text(mc.font, line, x + 6, y + 17, 0xFFE5EEE7);
        if (same) g.text(mc.font, ping.x() + ", " + ping.y() + ", " + ping.z(), x + 6, y + 28, 0xFFAAAAAA);
    }
}
