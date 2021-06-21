package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.generator;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.opencubicchunks.cubicchunks.chunk.NoiseSettingsCC;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseSamplingSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseSlideSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseSettings.class)
public abstract class MixinNoiseSettings implements NoiseSettingsCC {

    @Mutable @Shadow @Final public static Codec<NoiseSettings> CODEC;

    private boolean slides = true;

    @Override public void setSlide(boolean slideSetting) {
        this.slides = slideSetting;
    }

    @Override public boolean slides() {
        return slides;
    }

    @Shadow private static DataResult<NoiseSettings> guardY(NoiseSettings config) {
        throw new Error("Mixin failed to apply");
    }

    @SuppressWarnings("UnresolvedMixinReference") @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void swapCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.<NoiseSettings>create((instance) -> {
            return instance.group(Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).fieldOf("min_y").forGetter(NoiseSettings::minY),
                Codec.intRange(0, DimensionType.Y_SIZE).fieldOf("height").forGetter(NoiseSettings::height),
                NoiseSamplingSettings.CODEC.fieldOf("sampling").forGetter(NoiseSettings::noiseSamplingSettings),
                NoiseSlideSettings.CODEC.fieldOf("top_slide").forGetter(NoiseSettings::topSlideSettings),
                NoiseSlideSettings.CODEC.fieldOf("bottom_slide").forGetter(NoiseSettings::bottomSlideSettings),
                Codec.intRange(1, 4).fieldOf("size_horizontal").forGetter(NoiseSettings::noiseSizeHorizontal),
                Codec.intRange(1, 4).fieldOf("size_vertical").forGetter(NoiseSettings::noiseSizeVertical),
                Codec.DOUBLE.fieldOf("density_factor").forGetter(NoiseSettings::densityFactor),
                Codec.DOUBLE.fieldOf("density_offset").forGetter(NoiseSettings::densityOffset),
                Codec.BOOL.fieldOf("simplex_surface_noise").forGetter(NoiseSettings::useSimplexSurfaceNoise),
                Codec.BOOL.optionalFieldOf("random_density_offset", false, Lifecycle.experimental()).forGetter(NoiseSettings::randomDensityOffset),
                Codec.BOOL.optionalFieldOf("island_noise_override", false, Lifecycle.experimental()).forGetter(NoiseSettings::islandNoiseOverride),
                Codec.BOOL.optionalFieldOf("amplified", false, Lifecycle.experimental()).forGetter(NoiseSettings::isAmplified),
                Codec.BOOL.optionalFieldOf("slideNoise", false, Lifecycle.experimental()).forGetter((noiseSettings -> ((NoiseSettingsCC) noiseSettings).slides()))).apply(instance,
                (minY, maxY, samplingSettings, topSlide, bottomSlide, sizeH, sizeV, densityFactor, densityOffset, simplexSurfaceNoise, randomDensityOffset, islandNoiseOverride, amplified,
                 slideNoise) -> {
                    NoiseSettings noiseSettings =
                        NoiseSettings.create(minY, maxY, samplingSettings, topSlide, bottomSlide, sizeH, sizeV, densityFactor, densityOffset, simplexSurfaceNoise, randomDensityOffset,
                            islandNoiseOverride, amplified);
                    ((NoiseSettingsCC) noiseSettings).setSlide(slideNoise);
                    return noiseSettings;
                });
        }).comapFlatMap(MixinNoiseSettings::guardY, Function.identity());
    }
}
