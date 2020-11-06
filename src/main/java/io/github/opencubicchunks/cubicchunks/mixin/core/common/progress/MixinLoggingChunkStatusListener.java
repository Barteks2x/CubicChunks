package io.github.opencubicchunks.cubicchunks.mixin.core.common.progress;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeStatusListener;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkStatus;

@Mixin(LoggerChunkProgressListener.class)
public abstract class MixinLoggingChunkStatusListener implements ICubeStatusListener {

    private int loadedCubes;
    private int totalCubes;

    @Shadow private int count;
    @Shadow @Final @Mutable private int maxCount;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(int vanillaSpawnRadius, CallbackInfo ci) {
        // Server chunk radius, divided by CUBE_DIAMETER to get the radius in cubes
        // Except we subtract one before the ceil and readd it after, for... some reason
        // Multiply by two to convert cube radius -> diameter,
        // And then add one for the center cube
        int ccCubeRadius = 1+(int) Math.ceil((vanillaSpawnRadius-1) / ((float) IBigCube.DIAMETER_IN_SECTIONS));
        int ccCubeDiameter = ccCubeRadius*2+1;
        totalCubes = ccCubeDiameter*ccCubeDiameter*ccCubeDiameter;

        int ccChunkRadius = ccCubeRadius * IBigCube.DIAMETER_IN_SECTIONS;
        int ccChunkDiameter = ccChunkRadius*2+1;
        maxCount = ccChunkDiameter*ccChunkDiameter;
    }

    @Override public void startCubes(CubePos center) {}

    @Override public void onCubeStatusChange(CubePos cubePos, @Nullable ChunkStatus newStatus) {
        if (newStatus == ChunkStatus.FULL) {
            this.loadedCubes++;
        }
    }

    /**
     * @author CursedFlames & NotStirred
     * @reason number of chunks is different due to rounding to chunks rounding to 1 cubes to 1, 2, 4, 8 depending on {@link IBigCube#DIAMETER_IN_SECTIONS}
     */
    @Overwrite
    public int getProgress() {
        int loaded = count + loadedCubes;
        int total = maxCount + totalCubes;
        return Mth.floor(loaded * 100.0F / total);
    }
}