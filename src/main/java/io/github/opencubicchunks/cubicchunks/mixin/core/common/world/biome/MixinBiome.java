package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.NoiseAndSurfaceBuilderHelper;
import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;
import io.github.opencubicchunks.cubicchunks.config.HeightSettings;
import io.github.opencubicchunks.cubicchunks.config.reloadlisteners.HeightSettingsReloadListener;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRandom;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.SetupCubeStructureStart;
import io.github.opencubicchunks.cubicchunks.world.biome.BiomeGetter;
import io.github.opencubicchunks.cubicchunks.world.gen.feature.CCFeatures;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.surfacebuilders.ConfiguredSurfaceBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Biome.class)
public class MixinBiome implements BiomeGetter {
    @Shadow
    @Final
    private BiomeGenerationSettings generationSettings;

    @Shadow
    @Final
    private Map<Integer, List<StructureFeature<?>>> structuresByStep;


    private static final List<ResourceLocation> featureBlacklist = new ArrayList<>(Arrays.asList(

        //Broken Features
        new ResourceLocation("ice_spike"), /**{@link net.minecraft.world.level.levelgen.feature.IceSpikeFeature}**/ //Handles its placement in its own class w/ a while loop.
        new ResourceLocation("spring_water"),
        //Requires similar 1.12 implementation, see: https://github.com/OpenCubicChunks/CubicWorldGen/blob/27de56d2f792513873584b2f8fd9f3082fb259ec/src/main/java/io/github
        // /opencubicchunks/cubicchunks/cubicgen/customcubic/populator/DefaultDecorator.java#L331-L361
        new ResourceLocation("spring_lava"),
        //Requires similar 1.12 implementation, see: https://github.com/OpenCubicChunks/CubicWorldGen/blob/27de56d2f792513873584b2f8fd9f3082fb259ec/src/main/java/io/github
        // /opencubicchunks/cubicchunks/cubicgen/customcubic/populator/DefaultDecorator.java#L331-L361
        new ResourceLocation("seagrass_simple"), //Requires Carving mask
        new ResourceLocation("fossil"),
        new ResourceLocation("desert_well"), //Iterates downwards in its placement
        new ResourceLocation("ice_patch") //Iterates downwards in its placement
    ));

    @Override
    public void generate(StructureFeatureManager structureManager, ChunkGenerator chunkGenerator, CubeWorldGenRegion region, long seed, CubeWorldGenRandom random,
                         BlockPos blockPos, boolean generatesStructures, BoundingBox structureBoundingBox) {
        List<List<Supplier<ConfiguredFeature<?, ?>>>> list = this.generationSettings.features();

        for (int genStepIDX = 0; genStepIDX < GenerationStep.Decoration.values().length; ++genStepIDX) {
            int k = 0;
            if (generatesStructures) {

                for (StructureFeature<?> structure : this.structuresByStep.getOrDefault(genStepIDX, Collections.emptyList())) {
                    region.upgradeY(HeightSettingsReloadListener.STRUCTURE_HEIGHT_SETTINGS.getOrDefault(structure, HeightSettings.DEFAULT));
                    random.setDecorationSeed(seed, k, genStepIDX);

                    try {
                        structureManager.startsForFeature(SectionPos.of(blockPos), structure).forEach((structureStart) -> {
                            ((SetupCubeStructureStart) structureStart).placeInCube(region, structureManager, chunkGenerator, random,
                                structureBoundingBox, blockPos);
                        });
                    } catch (Exception e) {
                        CrashReport crashReport = CrashReport.forThrowable(e, "Structure Feature placement");
                        crashReport.addCategory("Feature").setDetail("Id", Registry.STRUCTURE_FEATURE.getKey(structure)).setDetail("Description", () -> {
                            return structure.toString();
                        });
                        CubicChunks.commonConfig().getWorldExceptionHandler().wrapException(new ReportedException(crashReport));
                    }
                }
            }

            if (list.size() > genStepIDX) {
                for (Supplier<ConfiguredFeature<?, ?>> configuredFeatureSupplier : list.get(genStepIDX)) {
                    ConfiguredFeature<?, ?> configuredFeature = configuredFeatureSupplier.get();
                    //noinspection SuspiciousMethodCalls
                    region.upgradeY(HeightSettingsReloadListener.STRUCTURE_HEIGHT_SETTINGS.getOrDefault(configuredFeature.feature, HeightSettings.DEFAULT));

                    ResourceLocation key = region.getLevel().getServer().registryAccess().registry(Registry.CONFIGURED_FEATURE_REGISTRY).get().getKey(configuredFeature);
                    if (key != null) {
                        if (key.equals(new ResourceLocation("lake_lava"))) {
                            configuredFeature = CCFeatures.CC_LAVA_LAKE;
                        } else if (key.equals(new ResourceLocation("lake_water"))) {
                            configuredFeature = CCFeatures.CC_WATER_LAKE;
                        }
                    }

                    ConfiguredFeature<?, ?> configuredFeature1 = configuredFeature;

                    if (!featureBlacklist.contains(key)) {
                        try {
                            configuredFeature.place(region, chunkGenerator, random, blockPos);
                        } catch (Exception e) {
                            CrashReport crashReport2 = CrashReport.forThrowable(e, "Feature placement");
                            crashReport2.addCategory("Feature").setDetail("Id", key).setDetail("Config", configuredFeature.config).setDetail("Description", () -> {
                                return configuredFeature1.feature.toString();
                            });
                            CubicChunks.LOGGER.fatal(crashReport2.getFriendlyReport());
                            CubicChunks.commonConfig().getWorldExceptionHandler().wrapException(new ReportedException(crashReport2));
                        }
                    }
                }
            }
        }
    }

    /**
     * @author Corgitaco
     * @reason Catch the surface builder
     */
    @Inject(method = "buildSurfaceAt", at = @At("HEAD"), cancellable = true)
    public void buildSurfaceAt(Random random, ChunkAccess chunk, int x, int z, int worldHeight, double noise, BlockState defaultBlock, BlockState defaultFluid, int seaLevel,
                               int surfaceMinHeight, long seed,
                               CallbackInfo ci) {

        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return;
        }

        ci.cancel();

        ConfiguredSurfaceBuilder<?> configuredSurfaceBuilder = this.generationSettings.getSurfaceBuilder().get();
        configuredSurfaceBuilder.initNoise(seed);

        if (!(random instanceof NonAtomicWorldgenRandom)) {
            if (chunk.getMinBuildHeight() > surfaceMinHeight) {
                surfaceMinHeight = chunk.getMinBuildHeight();
            } else if (chunk.getMaxBuildHeight() < surfaceMinHeight) {
                surfaceMinHeight = Integer.MAX_VALUE;
            }
        }

        try {
            configuredSurfaceBuilder.apply(random, chunk, (Biome) (Object) this, x, z, worldHeight, noise, defaultBlock, defaultFluid, seaLevel, surfaceMinHeight, seed);
        } catch (NoiseAndSurfaceBuilderHelper.StopGeneratingThrowable ignored) {
            CubicChunks.commonConfig().getWorldExceptionHandler().wrapException(ignored);
        }
    }
}
