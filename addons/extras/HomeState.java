package holylois.boombox;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Only the requesting authenticated player receives their native home names. */
public record HomeState(boolean open, int limit, long revision, List<Home> homes) implements CustomPacketPayload {
    public record Home(String name, String dimension) {}
    public static final Type<HomeState> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "homes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HomeState> CODEC = CustomPacketPayload.codec((v,b) -> {
        b.writeBoolean(v.open());b.writeVarInt(v.limit());b.writeLong(v.revision());b.writeVarInt(v.homes().size());
        for (var h:v.homes()) { b.writeUtf(h.name(),128);b.writeUtf(h.dimension(),128); }
    }, b -> {
        boolean open=b.readBoolean();int limit=b.readVarInt();long rev=b.readLong();int count=b.readVarInt();
        if (limit<0 || limit>128 || count<0 || count>128) throw new IllegalArgumentException("Invalid home count");
        var homes=new ArrayList<Home>();for(int i=0;i<count;i++) homes.add(new Home(b.readUtf(128),b.readUtf(128)));
        return new HomeState(open,limit,rev,List.copyOf(homes));
    });
    @Override public Type<HomeState> type() { return TYPE; }
}
