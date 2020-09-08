package io.github.opencubicchunks.cubicchunks.mixin.core.client.progress;

import io.github.opencubicchunks.cubicchunks.chunk.ICubeStatusListener;
import io.github.opencubicchunks.cubicchunks.chunk.ITrackingCubeStatusListener;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.listener.LoggingChunkStatusListener;
import net.minecraft.world.chunk.listener.TrackingChunkStatusListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(TrackingChunkStatusListener.class)
public abstract class MixinTrackingChunkStatusListener implements ICubeStatusListener, ITrackingCubeStatusListener {

    @Shadow private boolean started;

    @Shadow @Final private LoggingChunkStatusListener delegate;

    @Shadow @Final private int radius;
    @Shadow private ChunkPos spawnPos;
    private CubePos spawnCube;
    private final Long2ObjectOpenHashMap<ChunkStatus> cubeStatuses = new Long2ObjectOpenHashMap<>();

    @Override
    public void startCubes(CubePos spawn) {
        if (this.started) {
            ((ICubeStatusListener) this.delegate).startCubes(spawn);
            this.spawnCube = spawn;
            this.spawnPos = spawnCube.asChunkPos();
        }
    }

    @Override
    public void onCubeStatusChange(CubePos cubePos, @Nullable ChunkStatus newStatus) {
        if (this.started) {
            ((ICubeStatusListener) this.delegate).onCubeStatusChange(cubePos, newStatus);
            if (newStatus == null) {
                this.cubeStatuses.remove(cubePos.asLong());
            } else {
                this.cubeStatuses.put(cubePos.asLong(), newStatus);
            }
        }
    }

    @Inject(method = "start", at = @At("HEAD"))
    public void startTracking(CallbackInfo ci) {
        this.cubeStatuses.clear();
    }

    @Nullable @Override
    public ChunkStatus getCubeStatus(int x, int y, int z) {
        if (spawnCube == null) {
            return null; // vanilla race condition, made worse by forge moving IChunkStatusListener ichunkstatuslistener = this.chunkStatusListenerFactory.create(11); earlier
        }
        int radiusCubes = Coords.sectionToCubeCeil(this.radius);
        return this.cubeStatuses.get(CubePos.asLong(
                x + this.spawnCube.getX() - radiusCubes,
                y + this.spawnCube.getY() - radiusCubes,
                z + this.spawnCube.getZ() - radiusCubes));
    }
}