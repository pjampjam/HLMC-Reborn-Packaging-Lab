package holylois.boombox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A fish of 10 kg and more needs both hands (owner round 4): it cannot sit in the off hand, and while one is held in the main
 * hand the off hand stays empty. Whatever is in the way goes into the inventory, or drops at your feet when it is full.
 */
public final class FishHands {
    private FishHands() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) check(player);
        });
    }

    static void check(ServerPlayer player) {
        var off = player.getOffhandItem();
        if (off.isEmpty() || player.isSpectator()) return;
        if (!FishData.twoHanded(off) && !FishData.twoHanded(player.getMainHandItem())) return;
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.getInventory().add(off);
        if (!off.isEmpty()) player.drop(off, false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.sendOverlayMessage(Component.translatable("holylois.fish.two_hands"));
    }
}
