package holylois.boombox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.radar.tracker.PlayerTrackerMapElement;

/** World map right-click on a player, for players without /tp: sends a normal /tpa request instead (XaeroTeleportMenuMixin). */
public final class XaeroTpaOption extends RightClickOption {
    private final String player;

    private XaeroTpaOption(String player, int index, IRightClickableElement target) {
        super("holylois.map.tpa", index, target);
        this.player = player;
        setNameFormatArgs(player);
    }

    /** Replaces Xaero's "teleport to player" entry, or null when the player behind it is unknown. */
    public static RightClickOption replace(RightClickOption teleport, int index, IRightClickableElement target) {
        try {
            for (var field : teleport.getClass().getDeclaredFields()) {
                if (!PlayerTrackerMapElement.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                var element = (PlayerTrackerMapElement<?>) field.get(teleport);
                var connection = Minecraft.getInstance().getConnection();
                var info = connection == null || element == null ? null : connection.getPlayerInfo(element.getPlayerId());
                return info == null ? null : new XaeroTpaOption(info.getProfile().name(), index, target);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return null;
    }

    @Override public void onAction(Screen screen) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        mc.getConnection().sendCommand("tpa " + player);
        mc.gui.setScreen(null);
    }
}
