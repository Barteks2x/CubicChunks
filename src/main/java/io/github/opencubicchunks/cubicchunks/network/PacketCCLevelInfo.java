package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

//TODO: Find an earlier point to call this packet, preferably right after client world construction.
public class PacketCCLevelInfo {
    private final String worldStyle;

    public PacketCCLevelInfo(CubicLevelHeightAccessor.WorldStyle worldStyle) {
        this.worldStyle = worldStyle.name();
    }

    PacketCCLevelInfo(FriendlyByteBuf buf) {
        this.worldStyle = buf.readUtf();
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.worldStyle);
    }

    public static class Handler {
        public static void handle(PacketCCLevelInfo packet, Level worldIn) {
            CubicChunks.currentClientStyle = CubicLevelHeightAccessor.WorldStyle.valueOf(packet.worldStyle);
        }
    }
}
