package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.surfacebuilder;

import java.util.Random;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.surfacebuilders.NetherCappedSurfaceBuilder;
import net.minecraft.world.level.levelgen.surfacebuilders.SurfaceBuilderBaseConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(NetherCappedSurfaceBuilder.class)
public class MixinNetherCappedSurfaceBuilder {

    @ModifyConstant(method = "apply", constant = { @Constant(intValue = 127), @Constant(intValue = 128) })
    private int useCubeY(int arg0, Random random, ChunkAccess chunk, Biome biome, int i, int j, int k, double d, BlockState blockState, BlockState blockState2, int l, int m, long n,
                         SurfaceBuilderBaseConfiguration surfaceBuilderBaseConfiguration) {
        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return arg0;
        }
        return arg0 == 127 ? chunk.getMaxBuildHeight() - 1 : chunk.getMaxBuildHeight();
    }

    @Inject(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;", ordinal = 1),
        cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void cancelBelowCubeBounds(Random random, ChunkAccess chunkAccess, Biome biome, int i, int j, int k, double d, BlockState blockState, BlockState blockState2, int l, int m,
                                       long n, SurfaceBuilderBaseConfiguration surfaceBuilderBaseConfiguration, CallbackInfo ci, int o, int p, int q, int r, int s, boolean bl,
                                       BlockState blockState3, BlockState blockState4, BlockPos.MutableBlockPos mutableBlockPos, BlockState blockState5, int t) {
        if (t <= chunkAccess.getMinBuildHeight()) {
            ci.cancel();
        }
    }

    @Inject(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;move(Lnet/minecraft/core/Direction;)"
        + "Lnet/minecraft/core/BlockPos$MutableBlockPos;", ordinal = 1), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void cancelBelowCubeBounds(Random random, ChunkAccess chunkAccess, Biome biome, int i, int j, int k, double d, BlockState blockState, BlockState blockState2, int l, int m,
                                       long n, SurfaceBuilderBaseConfiguration surfaceBuilderBaseConfiguration, CallbackInfo ci, int o, int p, int q, int r, int s, boolean bl,
                                       BlockState blockState3, BlockState blockState4, BlockPos.MutableBlockPos mutableBlockPos, BlockState blockState5, int t, BlockState blockState6,
                                       int v) {
        if (mutableBlockPos.getY() <= chunkAccess.getMinBuildHeight()) {
            ci.cancel();
        }
    }
}
