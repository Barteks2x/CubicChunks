package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.chunk.IClientCubeProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

public class UpdateClientChunkSectionArray {
    private final int directionOrdinal;

    public UpdateClientChunkSectionArray(Direction.AxisDirection direction) {
        this.directionOrdinal = direction.ordinal();
    }

    UpdateClientChunkSectionArray(FriendlyByteBuf buf) {
        this.directionOrdinal = buf.readVarInt();
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.directionOrdinal);
    }

    public static class Handler {
        public static void handle(UpdateClientChunkSectionArray packet, Level worldIn) {
            ((IClientCubeProvider) worldIn.getChunkSource()).updateChunkSectionArrays(Direction.AxisDirection.values()[packet.directionOrdinal]);
        }
    }
}
