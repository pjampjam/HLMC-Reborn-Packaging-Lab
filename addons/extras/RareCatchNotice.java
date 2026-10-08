package holylois.boombox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record RareCatchNotice(String item,String rarity,double kilograms) implements CustomPacketPayload {
    public static final Type<RareCatchNotice> TYPE=new Type<>(Identifier.fromNamespaceAndPath("holylois","rare_catch"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RareCatchNotice> CODEC=CustomPacketPayload.codec((v,b)->{b.writeUtf(v.item(),128);b.writeUtf(v.rarity(),32);b.writeDouble(v.kilograms());},b->{
        String item=b.readUtf(128),rarity=b.readUtf(32);double weight=b.readDouble();if(!Double.isFinite(weight)||weight<0||weight>1_000_000)throw new IllegalArgumentException("Invalid fish weight");return new RareCatchNotice(item,rarity,weight);
    });
    @Override public Type<RareCatchNotice> type(){return TYPE;}
}
