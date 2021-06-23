package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.structure;

import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRandom;
import io.github.opencubicchunks.cubicchunks.world.gen.structure.ICubicConfiguredStructureFeature;
import io.github.opencubicchunks.cubicchunks.world.gen.structure.ICubicStructureFeature;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ConfiguredStructureFeature.class)
public class MixinConfiguredStructureFeature<FC extends FeatureConfiguration, F extends StructureFeature<FC>> implements ICubicConfiguredStructureFeature {
    @Shadow @Final public F feature;

    @Shadow @Final public FC config;

    @SuppressWarnings("unchecked") @Override public StructureStart<?> generate(RegistryAccess registryManager, ChunkGenerator chunkGenerator, BiomeSource biomeSource,
                                                                               StructureManager structureManager, long worldSeed, SectionPos chunkPos, Biome biome, int referenceCount,
                                                                               StructureFeatureConfiguration structureConfig, LevelHeightAccessor levelHeightAccessor) {
        return ((ICubicStructureFeature<FC>) this.feature).generateCC(registryManager, chunkGenerator, biomeSource, structureManager, worldSeed, chunkPos,
            biome, referenceCount, new CubeWorldGenRandom(), structureConfig, this.config, levelHeightAccessor);
    }
}
