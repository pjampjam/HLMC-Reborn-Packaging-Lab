package holylois.auth;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server authority only. Never contains a password, account hash, or IP address. */
public record AuthStatus(int mode, int minimumLength) implements CustomPacketPayload {
    public static final Type<AuthStatus> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "auth_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuthStatus> CODEC =
        CustomPacketPayload.codec((value, buffer) -> {
            buffer.writeVarInt(value.mode()); buffer.writeVarInt(value.minimumLength());
        }, buffer -> new AuthStatus(buffer.readVarInt(), buffer.readVarInt()));
    @Override public Type<AuthStatus> type() { return TYPE; }
}
