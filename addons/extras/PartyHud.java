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
    static final class Options { boolean hud = false; boolean automatic = true; boolean pings = true; }
    private static Options options = new Options();
    private static boolean warned;
    private static boolean collapsed;
    private static int partyX,partyY,rallyX,rallyY,hitWidth,rallyHeight;
    private static boolean partyHit,rallyHit;
    public static void collapseRally(){collapsed=true;}
    public static boolean click(double x,double y,int button){
        var mc=Minecraft.getInstance();
        if(button!=1 || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) || current().members().isEmpty())return false;
        if(rallyHit && x>=rallyX && x<rallyX+hitWidth && y>=rallyY && y<rallyY+rallyHeight){
            if(x>=rallyX+hitWidth-22 && !collapsed){collapsed=true;return true;}
            collapsed=false;mc.gui.setScreen(new RallyScreen());return true;
        }
        if(partyHit && x>=partyX && x<partyX+hitWidth && y>=partyY && y<partyY+16){mc.gui.setScreen(new PartyScreen());return true;}
        return false;
    }
    static PartyState current(){return System.currentTimeMillis()-receivedAt<3000?state:PartyState.empty();}
    public static void register() {
        try { if (Files.exists(FILE)) {var saved=Files.readString(FILE);options = new Gson().fromJson(saved, Options.class);if(options!=null&&!com.google.gson.JsonParser.parseString(saved).getAsJsonObject().has("automatic"))options.automatic=false;} } catch (Exception ignored) {}
        if (options == null) options = new Options();
        ClientPlayNetworking.registerGlobalReceiver(PartyState.TYPE, (payload, context) -> {
            var old=state.rally();var next=payload.rally();
            if(next==null || old==null || !Objects.equals(state.party(),payload.party()) || !next.author().equals(old.author())
                    || !next.dimension().equals(old.dimension()) || next.x()!=old.x() || next.y()!=old.y() || next.z()!=old.z() || next.seconds()>old.seconds()+2)collapsed=false;
            state = payload; receivedAt = System.currentTimeMillis();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { state = PartyState.empty(); receivedAt = 0;collapsed=false;partyHit=false;rallyHit=false; });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommands.literal("partyhud")
            .executes(c -> { c.getSource().sendFeedback(Component.translatable("holylois.party.help")); return 1; })
            .then(ClientCommands.literal("on").executes(c -> setting(true, false)))
            .then(ClientCommands.literal("off").executes(c -> setting(false, false)))
            .then(ClientCommands.literal("auto").executes(c -> automatic()))
            .then(ClientCommands.literal("pings").then(ClientCommands.literal("on").executes(c -> setting(true, true))).then(ClientCommands.literal("off").executes(c -> setting(false, true))))));
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("holylois", "party_hud"), (g, delta) -> {
            try { render(g); } catch (RuntimeException error) { if (!warned) org.slf4j.LoggerFactory.getLogger("HolyLois").warn("Party HUD could not render", error); warned = true; }
        });
    }
    private static int setting(boolean value, boolean pings) {
        if (pings) options.pings = value; else {options.hud = value;options.automatic=false;}
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, new Gson().toJson(options)); }
        catch (Exception error) { Minecraft.getInstance().player.sendSystemMessage(Component.translatable("holylois.party.save_failed")); }
        return 1;
    }
    private static int automatic(){options.hud=false;options.automatic=true;try{Files.createDirectories(FILE.getParent());Files.writeString(FILE,new Gson().toJson(options));}catch(Exception error){Minecraft.getInstance().player.sendSystemMessage(Component.translatable("holylois.party.save_failed"));}return 1;}
    private static float safe(float v) { return Float.isFinite(v) ? Math.max(0, v) : 0; }
    /** Right edge, under the status effect icons: the minimap and voice icons own the top-left, Jade the top centre. */
    private static void render(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        partyHit=false;rallyHit=false;
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden() || (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) || System.currentTimeMillis() - receivedAt > 3_000) return;
        int width = Math.min(150, g.guiWidth() - 12), x = g.guiWidth() - width - 6, y = 54;
        hitWidth=width;
        if ((options.hud || options.automatic && state.members().size()>1) && !state.members().isEmpty()) {
            int visible = Math.min(Math.max(1, Math.min(6, (g.guiHeight() - 110) / 24)), state.members().size()), height = 18 + visible * 24 + (visible < state.members().size() ? 11 : 0);
            Ui.card(g, x, y, width, height, Ui.GOLD, 1);
            partyX=x;partyY=y;partyHit=true;
            g.text(mc.font, Component.translatable("holylois.party.title"), x + 8, y + 6, Ui.GOLD, false);
            String count = String.valueOf(state.members().size());
            g.text(mc.font, count, x + width - 8 - mc.font.width(count), y + 6, Ui.MUTED, false);
            y += 18;
            for (var member : state.members().stream().limit(visible).toList()) {
                g.text(mc.font, mc.font.plainSubstrByWidth(member.name(), width - 30), x + 8, y, member.online() ? Ui.TEXT : Ui.MUTED, false);
                if (!member.sameDimension() && member.online()) g.text(mc.font, "*", x + width - 12, y, 0xFFBBAADD, false);
                if (!member.online()) g.text(mc.font, Component.translatable("holylois.party.offline"), x + 8, y + 10, Ui.alpha(Ui.MUTED, 0.7f), false);
                else {
                    float health = safe(member.health()), maximum = Math.max(1, safe(member.maximum()));
                    int barWidth = width - 62;
                    Ui.bar(g, x + 8, y + 11, barWidth, 4, health / maximum, Ui.HEALTH, Ui.alpha(Ui.HEALTH, 0.22f));
                    if (member.absorption() > 0) g.fill(x + 8, y + 11, x + 8 + Math.min(barWidth, Math.round(barWidth * safe(member.absorption()) / maximum)), y + 12, Ui.GOLD);
                    String hp = String.format(Locale.ROOT, "%.0f", health) + (member.absorption() > 0 ? "+" + Math.round(safe(member.absorption())) : "");
                    g.text(mc.font, hp, x + 12 + barWidth, y + 9, member.absorption() > 0 ? Ui.GOLD : Ui.TEXT, false);
                    String food = String.valueOf(Math.max(0, Math.min(20, member.food())));
                    g.text(mc.font, food, x + width - 8 - mc.font.width(food), y + 9, Ui.FOOD, false);
                }
                y += 24;
            }
            if (visible < state.members().size()) { g.text(mc.font, "+" + (state.members().size() - visible), x + 8, y - 2, Ui.MUTED, false); y += 11; }
            y += 6;
        }
        var ping = state.rally();
        int seconds = ping == null ? 0 : ping.seconds() - (int)((System.currentTimeMillis() - receivedAt) / 1000);
        if (!options.pings || ping == null || seconds <= 0) return;
        rallyX=x;rallyY=y;rallyHeight=collapsed?20:54;rallyHit=true;
        Ui.card(g, x, y, width, rallyHeight, Ui.GREEN, 1);
        var title=Component.translatable("holylois.party.rally", ping.name()).getString();
        g.text(mc.font, mc.font.plainSubstrByWidth(title,width-30), x + 8, y + 6, Ui.SUCCESS, false);
        g.text(mc.font,collapsed?"+":"-",x+width-13,y+6,Ui.MUTED,false);
        if(collapsed)return;
        boolean same = mc.level.dimension().identifier().toString().equals(ping.dimension());
        double bearing = Math.toDegrees(Math.atan2(-(ping.x() + .5 - mc.player.getX()), ping.z() + .5 - mc.player.getZ()));
        double relative = (bearing - mc.player.getYRot() + 540) % 360 - 180;
        String arrow = new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[Math.floorMod((int)Math.round(relative / 45), 8)];
        String line = same ? String.format(Locale.ROOT, "%s %.0f m  %d s", arrow, mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(ping.x() + .5, ping.y(), ping.z() + .5)), seconds) : Component.translatable("holylois.party.other_dimension").getString();
        g.text(mc.font, line, x + 8, y + 18, Ui.TEXT, false);
        if (same) g.text(mc.font, ping.x() + ", " + ping.y() + ", " + ping.z(), x + 8, y + 29, Ui.MUTED, false);
        g.text(mc.font,mc.font.plainSubstrByWidth(Component.translatable("holylois.party.rally_open").getString(),width-16),x+8,y+41,Ui.alpha(Ui.SUCCESS,0.8f),false);
    }
}
