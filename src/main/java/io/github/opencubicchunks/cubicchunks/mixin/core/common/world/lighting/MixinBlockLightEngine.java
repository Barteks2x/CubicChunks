package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.lighting;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.lighting.ICubeLightProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("rawtypes")
@Mixin(BlockLightEngine.class)
public abstract class MixinBlockLightEngine extends MixinLayerLightEngine {

    /**
     * @author NotStirred
     * @reason Vanilla lighting is bye bye
     */
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void getLightEmission(long worldPos, CallbackInfoReturnable<Integer> cir) {
        if (!((CubicLevelHeightAccessor) this.chunkSource.getLevel()).isCubic()) {
            return;
        }

        cir.cancel();
        int blockX = BlockPos.getX(worldPos);
        int blockY = BlockPos.getY(worldPos);
        int blockZ = BlockPos.getZ(worldPos);
        BlockGetter iblockreader = ((ICubeLightProvider) this.chunkSource).getCubeForLighting(
            SectionPos.blockToSectionCoord(blockX),
            SectionPos.blockToSectionCoord(blockY),
            SectionPos.blockToSectionCoord(blockZ)
        );
        cir.setReturnValue(iblockreader != null ? iblockreader.getLightEmission(this.pos.set(blockX, blockY, blockZ)) : 0);
    }

}