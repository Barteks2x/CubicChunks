package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.structure;

import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.opencubicchunks.cubicchunks.world.gen.structure.CubicStructureConfiguration;
import io.github.opencubicchunks.cubicchunks.world.gen.structure.ICubicStructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Mixin(StructureFeatureConfiguration.class)
public abstract class MixinStructureFeatureConfiguration implements ICubicStructureFeatureConfiguration {

    @Mutable @Shadow @Final public static Codec<StructureFeatureConfiguration> CODEC;

    private Optional<CubicStructureConfiguration> cubicStructureConfiguration = Optional.empty();

    @Override public void setVerticalSettings(Optional<CubicStructureConfiguration> cubicStructureConfigurationVal) {
        this.cubicStructureConfiguration = cubicStructureConfigurationVal;
    }

    @Nullable @Override public Optional<CubicStructureConfiguration> getVerticalSettings() {
        return this.cubicStructureConfiguration;
    }

    @SuppressWarnings({ "Convert2MethodRef", "UnresolvedMixinReference", "ConstantConditions" }) @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void setNewStructureCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.<StructureFeatureConfiguration>create((instance) -> {
            return instance.group(Codec.intRange(0, 4096).fieldOf("spacing").forGetter((config) -> {
                return config.spacing();
            }), Codec.intRange(0, 4096).fieldOf("separation").forGetter((config) -> {
                return config.separation();
            }), Codec.optionalField("vertical_settings", CubicStructureConfiguration.CODEC).orElse(Optional.empty()).forGetter((config) -> {
                return ((ICubicStructureFeatureConfiguration) config).getVerticalSettings();
            }), Codec.intRange(0, Integer.MAX_VALUE).fieldOf("salt").forGetter((config) -> {
                return config.salt();
            })).apply(instance, (hSpacing, hSeparation, verticalSettings, salt) -> {
                StructureFeatureConfiguration structureFeatureConfiguration = new StructureFeatureConfiguration(hSpacing, hSeparation, salt);
                ((ICubicStructureFeatureConfiguration) structureFeatureConfiguration).setVerticalSettings(verticalSettings);
                return structureFeatureConfiguration;
            });
        }).comapFlatMap((config) -> {
            return config.spacing() <= config.separation() ? DataResult.error("Spacing has to be smaller than separation") : DataResult.success(config);
        }, Function.identity());
    }
}
