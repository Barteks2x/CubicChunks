package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class MixinWorld implements ICubicWorld, LevelHeightAccessor {

    @Override public int getHeight() {
        return 20000000;
    }

    @Override public int getMinBuildHeight() {
        return -20000000;
    }

    @Inject(method = "blockEntityChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;markUnsaved()V"))
    private void onBlockEntityChanged(BlockPos blockPos, CallbackInfo ci) {
        this.getCubeAt(blockPos).setDirty(true);
    }

    public IBigCube getCubeAt(BlockPos pos) {
        return this.getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
    }

    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, true);
    }

    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status) {
        return this.getCube(cubeX, cubeY, cubeZ, status, true);
    }

    //The method .getWorld() No longer exists
    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean nonnull) {
        IBigCube icube = ((ICubeProvider) ((Level) (Object) this).getChunkSource()).getCube(cubeX, cubeY, cubeZ, requiredStatus, nonnull);
        if (icube == null && nonnull) {
            throw new IllegalStateException("Should always be able to create a cube!");
        } else {
            return icube;
        }
    }
}