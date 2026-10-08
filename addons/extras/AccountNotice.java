package holylois.boombox;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Private account form state, without passwords, hashes, recovery codes or addresses. */
public record AccountNotice(int kind,UUID grant,int minimum,boolean authenticated,boolean open,String message) implements CustomPacketPayload {
    public static final Type<AccountNotice> TYPE=new Type<>(Identifier.fromNamespaceAndPath("holylois","account_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AccountNotice> CODEC=CustomPacketPayload.codec((v,b)->{b.writeVarInt(v.kind());b.writeUUID(v.grant());b.writeVarInt(v.minimum());b.writeBoolean(v.authenticated());b.writeBoolean(v.open());b.writeUtf(v.message(),80);},b->new AccountNotice(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readUtf(80)));
    @Override public Type<AccountNotice> type(){return TYPE;}
}
