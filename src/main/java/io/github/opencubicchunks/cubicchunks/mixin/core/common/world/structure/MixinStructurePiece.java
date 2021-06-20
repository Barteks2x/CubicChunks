package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.structure;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructurePiece.class)
public class MixinStructurePiece {

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;"),
        method = "placeBlock")
    private ChunkAccess getCube(WorldGenLevel worldGenLevel, BlockPos blockPos) {
        if (((CubicLevelHeightAccessor) worldGenLevel).generates2DChunks()) {
            return worldGenLevel.getChunk(blockPos);
        }

        return (ChunkAccess) ((ICubicWorld) worldGenLevel).getCube(blockPos);
    }

    @Redirect(method = "fillColumnDown", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getMinBuildHeight()I"))
    private int fillToRegionBottom(WorldGenLevel level) {
        if (!((CubicLevelHeightAccessor) level).isCubic()) {
            return level.getMinBuildHeight();
        }

        return Coords.cubeToMinBlock(((CubeWorldGenRegion) level).getMinCubeY());
    }
}
