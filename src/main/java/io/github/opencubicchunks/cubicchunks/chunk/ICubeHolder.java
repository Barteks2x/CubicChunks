package io.github.opencubicchunks.cubicchunks.chunk;

import static io.github.opencubicchunks.cubicchunks.chunk.util.Utils.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimerWrapper;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubeStatus;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkHolderAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface ICubeHolder {
    // TODO: all of their usages should be replaced with ASM
    // TODO: rename to match mojang names
    @Deprecated
    Either<IBigCube, ChunkHolder.ChunkLoadingFailure> MISSING_CUBE = unsafeCast(ChunkHolder.UNLOADED_CHUNK);
    @Deprecated
    Either<BigCube, ChunkHolder.ChunkLoadingFailure> UNLOADED_CUBE = unsafeCast(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    @Deprecated
    CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> UNLOADED_CUBE_FUTURE = unsafeCast(ChunkHolderAccess.getUnloadedChunkFuture());
    @Deprecated
    CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> MISSING_CUBE_FUTURE = unsafeCast(ChunkHolder.UNLOADED_CHUNK_FUTURE);

    static ChunkStatus getCubeStatusFromLevel(int cubeLevel) {
        return cubeLevel < 33 ? ChunkStatus.FULL : CubeStatus.getStatus(cubeLevel - 33);
    }

    @Nullable
    BigCube getCubeIfComplete();

    CubePos getCubePos();

    // func_219276_a, getOrScheduleFuture
    CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getOrScheduleCubeFuture(ChunkStatus chunkStatus, ChunkMap chunkManager);

    CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getCubeFutureIfPresentUnchecked(ChunkStatus chunkStatus);

    CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>> getCubeEntityTickingFuture();

    // func_219294_a, replaceProtoChunk
    void replaceProtoCube(CubePrimerWrapper primer);

    // func_225410_b, getFutureIfPresent
    CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getCubeFutureIfPresent(ChunkStatus chunkStatus);

    void addCubeStageListener(ChunkStatus status, BiConsumer<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>, Throwable> consumer, ChunkMap chunkManager);


    void broadcastChanges(BigCube cube);

    CompletableFuture<IBigCube> getCubeToSave();

    // added with ASM, can't be shadow because mixin validates shadows before preApply runs
    void updateCubeFutures(ChunkMap chunkManagerIn, Executor executor);

    class CubeLoadingError implements ChunkHolder.ChunkLoadingFailure {
        private final ChunkHolder holder;

        public CubeLoadingError(ChunkHolder holder) {
            this.holder = holder;
        }

        @Override public String toString() {
            return "Unloaded ticket level " + ((ICubeHolder) holder).getCubePos().toString();
        }
    }
}