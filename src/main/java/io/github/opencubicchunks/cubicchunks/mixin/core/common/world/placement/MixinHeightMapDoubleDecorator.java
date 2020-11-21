package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.placement;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.DecoratorConfiguration;
import net.minecraft.world.level.levelgen.placement.DecorationContext;
import net.minecraft.world.level.levelgen.placement.HeightmapDoubleDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;
import java.util.stream.Stream;

@Mixin(HeightmapDoubleDecorator.class)
public abstract class MixinHeightMapDoubleDecorator<DC extends DecoratorConfiguration> {

    @Shadow protected abstract Heightmap.Types type(DC decoratorConfiguration);

    @Inject(method = "getPositions", at = @At(value = "HEAD"), cancellable = true)
    private void allowNegativeCoords(DecorationContext decorationContext, Random random, DC decoratorConfiguration, BlockPos blockPos, CallbackInfoReturnable<Stream<BlockPos>> cir) {
        CubicLevelHeightAccessor context = (CubicLevelHeightAccessor) decorationContext;

//        cir.setReturnValue(Stream.of());

        if (!context.isCubicWorld())
            return;

        if (random.nextFloat() >= (0.1F * IBigCube.DIAMETER_IN_SECTIONS))
            cir.setReturnValue(Stream.of());
        int x = blockPos.getX();
        int z = blockPos.getZ();

        int yHeightMap = decorationContext.getHeight(this.type(decoratorConfiguration), x, z);
        if (yHeightMap <= decorationContext.getMinBuildHeight()) {
            cir.setReturnValue(Stream.of());
        } else {
            int y = blockPos.getY() + random.nextInt(IBigCube.DIAMETER_IN_BLOCKS);
            cir.setReturnValue(Stream.of(new BlockPos(x, y, z)));
        }
    }
}
