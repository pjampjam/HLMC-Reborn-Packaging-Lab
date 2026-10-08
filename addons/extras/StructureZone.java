package holylois.boombox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server to client: the structure the player is standing in ("minecraft:ancient_city", a YUNG's dungeon), or "" after leaving.
 * Only the server knows structures; the client shows it as a zone title (ZoneTitles). Checked once a second per player,
 * using the structure pieces, so walking past a village's outskirts does not count.
 */
public record StructureZone(String structure, long start) implements CustomPacketPayload {
    public static final Type<StructureZone> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "structure_zone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructureZone> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(128), StructureZone::structure, ByteBufCodecs.VAR_LONG, StructureZone::start, StructureZone::new);
    @Override public Type<StructureZone> type() { return TYPE; }

    /** Too small or too common to announce. */
    private static final Set<String> QUIET = Set.of("buried_treasure", "nether_fossil", "ruined_portal");
    private static final Map<UUID, String> inside = new HashMap<>();

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerTickEvents.END_SERVER_TICK.register(StructureZone::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> inside.remove(handler.player.getUUID()));
    }

    static boolean quiet(Identifier id) {
        String path = id.getPath();
        return QUIET.stream().anyMatch(path::startsWith);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 7) return;
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, TYPE) || player.isSpectator()) continue;
            var level = (ServerLevel) player.level();
            var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            String now = "";
            long start = 0;
            try {
                var found = level.structureManager().getStructureWithPieceAt(player.blockPosition(),
                    holder -> holder.unwrapKey().map(key -> !quiet(key.identifier())).orElse(false));
                if (found != null && found.isValid()) {
                    var id = structures.getKey(found.getStructure());
                    if (id != null) { now = id.toString(); start = found.getChunkPos().pack(); }
                }
            } catch (RuntimeException error) {
                continue;
            }
            String key = now + "@" + start;
            if (key.equals(inside.get(player.getUUID()))) continue;
            inside.put(player.getUUID(), key);
            ServerPlayNetworking.send(player, new StructureZone(now, start));
        }
    }
}
