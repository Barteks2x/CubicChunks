package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.chunk.ChunkStatus$SimpleGenerationTask")
public interface MixinISelectiveWorker {

    @Shadow void doWork(ChunkStatus status, ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk);

    /**
     * @author Batrteks2x
     * @reason inject is not supported, SectionPrimer check
     */
    @Overwrite(remap = false) //TODO: REMOVE "remap=false" WHEN INTERMEDIARY IS FIXED!
    default CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> doWork(
        ChunkStatus status, Executor executor, ServerLevel world, ChunkGenerator generator,
        StructureManager structureManager, ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> completableFuture,
        List<ChunkAccess> neighbors, ChunkAccess chunk) {

        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            if (!chunk.getStatus().isOrAfter(status)) {
                this.doWork(status, world, generator, neighbors, chunk);
                if (chunk instanceof ProtoChunk) {
                    ((ProtoChunk) chunk).setStatus(status);
                }
            }
            return CompletableFuture.completedFuture(Either.left(chunk));
        }

        if (!chunk.getStatus().isOrAfter(status)) {
            this.doWork(status, world, generator, neighbors, chunk);
            if (chunk instanceof ProtoChunk) {
                ((ProtoChunk) chunk).setStatus(status);
            } else if (chunk instanceof CubePrimer) {
                ((CubePrimer) chunk).setStatus(status);
            }
        }
        return CompletableFuture.completedFuture(Either.left(chunk));
    }
}
