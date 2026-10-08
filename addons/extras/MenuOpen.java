package holylois.boombox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record MenuOpen(int menu) implements CustomPacketPayload {
    public static final Type<MenuOpen> TYPE=new Type<>(Identifier.fromNamespaceAndPath("holylois","menu_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MenuOpen> CODEC=CustomPacketPayload.codec((v,b)->b.writeVarInt(v.menu()),b->{int menu=b.readVarInt();if(menu<1||menu>2)throw new IllegalArgumentException("Invalid menu");return new MenuOpen(menu);});
    @Override public Type<MenuOpen> type(){return TYPE;}
}
