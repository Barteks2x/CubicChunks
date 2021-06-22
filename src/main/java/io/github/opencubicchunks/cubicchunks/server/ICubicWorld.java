package io.github.opencubicchunks.cubicchunks.server;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeCollisionGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface ICubicWorld extends CubicLevelHeightAccessor, CubeCollisionGetter { //TODO: maybe rename this class? I think this name is reserved by the API
    IBigCube getCube(int cubeX, int cubeY, int cubeZ);

    default IBigCube getCube(CubePos cubePos) {
        return getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ());
    }

    IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status);

    default IBigCube getCube(CubePos cubePos, ChunkStatus status) {
        return getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), status);
    }

    default IBigCube getCube(BlockPos pos) {
        return getCube(CubePos.from(pos));
    }

    @Nullable
    default IBigCube getCube(BlockPos pos, ChunkStatus status, boolean notnull) {
        return getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()), status, notnull);
    }

    @Nullable
    IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status, boolean notnull);

    @Nullable
    @Override
    default BlockGetter getCubeForCollisions(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.EMPTY, false);
    }
}