package io.github.opencubicchunks.cubicchunks.mixin.core.common.block;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultifaceBlock.class)
public class MixinMultifaceBlock {

    @Redirect(method = "spreadToFace",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess getCube(LevelAccessor levelAccessor, BlockPos pos) {
        if (!((CubicLevelHeightAccessor) levelAccessor).isCubic()) {
            levelAccessor.getChunk(pos);
        }

        return ((ICubicWorld) levelAccessor).getCube(pos);
    }
}