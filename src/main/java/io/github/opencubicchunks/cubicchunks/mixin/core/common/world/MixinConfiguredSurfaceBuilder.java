package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import java.util.Random;

import io.github.opencubicchunks.cubicchunks.world.surfacebuilder.ChunkGeneratorSurfaceBuilderContextObtainer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.surfacebuilders.ConfiguredSurfaceBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConfiguredSurfaceBuilder.class)
public class MixinConfiguredSurfaceBuilder implements ChunkGeneratorSurfaceBuilderContextObtainer {

    private BlockState defaultFluidBlockState;
    private int minSurfaceHeight;

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void getDefaultFluidState(Random random, ChunkAccess chunk, Biome biome, int x, int z, int height, double noise, BlockState defaultBlock, BlockState defaultFluid, int seaLevel,
                                      int minSurfaceHeight, long l, CallbackInfo ci) {
        this.defaultFluidBlockState = defaultFluid;
        this.minSurfaceHeight = minSurfaceHeight;
    }

    @Override public BlockState getDefaultFluidBlockState() {
        return this.defaultFluidBlockState;
    }

    @Override public int getMinSurfaceHeight() {
        return this.minSurfaceHeight;
    }
}
