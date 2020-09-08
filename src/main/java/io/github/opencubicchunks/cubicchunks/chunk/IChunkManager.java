package io.github.opencubicchunks.cubicchunks.chunk;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubeStatus;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ChunkHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

public interface IChunkManager {
    int MAX_CUBE_DISTANCE = 33 + CubeStatus.maxDistance();

    int getTickingGeneratedCubes();

    int sizeCubes();

    // implemented by ASM in MainTransformer
    @Nullable
    ChunkHolder updateCubeScheduling(long cubePosIn, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    LongSet getCubesToDrop();

    @Nullable
    ChunkHolder getCubeHolder(long cubePosIn);
    @Nullable
    ChunkHolder getImmutableCubeHolder(long cubePosIn);

    CompletableFuture<Either<IBigCube, ChunkHolder.IChunkLoadingError>> scheduleCube(ChunkHolder chunkHolderIn,
                                                                                         ChunkStatus chunkStatusIn);

    CompletableFuture<Either<BigCube, ChunkHolder.IChunkLoadingError>> unpackCubeTicks(ChunkHolder chunkHolder);


    CompletableFuture<Either<BigCube, ChunkHolder.IChunkLoadingError>> postProcessCube(ChunkHolder chunkHolder);

    CompletableFuture<Either<List<IBigCube>, ChunkHolder.IChunkLoadingError>> getCubeRangeFuture(CubePos pos, int p_219236_2_,
                                                                                                     IntFunction<ChunkStatus> p_219236_3_);

    CompletableFuture<Void> packCubeTicks(BigCube cubeIn);

    CompletableFuture<Either<BigCube, ChunkHolder.IChunkLoadingError>> getCubeEntityTickingRangeFuture(CubePos pos);

    Iterable<ChunkHolder> getCubes();

    // func_219215_b, checkerboardDistance
    static int getCubeChebyshevDistance(CubePos pos, ServerPlayerEntity player, boolean p_219215_2_)  {
        int x;
        int y;
        int z;
        if (p_219215_2_) {
            SectionPos sectionpos = player.getLastSectionPos();
            x = Coords.sectionToCube(sectionpos.x());
            y = Coords.sectionToCube(sectionpos.y());
            z = Coords.sectionToCube(sectionpos.z());
        } else {
            x = Coords.getCubeXForEntity(player);
            y = Coords.getCubeYForEntity(player);
            z = Coords.getCubeZForEntity(player);
        }

        return getCubeDistance(pos, x, y, z);
    }

    static int getCubeDistance(CubePos cubePosIn, int x, int y, int z) {
        int dX = cubePosIn.getX() - x;
        int dY = cubePosIn.getY() - y;
        int dZ = cubePosIn.getZ() - z;
        return Math.max(Math.max(Math.abs(dX), Math.abs(dZ)), Math.abs(dY));
    }

    IntSupplier getCubeQueueLevel(long cubePosIn);

    void releaseLightTicket(CubePos cubePos);
}