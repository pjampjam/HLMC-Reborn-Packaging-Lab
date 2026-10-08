package holylois.boombox;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client: every item dropped by one of your deaths is gone (picked up, despawned or destroyed), so the client
 * removes the minimap death marker at that spot (XaeroDeathpoints). Sent by the onboarding add-on.
 */
public record DeathLootGone(Identifier dimension, BlockPos pos) implements CustomPacketPayload {
    public static final Type<DeathLootGone> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "death_loot_gone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeathLootGone> CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, DeathLootGone::dimension, BlockPos.STREAM_CODEC, DeathLootGone::pos, DeathLootGone::new);
    @Override public Type<DeathLootGone> type() { return TYPE; }

    static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }

    /** False when the player's client cannot receive it (an old pack); the marker then simply stays. */
    public static boolean send(ServerPlayer player, Identifier dimension, BlockPos pos) {
        if (!ServerPlayNetworking.canSend(player, TYPE)) return false;
        ServerPlayNetworking.send(player, new DeathLootGone(dimension, pos));
        return true;
    }
}
