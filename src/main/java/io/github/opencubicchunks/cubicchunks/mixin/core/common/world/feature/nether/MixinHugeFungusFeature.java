package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.feature.nether;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HugeFungusFeature.class)
public class MixinHugeFungusFeature {

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getGenDepth()I"))
    private int useRegionBounds(ChunkGenerator chunkGenerator, FeaturePlaceContext<HugeFungusConfiguration> context) {
        if (!((CubicLevelHeightAccessor) context.level()).isCubic()) {
            return chunkGenerator.getGenDepth();
        }
        return Coords.cubeToMaxBlock(((CubeWorldGenRegion) context.level()).getMaxCubeY());
    }
}
