package holylois.boombox.mixins;

import holylois.boombox.FishData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F in the world: a heavy fish stays in the main hand and nothing joins it in the off hand. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FishHandsSwapMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true, require = 1)
    private void holyLoisKeepBothHands(ServerboundPlayerActionPacket packet, CallbackInfo info) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        // The packet arrives on the network thread first; vanilla then re-runs it on the server thread, where we decide.
        if (!player.level().getServer().isSameThread()) return;
        if (!FishData.twoHanded(player.getMainHandItem()) && !FishData.twoHanded(player.getOffhandItem())) return;
        player.sendOverlayMessage(Component.translatable("holylois.fish.two_hands"));
        player.inventoryMenu.sendAllDataToRemote();
        info.cancel();
    }
}
