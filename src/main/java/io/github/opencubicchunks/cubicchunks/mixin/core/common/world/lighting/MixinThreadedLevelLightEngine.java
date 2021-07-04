package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.lighting;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.IChunkManager;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.CubeTaskPriorityQueueSorter;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.server.IServerWorldLightManager;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class MixinThreadedLevelLightEngine extends MixinLevelLightEngine implements IServerWorldLightManager {

    private ProcessorHandle<CubeTaskPriorityQueueSorter.FunctionEntry<Runnable>> cubeSorterMailbox;

    @Shadow @Final private ChunkMap chunkMap;

    @Shadow @Final private ObjectList<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> lightTasks;

    @Shadow private volatile int taskPerBatch;

    @Shadow protected abstract void runUpdate();
    @Shadow
    protected abstract void addTask(int x, int z, ThreadedLevelLightEngine.TaskType stage, Runnable task);

    @Override public void postConstructorSetup(CubeTaskPriorityQueueSorter sorter,
                                               ProcessorHandle<CubeTaskPriorityQueueSorter.FunctionEntry<Runnable>> taskExecutor) {
        this.cubeSorterMailbox = taskExecutor;
    }

    /**
     * @author NotStirred
     * @reason lambdas
     */
    @Inject(method = "checkBlock", at = @At("HEAD"), cancellable = true)
    public void checkBlock(BlockPos blockPosIn, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }
        ci.cancel();

        BlockPos blockpos = blockPosIn.immutable();
        this.addTask(
            Coords.blockToCube(blockPosIn.getX()),
            Coords.blockToCube(blockPosIn.getY()),
            Coords.blockToCube(blockPosIn.getZ()),
            ThreadedLevelLightEngine.TaskType.POST_UPDATE,
            Util.name(() -> super.checkBlock(blockpos),
                () -> "checkBlock " + blockpos)
        );
    }

    // func_215586_a, addTask
    private void addTask(int cubePosX, int cubePosY, int cubePosZ, ThreadedLevelLightEngine.TaskType phase, Runnable runnable) {
        this.addTask(cubePosX, cubePosY, cubePosZ, ((IChunkManager) this.chunkMap).getCubeQueueLevel(CubePos.of(cubePosX, cubePosY,
            cubePosZ).asLong()), phase, runnable);
    }

    // func_215600_a, addTask
    private void addTask(int cubePosX, int cubePosY, int cubePosZ, IntSupplier getCompletedLevel, ThreadedLevelLightEngine.TaskType phase,
                         Runnable runnable) {
        this.cubeSorterMailbox.tell(CubeTaskPriorityQueueSorter.createMsg(() -> {
            this.lightTasks.add(Pair.of(phase, runnable));
            if (this.lightTasks.size() >= this.taskPerBatch) {
                this.runUpdate();
            }

        }, CubePos.asLong(cubePosX, cubePosY, cubePosZ), getCompletedLevel));
    }

    // updateChunkStatus
    public void setCubeStatusEmpty(CubePos cubePos) {
        this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), () -> {
            return 0;
        }, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.retainData(cubePos, false);
            super.enableLightSources(cubePos, false);


            for (int i = 0; i < IBigCube.SECTION_COUNT; ++i) {
                super.queueSectionData(LightLayer.BLOCK, Coords.sectionPosByIndex(cubePos, i), (DataLayer) null, true);
                super.queueSectionData(LightLayer.SKY, Coords.sectionPosByIndex(cubePos, i), (DataLayer) null, true);
            }

            for (int j = 0; j < IBigCube.SECTION_COUNT; ++j) {
                super.updateSectionStatus(Coords.sectionPosByIndex(cubePos, j), true);
            }

        }, () -> "setCubeStatusEmpty " + cubePos + " " + true));
    }

    // lightChunk
    @Override
    public CompletableFuture<IBigCube> lightCube(IBigCube icube, boolean flagIn) {
        CubePos cubePos = icube.getCubePos();
        icube.setCubeLight(false);
        this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            for (int i = 0; i < IBigCube.SECTION_COUNT; ++i) {
                LevelChunkSection chunksection = icube.getCubeSections()[i];
                if (!LevelChunkSection.isEmpty(chunksection)) {
                    super.updateSectionStatus(Coords.sectionPosByIndex(cubePos, i), false);
                }
            }

            super.enableLightSources(cubePos, true);
            if (!flagIn) {
                icube.getCubeLightSources().forEach((blockPos) -> {
                    assert blockPos != null;
                    super.onBlockEmissionIncrease(blockPos, icube.getLightEmission(blockPos));
                });
                // FIXME we probably want another flag for controlling skylight
                super.doSkyLightForCube(icube);
            }

        }, () -> "lightCube " + cubePos + " " + flagIn));
        return CompletableFuture.supplyAsync(() -> {
            icube.setCubeLight(true);
            super.retainData(cubePos, false);
            ((IChunkManager) this.chunkMap).releaseLightTicket(cubePos);
            return icube;
        }, (runnable) -> {
            this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable);
        });
    }

    @Override
    public void checkSkyLightColumn(CubeMapGetter chunk, int x, int z, int oldHeight, int newHeight) {
        // FIXME figure out when this should actually be scheduled instead of just hoping for the best
        this.addTask(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z), ThreadedLevelLightEngine.TaskType.POST_UPDATE, Util.name(() -> {
            super.checkSkyLightColumn(chunk, x, z, oldHeight, newHeight);
        }, () -> "checkSkyLightColumn " + x + " " + z));
    }

    @Inject(method = "updateChunkStatus", at = @At("HEAD"), cancellable = true)
    private void cancelUpdateChunkStatus(ChunkPos pos, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            ci.cancel();
        }
    }

    /**
     * @author NotStirred
     * @reason Vanilla lighting is gone
     */
    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    public void updateSectionStatus(SectionPos pos, boolean isEmpty, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }

        ci.cancel();
        CubePos cubePos = CubePos.from(pos);
        this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.updateSectionStatus(pos, isEmpty);
        }, () -> "updateSectionStatus " + pos + " " + isEmpty));
    }

    @Override
    public void enableLightSources(CubePos cubePos, boolean flag) {
        this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.enableLightSources(cubePos, flag);
        }, () -> "enableLight " + cubePos + " " + flag));
    }

    /**
     * @author NotStirred
     * @reason Vanilla lighting is gone
     */
    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    public void queueSectionData(LightLayer type, SectionPos pos, @Nullable DataLayer array, boolean flag, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }

        ci.cancel();

        CubePos cubePos = CubePos.from(pos);
        this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.queueSectionData(type, pos, array, flag);
        }, () -> "queueData " + pos));
    }

    //retainData(ChunkPos, bool)
    @Override
    public void retainData(CubePos cubePos, boolean retain) {
        this.addTask(cubePos.getX(), cubePos.getY(), cubePos.getZ(), () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.retainData(cubePos, retain);
        }, () -> "retainData " + cubePos));
    }
}