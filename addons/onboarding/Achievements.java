package holylois;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * Grants the Holy Lois advancements that vanilla triggers cannot see. Their criteria are "minecraft:impossible"
 * in the holylois-advancements datapack, so only this code can complete them.
 */
public final class Achievements {
    private Achievements() {}

    static void award(MinecraftServer server, ServerPlayer player, String advancement, String criterion) {
        var holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath("holylois", advancement));
        if (holder != null) player.getAdvancements().award(holder, criterion);
    }

    /** Chair style of a Macaw's Furniture seat block, or null when it is not a seat. */
    static String chairStyle(String blockId) {
        if (!blockId.startsWith("mcwfurnitures:")) return null;
        String path = blockId.substring("mcwfurnitures:".length());
        for (String style : new String[] {"modern_chair", "stool_chair", "striped_chair", "couch"})
            if (path.endsWith("_" + style)) return style;
        return path.endsWith("_chair") ? "chair" : null;
    }

    /** Every five seconds per player: seats. Every minute: distance and playtime. */
    static void check(MinecraftServer server, ServerPlayer player, int tick) {
        var vehicle = player.getVehicle();
        if (vehicle != null && BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getNamespace().equals("mcwfurnitures")) {
            for (var pos : new net.minecraft.core.BlockPos[] {vehicle.blockPosition(), vehicle.blockPosition().below()}) {
                String style = chairStyle(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString());
                if (style == null) continue;
                award(server, player, "furniture/take_a_seat", "sat");
                award(server, player, "furniture/musical_chairs", style);
                break;
            }
        }
        if (tick % 1200 != 0) return;
        if (java.time.LocalTime.now(DailyRewards.RIGA).getHour() == 3) award(server, player, "fun/night_owl", "done");
        if (Economy.unclaimedDaily(server, player.getUUID()) < 0 && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("economycraft"))
            award(server, player, "daily/pocket_money", "done");
        var stats = player.getStats();
        long centimetres = 0;
        // Iterate the registered values: Stats.CUSTOM.get needs the registry's own instances, not equal keys.
        for (var id : BuiltInRegistries.CUSTOM_STAT)
            if (id.getPath().endsWith("_one_cm") && !id.getPath().contains("fall")) centimetres += stats.getValue(Stats.CUSTOM.get(id));
        if (centimetres >= 100L * 100_000) award(server, player, "travel/long_way_home", "done");
        if (centimetres >= 1000L * 100_000) award(server, player, "travel/around_the_world", "done");
        long hours = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000L;
        if (hours >= 24) award(server, player, "time/regular", "done");
        if (hours >= 100) award(server, player, "time/resident", "done");
    }

    static void streak(MinecraftServer server, ServerPlayer player, int streak) {
        if (streak >= 7) award(server, player, "daily/seven_days", "done");
        if (streak >= 28) award(server, player, "daily/devoted", "done");
    }
}
