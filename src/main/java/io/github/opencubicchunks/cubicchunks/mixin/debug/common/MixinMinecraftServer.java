package io.github.opencubicchunks.cubicchunks.mixin.debug.common;

import java.util.Iterator;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.server.IServerChunkProvider;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 999)
public abstract class MixinMinecraftServer {
    private static final boolean DEBUG_LOAD_ORDER_ENABLED = System.getProperty("cubicchunks.debug.loadorder", "false").equals("true");

    private int count = 0;

    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow private long nextTickTime;

    @Shadow protected abstract void waitUntilNextTick();

    @Shadow protected abstract void updateMobSpawningFlags();

    @Shadow public abstract ServerLevel overworld();

    @Shadow public abstract boolean isRunning();

    private void addChunk(ServerChunkCache serverChunkCache, ChunkPos pos) {
        serverChunkCache.addRegionTicket(TicketType.START, pos, 1, Unit.INSTANCE);
        count++;
    }

    private void addCube(IServerChunkProvider serverChunkProvider, CubePos pos) {
        serverChunkProvider.addCubeRegionTicket(TicketType.START, pos, 1, Unit.INSTANCE);
        count++;
    }

    /**
     * @author NotStirred
     * @reason Custom chunk loading order for debugging
     */
    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void prepareLevels(ChunkProgressListener worldGenerationProgressListener, CallbackInfo ci) {
        if (!DEBUG_LOAD_ORDER_ENABLED) {
            return;
        }

        ci.cancel();
        ServerLevel serverLevel = this.overworld();
        CubicChunks.LOGGER.info("Preparing start region for dimension {}", serverLevel.dimension().location());
        BlockPos blockPos = serverLevel.getSharedSpawnPos();
        worldGenerationProgressListener.updateSpawnPos(new ChunkPos(blockPos));
        ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
        serverChunkCache.getLightEngine().setTaskPerBatch(500);
        this.nextTickTime = Util.getMillis();

        IServerChunkProvider prov = (IServerChunkProvider) serverChunkCache;
        addChunk(serverChunkCache, new ChunkPos(0, 0));
        addCube(prov, CubePos.of(0, 0, 0));

        addCube(prov, CubePos.of(5, 5, 5));
        addCube(prov, CubePos.of(-5, 5, 5));
        addCube(prov, CubePos.of(-5, -5, 5));
        addCube(prov, CubePos.of(-5, -5, -5));
        addCube(prov, CubePos.of(5, -5, 5));
        addCube(prov, CubePos.of(5, -5, -5));
        addCube(prov, CubePos.of(5, 5, -5));
        addCube(prov, CubePos.of(-5, 5, -5));

        while (this.isRunning() && (serverChunkCache.getTickingGenerated() + prov.getTickingGeneratedCubes() < count)) {
            this.nextTickTime = Util.getMillis() + 10L;
            this.waitUntilNextTick();
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            int ignored = 0;
        }

        this.nextTickTime = Util.getMillis() + 10L;
        this.waitUntilNextTick();
        Iterator<ServerLevel> levelIter = this.levels.values().iterator();

        while (true) {
            ServerLevel serverLevel2;
            ForcedChunksSavedData forcedChunksSavedData;
            do {
                if (!levelIter.hasNext()) {
                    this.nextTickTime = Util.getMillis() + 10L;
                    this.waitUntilNextTick();
                    worldGenerationProgressListener.stop();
                    serverChunkCache.getLightEngine().setTaskPerBatch(5);
                    this.updateMobSpawningFlags();
                    return;
                }

                serverLevel2 = levelIter.next();
                forcedChunksSavedData = serverLevel2.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
            } while (forcedChunksSavedData == null);

            LongIterator longIterator = forcedChunksSavedData.getChunks().iterator();

            while (longIterator.hasNext()) {
                long l = longIterator.nextLong();
                ChunkPos chunkPos = new ChunkPos(l);
                serverLevel2.getChunkSource().updateChunkForced(chunkPos, true);
            }
        }
    }
}
