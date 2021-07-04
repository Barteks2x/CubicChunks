package io.github.opencubicchunks.cubicchunks.chunk.heightmap;

import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.ISkyLightColumnChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClientLightSurfaceTracker extends ClientSurfaceTracker {
    private final int bitsPerColumn;
    private final int minHeight;
    public ClientLightSurfaceTracker(ChunkAccess chunkAccess) {
        // type shouldn't matter
        super(chunkAccess, Types.WORLD_SURFACE);
        bitsPerColumn = Mth.ceillog2(((HeightmapAccess) this).getChunk().getHeight() + 1);
        minHeight = ((HeightmapAccess) this).getChunk().getMinBuildHeight();
    }

    @Override public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        throw new UnsupportedOperationException("ClientLightSurfaceTracker.update should never be called");
    }

    protected VoxelShape getShape(BlockState blockState, BlockPos pos, Direction facing) {
        return blockState.canOcclude() && blockState.useShapeForLightOcclusion() ? blockState.getFaceOcclusionShape(((HeightmapAccess) this).getChunk(), pos, facing) : Shapes.empty();
    }

    private static int getIndex(int x, int z) {
        return x + z * 16;
    }

    public void setRawData(long[] heightmap, LevelChunk chunk) {
        // We need to compare the old and new data here, hence the inefficiencies with making a new bitstorage
        // TODO can this be optimized to operate on long[]s directly instead of making an extra BitStorage?
        BitStorage storage = ((HeightmapAccess) this).getData();
        BitStorage oldStorage = new BitStorage(bitsPerColumn, 256, storage.getRaw().clone());
        System.arraycopy(heightmap, 0, storage.getRaw(), 0, heightmap.length);
//        ChunkAccess chunk = ((HeightmapAccess) this).getChunk();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = getIndex(x, z);
                int oldHeight = oldStorage.get(index) + minHeight;
                int newHeight = storage.get(index) + minHeight;
                if (oldHeight != newHeight) {
                    ((ISkyLightColumnChecker) Minecraft.getInstance().level.getLightEngine()).checkSkyLightColumn((CubeMapGetter) chunk, baseX + x, baseZ + z, oldHeight, newHeight);
                }
            }
        }
    }
}
