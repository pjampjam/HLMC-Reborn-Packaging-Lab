package holylois.boombox;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client, sent just before a player dies to another player: the client skips the minimap death marker for
 * that death (PvP loot belongs to the winner, so nobody gets a pointer back to it).
 */
public record PvpDeath() implements CustomPacketPayload {
    public static final Type<PvpDeath> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "pvp_death"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PvpDeath> CODEC = StreamCodec.unit(new PvpDeath());
    @Override public Type<PvpDeath> type() { return TYPE; }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            try {
                if (entity instanceof ServerPlayer player && (source.getEntity() instanceof ServerPlayer || player.getKillCredit() instanceof ServerPlayer)
                    && ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, new PvpDeath());
            } catch (RuntimeException ignored) {
                // Only the minimap marker depends on this; the death itself always goes ahead.
            }
            return true;
        });
    }
}
