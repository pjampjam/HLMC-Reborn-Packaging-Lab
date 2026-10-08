package holylois.boombox;

import com.ultimatearpg.item.ArpgCurrencyService;
import com.ultimatearpg.item.ArpgDataComponents;
import holylois.boombox.mixins.RuneforgedAcquisitionInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Consume only the native temporary acquisition marker, retaining every rolled component. */
public final class RuneforgedAcquisition {
    public static String source(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String source = stack.get(ArpgDataComponents.ADVANCEMENT_SOURCE);
        if (source == null || !ArpgCurrencyService.isCurrency(stack)) return null;
        return "chest_reward".equals(source) || "fishing_reward".equals(source)
            || "trade_reward".equals(source) ? source : null;
    }

    public static void acquired(ServerPlayer player, ItemStack stack) {
        if (source(stack) != null && PartySupport.ready(player))
            RuneforgedAcquisitionInvoker.holyLoisAcquired(player, stack);
    }
}
