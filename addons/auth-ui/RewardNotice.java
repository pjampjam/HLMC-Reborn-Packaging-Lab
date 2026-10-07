package holylois.auth;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A receipt for already-delivered rewards, never a client request to grant anything. */
public record RewardNotice(String item, int count, int streak, long coins, long waiting) implements CustomPacketPayload {
    public static final Type<RewardNotice> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "reward_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RewardNotice> CODEC = CustomPacketPayload.codec((v, b) -> {
        b.writeUtf(v.item(), 128); b.writeVarInt(v.count()); b.writeVarInt(v.streak()); b.writeVarLong(v.coins()); b.writeVarLong(v.waiting());
    }, b -> new RewardNotice(b.readUtf(128), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarLong()));
    @Override public Type<RewardNotice> type() { return TYPE; }
}
