package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.generator;


import io.github.opencubicchunks.cubicchunks.chunk.NoiseSettingsCC;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseGeneratorSettings.class)
public class MixinNoiseGeneratorSettings {

    @ModifyConstant(method = "endLikePreset", constant = @Constant(intValue = 0, ordinal = 3))
    private static int noSeaLevel(int arg0) {
        return Integer.MIN_VALUE;
    }

    @Inject(method = "overworld", at = @At("RETURN"))
    private static void setSlide(StructureSettings structuresConfig, boolean amplified, CallbackInfoReturnable<NoiseGeneratorSettings> cir) {
        ((NoiseSettingsCC) cir.getReturnValue().noiseSettings()).setSlide(false);
    }

    @Inject(method = "netherLikePreset", at = @At("RETURN"), cancellable = true)
    private static void setSlide(StructureSettings structuresConfig, BlockState defaultBlock, BlockState defaultFluid, CallbackInfoReturnable<NoiseGeneratorSettings> cir) {
        ((NoiseSettingsCC) cir.getReturnValue().noiseSettings()).setSlide(false);
    }

    @Inject(method = "endLikePreset", at = @At("RETURN"), cancellable = true)
    private static void setSlide(StructureSettings structuresConfig, BlockState defaultBlock, BlockState defaultFluid, boolean bl, boolean bl2,
                                 CallbackInfoReturnable<NoiseGeneratorSettings> cir) {
        ((NoiseSettingsCC) cir.getReturnValue().noiseSettings()).setSlide(true);
    }
}
