package holylois;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Reads EconomyCraft's daily coin state. Nothing here touches balances. */
final class Economy {
    private Economy() {}

    /** Coins /daily would pay now, or -1 when they were already claimed today or EconomyCraft is missing. */
    static long unclaimedDaily(MinecraftServer server, UUID id) {
        if (!FabricLoader.getInstance().isModLoaded("economycraft")) return -1;
        return Bridge.unclaimedDaily(server, id);
    }

    // Separate class so EconomyCraft types load only when the mod is present.
    private static final class Bridge {
        static long unclaimedDaily(MinecraftServer server, UUID id) {
            var manager = com.reazip.economycraft.EconomyCraft.getManager(server);
            if (manager == null || manager.hasClaimedDailyToday(id)) return -1;
            return com.reazip.economycraft.EconomyConfig.get().dailyAmount;
        }
    }
}
