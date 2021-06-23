package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.biome;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.NoiseAndSurfaceBuilderHelper;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRandom;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.biome.BiomeGetter;
import io.github.opencubicchunks.cubicchunks.world.gen.feature.CCFeatures;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
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


    private final Set<ResourceLocation> featureBlacklist = new HashSet<>();

    @Override
    public void generate(StructureFeatureManager structureManager, ChunkGenerator chunkGenerator, CubeWorldGenRegion region, long seed, CubeWorldGenRandom random,
                         BlockPos blockPos) {
        List<List<Supplier<ConfiguredFeature<?, ?>>>> list = this.generationSettings.features();

        for (int genStepIDX = 0; genStepIDX < GenerationStep.Decoration.values().length; ++genStepIDX) {
            int k = 0;
            if (structureManager.shouldGenerateFeatures()) {

                for (StructureFeature<?> structure : this.structuresByStep.getOrDefault(genStepIDX, Collections.emptyList())) {

                    random.setDecorationSeed(seed, k, genStepIDX);
                    int minSectionX = Coords.sectionToMinBlock(Coords.blockToSection(blockPos.getX()));
                    int minSectionZ = Coords.sectionToMinBlock(Coords.blockToSection(blockPos.getZ()));

                    try {
                        region.usesRegionHeightMap();
                        structureManager.startsForFeature(SectionPos.of(blockPos), structure).forEach((structureStart) -> {
                            structureStart.placeInChunk(region, structureManager, chunkGenerator, random,
                                new BoundingBox(minSectionX, Coords.cubeToMinBlock(region.getMinCubeY()) + 1, minSectionZ, minSectionX + 15, Coords.cubeToMaxBlock(region.getMaxCubeY()) - 1,
                                    minSectionZ + 15),
                                new ChunkPos(blockPos));
                        });
                    } catch (Exception e) {
                        CrashReport crashReport = CrashReport.forThrowable(e, "Structure Feature placement");
                        crashReport.addCategory("Feature").setDetail("Id", Registry.STRUCTURE_FEATURE.getKey(structure)).setDetail("Description", () -> {
                            return structure.toString();
                        });
                        throw new ReportedException(crashReport);
                    }
                }
            }

            if (featureBlacklist.isEmpty()) {
                getBlacklist();
            }


            if (list.size() > genStepIDX) {
                for (Supplier<ConfiguredFeature<?, ?>> configuredFeatureSupplier : list.get(genStepIDX)) {
                    ConfiguredFeature<?, ?> configuredFeature = configuredFeatureSupplier.get();

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
                            region.usesCubeHeightMap();
                            configuredFeature.place(region, chunkGenerator, random, blockPos);
                        } catch (Exception e) {
                            CrashReport crashReport2 = CrashReport.forThrowable(e, "Feature placement");
                            crashReport2.addCategory("Feature").setDetail("Id", key).setDetail("Config", configuredFeature.config).setDetail("Description", () -> {
                                return configuredFeature1.feature.toString();
                            });
                            CubicChunks.LOGGER.fatal(crashReport2.getFriendlyReport());
                            throw new ReportedException(crashReport2);
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
        try {
            int cubicChunksSurfaceHeight;
            if (chunk.getMinBuildHeight() > surfaceMinHeight) {
                cubicChunksSurfaceHeight = chunk.getMinBuildHeight();
            } else if (chunk.getMaxBuildHeight() < surfaceMinHeight) {
                cubicChunksSurfaceHeight = Integer.MAX_VALUE;
            } else {
                cubicChunksSurfaceHeight = surfaceMinHeight;
            }

            configuredSurfaceBuilder.apply(random, chunk, (Biome) (Object) this, x, z, worldHeight, noise, defaultBlock, defaultFluid, seaLevel, cubicChunksSurfaceHeight, seed);
        } catch (NoiseAndSurfaceBuilderHelper.StopGeneratingThrowable ignored) {
            // used as a way to stop the surface builder when it goes below the current cube
        }
    }

    //TODO: Remove this blacklist.
    private void getBlacklist() {

        List<ResourceLocation> resourceLocationList = Arrays.asList(
            //Requires similar 1.12 implementation, see: https://github.com/OpenCubicChunks/CubicWorldGen/blob/27de56d2f792513873584b2f8fd9f3082fb259ec/src/main/java/io/github
            // /opencubicchunks/cubicchunks/cubicgen/customcubic/populator/DefaultDecorator.java#L331-L361
            new ResourceLocation("spring_water"),
            //Requires similar 1.12 implementation, see: https://github.com/OpenCubicChunks/CubicWorldGen/blob/27de56d2f792513873584b2f8fd9f3082fb259ec/src/main/java/io/github
            // /opencubicchunks/cubicchunks/cubicgen/customcubic/populator/DefaultDecorator.java#L331-L361
            new ResourceLocation("spring_lava")
        );

        featureBlacklist.addAll(resourceLocationList);
    }
}
