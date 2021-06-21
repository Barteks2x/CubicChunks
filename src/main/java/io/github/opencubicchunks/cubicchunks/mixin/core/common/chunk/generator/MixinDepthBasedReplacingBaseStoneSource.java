package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.generator;

import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DepthBasedReplacingBaseStoneSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DepthBasedReplacingBaseStoneSource.class)
public class MixinDepthBasedReplacingBaseStoneSource {

    @Shadow @Final private BlockState normalBlock;
    @Shadow @Final private BlockState replacementBlock;

    @Shadow @Final private long seed;
    @Shadow @Final private WorldgenRandom random;

    private long seedX, seedY, seedZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(long newSeed, BlockState blockState, BlockState blockState2, NoiseGeneratorSettings noiseGeneratorSettings, CallbackInfo ci) {
        // precompute axis seeds
        this.seedX = random.nextLong();
        this.seedY = random.nextLong();
        this.seedZ = random.nextLong();
        random.setSeed(newSeed);
    }

    @Inject(method = "getBaseBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setBaseStoneSeed(JIII)J", shift = At.Shift.BEFORE),
        cancellable = true)
    private void dontCalculateBaseStone(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        double probability = Mth.clampedMap(y, -8.0D, 0.0D, 1.0D, 0.0D);
        if (probability <= 0) {
            cir.setReturnValue(this.normalBlock);
        } else if (probability >= 1) {
            cir.setReturnValue(this.replacementBlock);
        } else {
            long newSeed = NonAtomicWorldgenRandom.scramble(x * this.seedX ^ y * this.seedY ^ z * this.seedZ ^ this.seed);
            newSeed = NonAtomicWorldgenRandom.nextSeed(newSeed);
            float sample = NonAtomicWorldgenRandom.sampleFloat(newSeed);
            cir.setReturnValue(sample < probability ? this.replacementBlock : this.normalBlock);
        }
    }
}
