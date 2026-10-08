package holylois.boombox;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Secrets travel in a bounded packet, never a chat command. */
public record AccountIntent(int kind,UUID grant,String value,String confirmation,String token) implements CustomPacketPayload {
    public static final Type<AccountIntent> TYPE=new Type<>(Identifier.fromNamespaceAndPath("holylois","account_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AccountIntent> CODEC=CustomPacketPayload.codec((v,b)->{b.writeVarInt(v.kind());b.writeUUID(v.grant());b.writeUtf(v.value(),100);b.writeUtf(v.confirmation(),100);b.writeUtf(v.token(),64);},b->new AccountIntent(b.readVarInt(),b.readUUID(),b.readUtf(100),b.readUtf(100),b.readUtf(64)));
    @Override public String toString(){return "AccountIntent[secrets redacted]";}
    @Override public Type<AccountIntent> type(){return TYPE;}
}
