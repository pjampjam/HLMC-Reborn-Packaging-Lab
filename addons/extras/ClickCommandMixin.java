package holylois.boombox.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Locale;
import java.util.Set;

/**
 * Clicking [Accept], a home name or the daily [Claim] opened "Confirm command" because the client cannot check the server's
 * permission rules for mod commands. These everyday player commands (none takes a signed chat message) run straight away;
 * everything else keeps the vanilla confirmation.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClickCommandMixin {
    @Unique private static final Set<String> SAFE = Set.of("home", "tpaccept", "tpdeny", "tpa", "tpahere", "daily", "spawn", "back",
        "warp", "claims", "rtp", "ah");

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisSafeClick(String command, Screen screen, CallbackInfo callback) {
        String root = command.strip().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("/")) root = root.substring(1);
        if (!SAFE.contains(root)) return;
        ((ClientPacketListener) (Object) this).send(new ServerboundChatCommandPacket(command.startsWith("/") ? command.substring(1) : command));
        Minecraft.getInstance().gui.setScreen(screen);
        callback.cancel();
    }
}
