package io.github.opencubicchunks.cubicchunks.server;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface IServerChunkProvider extends ICubeProvider {
    // TODO check whether this is still needed
    // ChunkHolder getChunkHolderForce(ChunkPos chunkPos, ChunkStatus requiredStatus);

    <T> void addCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value);

    int getTickingGeneratedCubes();

    void forceCube(CubePos pos, boolean add);

    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getColumnFutureForCube(CubePos cubePos, int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create);
    // TODO check whether this is still needed
    // boolean isEntityTickingCube(CubePos pos);

    boolean checkCubeFuture(long cubePosLong, Function<ChunkHolder, CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>>> futureFunction);
}
