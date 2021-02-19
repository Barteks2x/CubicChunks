package io.github.opencubicchunks.cubicchunks.chunk;

import java.util.function.BooleanSupplier;

import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;

public interface IChunkMapInternal {

    boolean isExistingCubeFull(CubePos pos);

    void processCubeUnloads(BooleanSupplier shouldKeepTicking);

    void saveAllCubes(boolean flush);

    boolean cubeSave(IBigCube cube);
}
