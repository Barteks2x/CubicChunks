package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld implements ICubicWorld {
    @Shadow @Final protected AbstractChunkProvider chunkProvider;

    @Inject(at = @At("RETURN"), method = "isYOutOfBounds", cancellable = true)
    private static void isYOutOfBounds(int y, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(y < -CubicChunks.worldMAXHeight || y >= CubicChunks.worldMAXHeight);
    }

    @Inject(method = "markChunkDirty", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;markDirty()V"))
    private void onMarkChunkDirty(BlockPos blockPos, TileEntity tileEntity, CallbackInfo ci) {
        this.getCubeAt(blockPos).setDirty(true);
    }

    public BigCube getCubeAt(BlockPos pos) {
        return this.getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
    }

    @Override
    public BigCube getCube(int cubeX, int cubeY, int cubeZ) {
        return (BigCube)this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, true);
    }

    public IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean nonnull) {
        IBigCube icube = ((ICubeProvider)this.chunkProvider).getCube(cubeX, cubeY, cubeZ, requiredStatus, nonnull);
        if (icube == null && nonnull) {
            throw new IllegalStateException("Should always be able to create a cube!");
        } else {
            return icube;
        }
    }
}
