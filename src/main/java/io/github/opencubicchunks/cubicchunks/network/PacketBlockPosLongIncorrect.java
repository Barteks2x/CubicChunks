package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.client.BlockPosLoadFailureScreenTrigger;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BlockPosAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

public class PacketBlockPosLongIncorrect {
    private final int packedXZ;

    public PacketBlockPosLongIncorrect(int packedXZ) {
        this.packedXZ = packedXZ;
    }

    PacketBlockPosLongIncorrect(FriendlyByteBuf buf) {
        this.packedXZ = buf.readVarInt();
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.packedXZ);
    }

    public static class Handler {
        public static void handle(PacketBlockPosLongIncorrect packet, Level worldIn) {
            if (packet.packedXZ != BlockPosAccess.getPackedXLength()) {
                BlockPosLoadFailureScreenTrigger.setBlockPosLongScreen(packet.packedXZ);
            }
        }
    }
}
