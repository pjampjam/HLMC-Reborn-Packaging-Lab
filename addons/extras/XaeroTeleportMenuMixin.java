package holylois.boombox.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.server.permissions.Permissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/**
 * Players without /tp rights never see Xaero's world map teleport entries (map, waypoint); on a player the entry becomes a
 * /tpa request (XaeroTpaOption). Operators keep Xaero's real teleports.
 */
@Pseudo
@Mixin(targets = "xaero.map.gui.dropdown.rightclick.GuiRightClickMenu")
public abstract class XaeroTeleportMenuMixin {
    @Redirect(method = "getMenu", at = @At(value = "INVOKE",
        target = "Lxaero/map/gui/IRightClickableElement;getRightClickOptions()Ljava/util/ArrayList;"))
    private static ArrayList<RightClickOption> holyLoisHideTeleport(IRightClickableElement target) {
        ArrayList<RightClickOption> options = target.getRightClickOptions();
        var player = Minecraft.getInstance().player;
        if (options == null || player == null || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return options;
        var visible = new ArrayList<RightClickOption>();
        for (var option : options) {
            String name = ((XaeroOptionAccessor) option).holylois$name();
            if (name.equals("gui.xaero_right_click_player_teleport")) {
                var tpa = holylois.boombox.XaeroTpaOption.replace(option, visible.size(), target);
                if (tpa != null) visible.add(tpa);
            } else if (!name.contains("teleport")) visible.add(option);
        }
        return visible;
    }
}
