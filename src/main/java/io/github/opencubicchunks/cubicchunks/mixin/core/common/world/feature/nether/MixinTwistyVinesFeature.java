package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.feature.nether;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.TwistingVinesFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TwistingVinesFeature.class)
public class MixinTwistyVinesFeature {


    @Redirect(method = "findFirstAirBlockAboveGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean isOutsideCubeY(LevelAccessor levelAccessor, BlockPos blockPos) {
        if (!((CubicLevelHeightAccessor) levelAccessor).isCubic()) {
            return levelAccessor.isOutsideBuildHeight(blockPos);
        }

        return !((CubeWorldGenRegion) levelAccessor).insideCubeHeight(blockPos.getY());
    }
}
