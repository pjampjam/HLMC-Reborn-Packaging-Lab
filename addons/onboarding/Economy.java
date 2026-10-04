package holylois;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** EconomyCraft bridge: daily coin state, plus balance changes for Holy Lois features (land claims). */
final class Economy {
    private Economy() {}

    private static boolean present() { return FabricLoader.getInstance().isModLoaded("economycraft"); }

    /** Coins /daily would pay now, or -1 when they were already claimed today or EconomyCraft is missing. */
    static long unclaimedDaily(MinecraftServer server, UUID id) {
        return present() ? Bridge.unclaimedDaily(server, id) : -1;
    }

    static long balance(MinecraftServer server, UUID id) { return present() ? Bridge.balance(server, id) : 0; }

    /** Takes coins only if the player has enough; false means nothing changed. */
    static boolean withdraw(MinecraftServer server, UUID id, long amount) { return present() && amount >= 0 && Bridge.withdraw(server, id, amount); }

    static void deposit(MinecraftServer server, UUID id, long amount) { if (present() && amount > 0) Bridge.deposit(server, id, amount); }

    // Separate class so EconomyCraft types load only when the mod is present.
    private static final class Bridge {
        static com.reazip.economycraft.EconomyManager manager(MinecraftServer server) { return com.reazip.economycraft.EconomyCraft.getManager(server); }
        static long unclaimedDaily(MinecraftServer server, UUID id) {
            var manager = manager(server);
            if (manager == null || manager.hasClaimedDailyToday(id)) return -1;
            return com.reazip.economycraft.EconomyConfig.get().dailyAmount;
        }
        static long balance(MinecraftServer server, UUID id) {
            var manager = manager(server);
            Long value = manager == null ? null : manager.getBalance(id, false);
            return value == null ? 0 : value;
        }
        static boolean withdraw(MinecraftServer server, UUID id, long amount) {
            var manager = manager(server);
            return manager != null && balance(server, id) >= amount && manager.removeMoney(id, amount);
        }
        static void deposit(MinecraftServer server, UUID id, long amount) {
            var manager = manager(server);
            if (manager != null) manager.addMoney(id, amount);
        }
    }
}
