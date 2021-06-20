package io.github.opencubicchunks.cubicchunks.world.gen.feature;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.gen.placement.CCPlacement;
import io.github.opencubicchunks.cubicchunks.world.gen.placement.CubicLakePlacementConfig;
import io.github.opencubicchunks.cubicchunks.world.gen.placement.UserFunction;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneDecoratorConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.FeatureDecorator;

public class CCFeatures {

    public static final ConfiguredFeature<?, ?> CC_WATER_LAKE = createCCConfiguredFeature("lake_water", Feature.LAKE
        .configured(new BlockStateConfiguration(Blocks.WATER.defaultBlockState()))
        .decorated(CCPlacement.CUBIC_LAKE
            .configured(new CubicLakePlacementConfig(
                UserFunction.builder()
                    // same as vanilla
                    .point(0, 1 / 64f)
                    .build(),
                UserFunction.builder()
                    // same as vanilla for y=0-128, probabilities get too low at 2xx heights so dont use them
                    .point(-1, 0.25f)
                    .point(0, 0.25f)
                    .point(128, 0.125f)
                    .point(129, 0.125f)
                    .build()
            ))
        ));


    public static final ConfiguredFeature<?, ?> CC_LAVA_LAKE = createCCConfiguredFeature("lake_lava", Feature.LAKE
        .configured(new BlockStateConfiguration(Blocks.LAVA.defaultBlockState()))
        .decorated(CCPlacement.CUBIC_LAKE
            .configured(new CubicLakePlacementConfig(
                UserFunction.builder()
                    // same as vanilla for y0-127, probabilities near y=256 are very low, so don't use them
                    .point(0, 4 / 263f)
                    .point(7, 4 / 263f)
                    .point(8, 247 / 16306f)
                    .point(62, 193 / 16306f)
                    .point(63, 48 / 40765f)
                    .point(127, 32 / 40765f)
                    .point(128, 32 / 40765f)
                    .build(),
                UserFunction.builder()
                    // sample vanilla probabilities at y=0, 31, 63, 95, 127
                    .point(-1, 19921 / 326120f)
                    .point(0, 19921 / 326120f)
                    .point(31, 1332 / 40765f)
                    .point(63, 579 / 81530f)
                    .point(95, 161 / 32612f)
                    .point(127, 129 / 40765f)
                    .point(128, 129 / 40765f)
                    .build()
            ))
        ));

    public static final ConfiguredFeature<?, ?> LAVA_LEAK_FIX = createCCConfiguredFeature("lava_leak_fix",
        CCFeature.LAVA_LEAK_FIX.configured(NoneFeatureConfiguration.INSTANCE).decorated(FeatureDecorator.NOPE.configured(NoneDecoratorConfiguration.INSTANCE)));

    public static void init() {
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>, CF extends ConfiguredFeature<FC, F>> CF createCCConfiguredFeature(String id, CF configuredFeature) {
        ResourceLocation ccID = new ResourceLocation(CubicChunks.MODID, id);
        if (BuiltinRegistries.CONFIGURED_FEATURE.keySet().contains(ccID)) {
            throw new IllegalStateException("Configured Feature ID: \"" + ccID.toString() + "\" already exists in the Configured Features registry!");
        }

        Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, ccID, configuredFeature);
        return configuredFeature;
    }
}
