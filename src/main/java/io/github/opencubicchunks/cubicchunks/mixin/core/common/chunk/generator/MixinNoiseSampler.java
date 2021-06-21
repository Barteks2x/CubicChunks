package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.generator;

import io.github.opencubicchunks.cubicchunks.chunk.NoiseSettingsCC;
import net.minecraft.world.level.levelgen.NoiseSampler;
import net.minecraft.world.level.levelgen.NoiseSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseSampler.class)
public class MixinNoiseSampler {

    @Shadow @Final private NoiseSettings noiseSettings;

    @Inject(method = "applySlide", at = @At("HEAD"), cancellable = true)
    private void onlySlideIslandNoise(double noise, int y, CallbackInfoReturnable<Double> cir) {
        if (!((NoiseSettingsCC) noiseSettings).slides()) {
            cir.setReturnValue(noise);
        }
    }
}
