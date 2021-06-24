package io.github.opencubicchunks.cubicchunks.chunk.biome;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BiomeContainerAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import org.jetbrains.annotations.Nullable;

public class ProtoColumnBiomeContainer extends ChunkBiomeContainer {
    private final ChunkBiomeContainer[] containers;
    private final int minCubeY;
    private int fallBackIdx = -1;

    public ProtoColumnBiomeContainer(IBigCube[] cubes, int xSectionOffset, int zSectionOffset) {
        super(null, new ProtoColumnHeightAccess(cubes), (Biome[]) null);
        if (!((CubicLevelHeightAccessor) cubes[0]).isCubic()) {
            throw new UnsupportedOperationException("Calling a cube class in a non cubic world.");
        }

        this.minCubeY = cubes[0].getCubePos().getY();

        ChunkBiomeContainer[] containers = new ChunkBiomeContainer[cubes.length];
        for (int yCube = 0; yCube < cubes.length; yCube++) {
            ChunkBiomeContainer biomeContainer = cubes[yCube].getBiomes();

            if (biomeContainer instanceof CubeBiomeContainer) {
                if (fallBackIdx == -1) {
                    fallBackIdx = yCube;
                }

                containers[yCube] = ((CubeBiomeContainer) biomeContainer).getContainerForColumn(xSectionOffset, zSectionOffset);
            } else {
                if (biomeContainer != null) {
                    throw new UnsupportedOperationException("Attempted to set a non cube column biome container, this operation is not supported. Attempted to set a biome container of "
                        + "type: " + biomeContainer.getClass().getSimpleName());
                }
            }
        }
        this.containers = containers;
    }

    protected ProtoColumnBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor levelHeightAccessor, Biome[] biomes) {
        super(idMap, levelHeightAccessor, biomes);
        throw new UnsupportedOperationException("We are Using the wrong constructor!");
    }

    public ProtoColumnBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor levelHeightAccessor, int[] is) {
        super(idMap, levelHeightAccessor, is);
        throw new UnsupportedOperationException("We are Using the wrong constructor!");
    }

    public ProtoColumnBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor levelHeightAccessor, ChunkPos chunkPos, BiomeSource biomeSource) {
        super(idMap, levelHeightAccessor, chunkPos, biomeSource);
        throw new UnsupportedOperationException("We are Using the wrong constructor!");
    }

    public ProtoColumnBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor levelHeightAccessor, ChunkPos chunkPos, BiomeSource biomeSource, @Nullable int[] is) {
        super(idMap, levelHeightAccessor, chunkPos, biomeSource, is);
        throw new UnsupportedOperationException("We are Using the wrong constructor!");
    }

    @Override public int[] writeBiomes() {
        throw new UnsupportedOperationException("We should not be saving the data in this container!");
    }

    @Override public Biome getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        @Nullable
        ChunkBiomeContainer container = containers[Math.abs(Coords.blockToCube(biomeY) - minCubeY)];

        if (container == null) {
            return ((BiomeContainerAccess) containers[fallBackIdx]).getBiomes()[0];
        }
        return container.getNoiseBiome(biomeX, biomeY, biomeZ);
    }


    public static class ProtoColumnHeightAccess implements LevelHeightAccessor {

        private final int minBuildHeight;
        private final int height;

        public ProtoColumnHeightAccess(IBigCube[] cubes) {
            this.minBuildHeight = cubes[0].getCubePos().minCubeY();
            this.height = Math.abs(cubes[cubes.length - 1].getCubePos().maxCubeY() - cubes[0].getCubePos().minCubeY());
        }

        @Override public int getHeight() {
            return this.height;
        }

        @Override public int getMinBuildHeight() {
            return this.minBuildHeight;
        }
    }
}
