package holylois.boombox;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded action arguments, never client coordinates or an acting player's UUID. */
public record TravelIntent(int action, String name, String nextName, long revision) implements CustomPacketPayload {
    public static final int HOMES=0,SAVE=1,GO=2,RENAME=3,DELETE=4,CREATE_PARTY=5,INVITE=6,ACCEPT=7,LEAVE=8,RALLY=9,JOIN_RALLY=10,DESTROY_PARTY=11;
    public static final Type<TravelIntent> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "travel_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TravelIntent> CODEC = CustomPacketPayload.codec((v,b) -> {
        b.writeVarInt(v.action());b.writeUtf(v.name(),128);b.writeUtf(v.nextName(),32);b.writeLong(v.revision());
    }, b -> {
        int action=b.readVarInt();if(action<0 || action>11)throw new IllegalArgumentException("Invalid travel action");
        return new TravelIntent(action,b.readUtf(128),b.readUtf(32),b.readLong());
    });
    @Override public Type<TravelIntent> type() { return TYPE; }
}
