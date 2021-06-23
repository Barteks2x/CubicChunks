package io.github.opencubicchunks.cubicchunks.chunk;

import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;

public interface ICubeGenerator {
    default void decorate(CubeWorldGenRegion region, StructureFeatureManager structureManager, CubePrimer chunkAccess) {
    }

    void buildSurfaceAndBedrockCC(WorldGenRegion region, ChunkAccess chunk);

    void applyCubicCarvers(long seed, BiomeManager access, ChunkAccess chunkAccess, GenerationStep.Carving carver);
}