package holylois.boombox;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Private server snapshot sent only to authenticated members of this exact party. */
public record PartyState(UUID party, List<Member> members, Rally rally) implements CustomPacketPayload {
    public record Member(UUID id, String name, boolean online, boolean sameDimension, float health, float maximum, float absorption, int food, boolean owner) {}
    public record Rally(UUID author, String name, String dimension, int x, int y, int z, int seconds) {}
    public static final Type<PartyState> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "party_state"));
    public static PartyState empty() { return new PartyState(new UUID(0, 0), List.of(), null); }
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyState> CODEC = CustomPacketPayload.codec((value, b) -> {
        b.writeUUID(value.party()); b.writeVarInt(value.members().size());
        for (var m : value.members()) {
            b.writeUUID(m.id()); b.writeUtf(m.name(), 32); b.writeBoolean(m.online()); b.writeBoolean(m.sameDimension());
            b.writeFloat(m.health()); b.writeFloat(m.maximum()); b.writeFloat(m.absorption()); b.writeVarInt(m.food());b.writeBoolean(m.owner());
        }
        b.writeBoolean(value.rally() != null);
        if (value.rally() != null) {
            var p = value.rally(); b.writeUUID(p.author()); b.writeUtf(p.name(), 32); b.writeUtf(p.dimension(), 128);
            b.writeInt(p.x()); b.writeInt(p.y()); b.writeInt(p.z()); b.writeVarInt(p.seconds());
        }
    }, b -> {
        UUID party = b.readUUID(); int count = b.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid party size");
        var members = new ArrayList<Member>(count);
        for (int i = 0; i < count; i++) members.add(new Member(b.readUUID(), b.readUtf(32), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(),b.readBoolean()));
        Rally rally = b.readBoolean() ? new Rally(b.readUUID(), b.readUtf(32), b.readUtf(128), b.readInt(), b.readInt(), b.readInt(), b.readVarInt()) : null;
        return new PartyState(party, List.copyOf(members), rally);
    });
    @Override public Type<PartyState> type() { return TYPE; }
}
