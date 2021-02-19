package io.github.opencubicchunks.cubicchunks.chunk;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface ICubeStatusListener {
    default void startCubes(CubePos center) {
    }

    default void onCubeStatusChange(CubePos cubePos, @Nullable ChunkStatus newStatus) {
    }
    //Interface does not have a stopCubes(); because the equivalent stop for chunks does the same thing, and is called at the same time.
}