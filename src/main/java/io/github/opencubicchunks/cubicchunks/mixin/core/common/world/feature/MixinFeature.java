package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.feature;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Feature.class)
public class MixinFeature {

    @Redirect(method = "markAboveForPostProcessing",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess getCube(WorldGenLevel level, BlockPos pos) {
        if (!((CubicLevelHeightAccessor) level).isCubic()) {
            return level.getChunk(pos);
        }

        return ((ICubicWorld) level).getCube(pos);
    }
}