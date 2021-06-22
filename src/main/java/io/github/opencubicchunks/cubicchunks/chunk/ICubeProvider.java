package io.github.opencubicchunks.cubicchunks.chunk;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface ICubeProvider {

    @Nullable
    IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean load);

    @Nullable
    default BigCube getCube(int cubeX, int cubeY, int cubeZ, boolean create) {
        return (BigCube) this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, create);
    }

    @Nullable
    default BigCube getCubeNow(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, false);
    }

    default boolean hasCube(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, false) != null;
    }
}