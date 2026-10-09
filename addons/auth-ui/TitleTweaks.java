package holylois.auth;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Title screen (owner round 5): Realms is no use on this pack, so its button joins Holy Lois: Reborn instead, straight into
 * the same panorama, connecting and login backdrop. The splash lines come from the website (assets/minecraft/texts).
 */
public final class TitleTweaks {
    private TitleTweaks() {}
    static final String ADDRESS = "play.holylois.com";

    static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof TitleScreen title)) return;
            var widgets = Screens.getWidgets(screen);
            for (int i = 0; i < widgets.size(); i++) {
                var widget = widgets.get(i);
                if (!(widget.getMessage().getContents() instanceof TranslatableContents text) || !text.getKey().equals("menu.online")) continue;
                var join = Button.builder(Component.literal("Holy Lois: Reborn").withStyle(ChatFormatting.YELLOW), b -> join(title))
                    .bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()).build();
                widgets.set(i, join);
                return;
            }
        });
    }

    private static void join(TitleScreen title) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        // The saved server-list entry keeps the player's own choices (like the server resource pack answer).
        ServerData server = null;
        var list = new net.minecraft.client.multiplayer.ServerList(mc);
        list.load();
        for (int i = 0; i < list.size() && server == null; i++) if (AuthPolicy.holyLoisAddress(list.get(i).ip)) server = list.get(i);
        if (server == null) server = new ServerData("Holy Lois: Reborn", ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(title, mc, ServerAddress.parseString(server.ip), server, false, null);
    }
}
