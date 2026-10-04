package holylois.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: sneak + scroll turns a boombox up or down, the placed one in the crosshair (pos) or the held one. */
public record BoomboxVolume(BlockPos pos, int step) implements CustomPacketPayload {
    public static final Type<BoomboxVolume> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holylois", "boombox_volume"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoomboxVolume> CODEC = CustomPacketPayload.codec((value, buffer) -> {
        buffer.writeBoolean(value.pos() != null);
        if (value.pos() != null) buffer.writeBlockPos(value.pos());
        buffer.writeByte(value.step());
    }, buffer -> new BoomboxVolume(buffer.readBoolean() ? buffer.readBlockPos() : null, Integer.signum(buffer.readByte())));
    @Override public Type<BoomboxVolume> type() { return TYPE; }
}
