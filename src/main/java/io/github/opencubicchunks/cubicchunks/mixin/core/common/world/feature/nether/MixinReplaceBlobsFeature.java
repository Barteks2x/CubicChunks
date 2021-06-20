package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.feature.nether;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ReplaceBlobsFeature.class)
public class MixinReplaceBlobsFeature {

    @Redirect(method = "findTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getMinBuildHeight()I"))
    private static int useCubeMinY(LevelAccessor levelAccessor) {
        if (!((CubicLevelHeightAccessor) levelAccessor).isCubic()) {
            return levelAccessor.getMinBuildHeight();
        }

        return Coords.cubeToMinBlock(((CubeWorldGenRegion) levelAccessor).getMainCubeY());
    }
}
