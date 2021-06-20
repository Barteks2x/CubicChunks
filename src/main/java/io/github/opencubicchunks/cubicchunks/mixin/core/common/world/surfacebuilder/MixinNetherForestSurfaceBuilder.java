package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.surfacebuilder;

import java.util.Random;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.surfacebuilders.NetherForestSurfaceBuilder;
import net.minecraft.world.level.levelgen.surfacebuilders.SurfaceBuilderBaseConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(NetherForestSurfaceBuilder.class)
public class MixinNetherForestSurfaceBuilder {

    @ModifyConstant(method = "apply", constant = @Constant(intValue = 127))
    private int useCubeY(int arg0, Random random, ChunkAccess chunk, Biome biome, int i, int j, int k, double d, BlockState blockState, BlockState blockState2, int l, int m, long n,
                         SurfaceBuilderBaseConfiguration surfaceBuilderBaseConfiguration) {
        return !((CubicLevelHeightAccessor) chunk).isCubic() ? arg0 : chunk.getMaxBuildHeight() - 1;
    }

    @Inject(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"), cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void cancelBelowCubeBounds(Random random, ChunkAccess chunkAccess, Biome biome, int i, int j, int k, double d, BlockState blockState, BlockState blockState2, int l, int m,
                                       long n, SurfaceBuilderBaseConfiguration surfaceBuilderBaseConfiguration, CallbackInfo ci, int o, int p, int q, boolean bl, boolean bl2, int r,
                                       BlockPos.MutableBlockPos mutableBlockPos, int s, BlockState blockState3, int t) {
        if (t <= chunkAccess.getMinBuildHeight()) {
            ci.cancel();
        }
    }
}
