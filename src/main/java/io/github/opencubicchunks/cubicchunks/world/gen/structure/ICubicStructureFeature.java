package io.github.opencubicchunks.cubicchunks.world.gen.structure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;

public interface ICubicStructureFeature<C extends FeatureConfiguration> {

    boolean isFeatureSection(ChunkGenerator chunkGenerator, BiomeSource biomeSource, long worldSeed, WorldgenRandom random, SectionPos sectionPos,
                             Biome biome, SectionPos chunkPos, C config, LevelHeightAccessor levelHeightAccessor);

    StructureStart<?> generateCC(RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource, StructureManager structureManager, long worldSeed,
                                 SectionPos chunkPos, Biome biome, int referenceCount, WorldgenRandom worldgenRandom, StructureFeatureConfiguration structureFeatureConfiguration,
                                 C featureConfiguration, LevelHeightAccessor levelHeightAccessor);
}
