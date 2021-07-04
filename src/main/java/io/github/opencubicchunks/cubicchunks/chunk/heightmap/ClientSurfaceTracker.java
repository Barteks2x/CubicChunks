package io.github.opencubicchunks.cubicchunks.chunk.heightmap;

import java.util.function.Predicate;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public class ClientSurfaceTracker extends Heightmap {

    protected final Predicate<BlockState> isOpaque;

    public ClientSurfaceTracker(ChunkAccess chunkAccess, Types types) {
        super(chunkAccess, types);
        this.isOpaque = ((HeightmapAccess) this).getIsOpaque();
    }

    @Override public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        int previous = getFirstAvailable(columnLocalX, columnLocalZ);
        if (globalY <= previous - 2) {
            return false;
        }
        if (this.isOpaque.test(blockState)) {
            if (globalY >= previous) {
                ((HeightmapAccess) this).invokeSetHeight(columnLocalX, columnLocalZ, globalY + 1);
                return true;
            }
            return true;
        }
        if (previous - 1 == globalY) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            ChunkAccess chunk = ((HeightmapAccess) this).getChunk();
            int currentY;
            for (currentY = globalY - 1; currentY >= globalY - 64; --currentY) {
                pos.set(columnLocalX, currentY, columnLocalZ);
                if (this.isOpaque.test(chunk.getBlockState(pos))) {
                    ((HeightmapAccess) this).invokeSetHeight(columnLocalX, columnLocalZ, currentY + 1);
                    return true;
                }
            }
            ((HeightmapAccess) this).invokeSetHeight(columnLocalX, columnLocalZ, currentY);
            return true;
        }
        return false;
    }
}
