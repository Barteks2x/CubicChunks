package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.generator;

import java.util.List;
import java.util.concurrent.Executor;

import io.github.opencubicchunks.cubicchunks.chunk.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FlatLevelSource.class)
public class MixinFlatLevelSource implements ICubeGenerator {

    @ModifyConstant(method = "fillFromNoise", constant = @Constant(intValue = 0, ordinal = 0))
    private int useChunkMinY(int arg0, Executor executor, StructureFeatureManager accessor, ChunkAccess chunk) {
        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return arg0;
        }
        return chunk.getMinBuildHeight();
    }

    @Redirect(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object noNegativeIndex(List list, int index, Executor executor, StructureFeatureManager accessor, ChunkAccess chunk) {
        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return list.get(index);
        }

        if (index > list.size() - 1) {
            return null;
        }

        if (index < 0) {
            return list.get(0);
        }

        return list.get(index);
    }

    @Redirect(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private int useChunkMaxY(int a, int b, Executor executor, StructureFeatureManager accessor, ChunkAccess chunk) {
        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return Math.min(a, b);
        }
        return chunk.getMaxBuildHeight();
    }

    @Redirect(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getMinBuildHeight()I"))
    private int dontAddMinBuildHeight(ChunkAccess chunk) {
        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return chunk.getMinBuildHeight();
        }
        return 0;
    }

    @Override public void decorate(CubeWorldGenRegion region, StructureFeatureManager structureManager, CubePrimer chunkAccess) {

    }
}
