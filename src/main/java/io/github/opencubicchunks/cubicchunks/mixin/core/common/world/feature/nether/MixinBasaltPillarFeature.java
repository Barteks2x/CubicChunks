package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.feature.nether;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.BasaltPillarFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BasaltPillarFeature.class)
public class MixinBasaltPillarFeature {

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean useCubeMinY(WorldGenLevel worldGenLevel, BlockPos blockPos) {
        if (!((CubicLevelHeightAccessor) worldGenLevel).isCubic()) {
            return worldGenLevel.isOutsideBuildHeight(blockPos);
        }

        return !((CubeWorldGenRegion) worldGenLevel).insideCubeHeight(blockPos.getY());
    }

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z", ordinal = 4))
    private boolean cancelOutsideBounds(WorldGenLevel level, BlockPos pos) {
        if (!((CubicLevelHeightAccessor) level).isCubic()) {
            return level.isEmptyBlock(pos);
        }

        return ((CubeWorldGenRegion) level).insideCubeHeight(pos.getY()) && level.isEmptyBlock(pos);
    }
}
