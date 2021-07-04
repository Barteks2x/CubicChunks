package io.github.opencubicchunks.cubicchunks.network;

import java.util.Map;

import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.ClientLightSurfaceTracker;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerWrapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class PacketHeightmap {
    private final ChunkPos pos;
    private final CompoundTag heightmaps;

    public PacketHeightmap(ChunkPos pos, CompoundTag heightmaps) {
        this.pos = pos;
        this.heightmaps = heightmaps;
    }

    PacketHeightmap(FriendlyByteBuf buf) {
        this.pos = new ChunkPos(buf.readInt(), buf.readInt());
        this.heightmaps = buf.readNbt();
    }

    public static PacketHeightmap forChunk(LevelChunk chunk) {
        CompoundTag heightmaps = new CompoundTag();

        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (entry.getKey().sendToClient()) {
                heightmaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }
        LightSurfaceTrackerWrapper lightHeightmap = ((LightHeightmapGetter) chunk).getServerLightHeightmap();
        heightmaps.put("light", new LongArrayTag(lightHeightmap.getRawData()));
        return new PacketHeightmap(chunk.getPos(), heightmaps);
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeInt(pos.x);
        buf.writeInt(pos.z);
        buf.writeNbt(heightmaps);
    }

    public static class Handler {
        public static void handle(PacketHeightmap packet, Level worldIn) {
            ChunkSource chunkProvider = worldIn.getChunkSource();
            LevelChunk chunk = chunkProvider.getChunk(packet.pos.x, packet.pos.z, false);
            if (chunk == null) {
                // CubicChunks.LOGGER.error("Chunk doesn't exist when receiving heightmap");
                return;
            }
            for (Heightmap.Types value : Heightmap.Types.values()) {
                if (!packet.heightmaps.contains(value.getSerializationKey())) {
                    continue;
                }
                long[] longArray = packet.heightmaps.getLongArray(value.getSerializationKey());
                chunk.setHeightmap(value, longArray);
            }
            if (packet.heightmaps.contains("light")) {
                // TODO is this safe on dedicated server?
                long[] data = packet.heightmaps.getLongArray("light");
                ClientLightSurfaceTracker heightmap = ((LightHeightmapGetter) chunk).getClientLightHeightmap();
                heightmap.setRawData(data, chunk);
            }
        }
    }
}
