package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import io.github.opencubicchunks.cubicchunks.utils.AddressTools;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class PacketHeightmapChanges {
    private final ChunkPos pos;
    private final short[] positionsAndTypes;
    private final int[] heights;

    public PacketHeightmapChanges(ChunkAccess chunk, ShortArrayList changed) {
        this.pos = chunk.getPos();
        this.positionsAndTypes = changed.toShortArray();
        this.heights = new int[positionsAndTypes.length];
        int dx = chunk.getPos().getMinBlockX();
        int dz = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < this.positionsAndTypes.length; i++) {
            int x = AddressTools.getLocalX(positionsAndTypes[i]);
            int z = AddressTools.getLocalZ(positionsAndTypes[i]);
            int index = AddressTools.getLocalY(positionsAndTypes[i]);
            if (index == 0xF) {  // Light heightmap
                heights[i] = ((LightHeightmapGetter) chunk).getLightHeightmap().getFirstAvailable(x, z);
            } else {  // Normal heightmaps
                Heightmap.Types type = Heightmap.Types.values()[AddressTools.getLocalY(positionsAndTypes[i])];
                heights[i] = chunk.getHeight(type, x + dx, z + dz) + 1;
            }
        }
    }

    PacketHeightmapChanges(FriendlyByteBuf buf) {
        this.pos = new ChunkPos(buf.readInt(), buf.readInt());
        this.heights = buf.readVarIntArray();
        this.positionsAndTypes = new short[heights.length];
        for (int i = 0; i < heights.length; i++) {
            this.positionsAndTypes[i] = buf.readShort();
        }
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeInt(pos.x);
        buf.writeInt(pos.z);
        buf.writeVarIntArray(heights);
        for (short positionsAndType : positionsAndTypes) {
            buf.writeShort(positionsAndType);
        }
    }

    public static class Handler {
        public static void handle(PacketHeightmapChanges packet, Level worldIn) {
            ChunkSource chunkProvider = worldIn.getChunkSource();
            LevelChunk chunk = chunkProvider.getChunk(packet.pos.x, packet.pos.z, false);
            if (chunk == null) {
                CubicChunks.LOGGER.error("Chunk doesn't exist when receiving heightmap update at " + packet.pos);
                return;
            }
            for (int i = 0; i < packet.positionsAndTypes.length; i++) {
                short posType = packet.positionsAndTypes[i];
                int x = AddressTools.getLocalX(posType);
                int z = AddressTools.getLocalZ(posType);
                int index = AddressTools.getLocalY(posType);
                if (index == 0xF) {
                    ((HeightmapAccess) ((LightHeightmapGetter) chunk).getLightHeightmap()).invokeSetHeight(x & 0xF, z & 0xF, packet.heights[i]);
                } else {
                    Heightmap.Types type = Heightmap.Types.values()[index];
                    ((HeightmapAccess) chunk.getOrCreateHeightmapUnprimed(type)).invokeSetHeight(x & 0xF, z & 0xF, packet.heights[i]);
                }
            }
        }
    }
}
