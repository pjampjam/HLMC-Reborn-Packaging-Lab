package holylois.boombox;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: a boombox is playing within earshot, so the client pauses the game's own music. */
public record BoomboxNear(boolean near) implements CustomPacketPayload {
    public static final Type<BoomboxNear> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "boombox_near"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoomboxNear> CODEC =
        CustomPacketPayload.codec((value, buffer) -> buffer.writeBoolean(value.near()), buffer -> new BoomboxNear(buffer.readBoolean()));
    @Override public Type<BoomboxNear> type() { return TYPE; }
}
