package io.github.opencubicchunks.cubicchunks.chunk.biome;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BiomeContainerAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.IdMap;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import org.jetbrains.annotations.Nullable;

public class CubeBiomeContainer extends ChunkBiomeContainer {
    private final ChunkBiomeContainer[] containers;

    public CubeBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor heightAccessor) {
        super(idMap, heightAccessor, (Biome[]) null);
        if (!((CubicLevelHeightAccessor) heightAccessor).isCubic()) {
            throw new UnsupportedOperationException("Calling a cube class in a non cubic world.");
        }

        this.containers = new ChunkBiomeContainer[IBigCube.DIAMETER_IN_SECTIONS * IBigCube.DIAMETER_IN_SECTIONS];
    }

    public CubeBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor heightAccessor, int[] incomingBiomes) {
        this(idMap, heightAccessor);
        int biomeArraySize = (1 << BiomeContainerAccess.getWidthBits() + BiomeContainerAccess.getWidthBits()) * ceilDiv(heightAccessor.getHeight(), 4);
        int[][] perBiomeContainerBiomeArray = new int[containers.length][];

        for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
            for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                int containerIDX = columnZ * IBigCube.DIAMETER_IN_SECTIONS + columnX;
                int[] biomeArray = new int[biomeArraySize];

                int offset = (incomingBiomes.length / containers.length) * containerIDX;
                int iterateRange = (incomingBiomes.length / containers.length) * (containerIDX + 1);

                for (int i = offset; i < iterateRange; i++) {
                    int incomingBiome = incomingBiomes[i];
                    biomeArray[i - offset] = incomingBiome;
                }

                perBiomeContainerBiomeArray[containerIDX] = biomeArray;

                ChunkBiomeContainer biomeContainer = new ChunkBiomeContainer(idMap, heightAccessor, perBiomeContainerBiomeArray[containerIDX]);

                containers[containerIDX] = biomeContainer;
            }
        }
    }


    public CubeBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor levelHeightAccessor, CubePos chunkPos, BiomeSource biomeSource) {
        this(idMap, levelHeightAccessor, chunkPos, biomeSource, null);
    }

    public CubeBiomeContainer(IdMap<Biome> idMap, LevelHeightAccessor heightAccessor, CubePos cubePos, BiomeSource biomeSource, @Nullable int[] incomingBiomes) {
        this(idMap, heightAccessor);
        int biomeArraySize = (1 << BiomeContainerAccess.getWidthBits() + BiomeContainerAccess.getWidthBits()) * ceilDiv(heightAccessor.getHeight(), 4);

        int[][] perBiomeContainerBiomeArray = new int[containers.length][];

        if (incomingBiomes != null) {
            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                    int containerIDX = columnZ * IBigCube.DIAMETER_IN_SECTIONS + columnX;
                    int[] biomeArray = new int[biomeArraySize];

                    int offset = (incomingBiomes.length / containers.length) * containerIDX;
                    int iterateRange = (incomingBiomes.length / containers.length) * (containerIDX + 1);

                    for (int i = offset; i < iterateRange; i++) {
                        int incomingBiome = incomingBiomes[i];
                        biomeArray[i] = incomingBiome;
                    }

                    perBiomeContainerBiomeArray[containerIDX] = biomeArray;

                    ChunkPos pos = cubePos.asChunkPos(columnX, columnZ);

                    ChunkBiomeContainer biomeContainer = new ChunkBiomeContainer(idMap, heightAccessor, pos, biomeSource, perBiomeContainerBiomeArray[containerIDX]);

                    containers[containerIDX] = biomeContainer;
                }
            }
        } else {
            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                    int containerIDX = columnZ * IBigCube.DIAMETER_IN_SECTIONS + columnX;
                    ChunkPos pos = cubePos.asChunkPos(columnX, columnZ);
                    containers[containerIDX] = new ChunkBiomeContainer(idMap, heightAccessor, pos, biomeSource, incomingBiomes);
                }
            }
        }
    }

    private static int ceilDiv(int i, int j) {
        return (i + j - 1) / j;
    }

    public void setContainerForColumn(int columnX, int columnZ, ChunkBiomeContainer container) {
        containers[columnZ * IBigCube.DIAMETER_IN_SECTIONS + columnX] = container;
    }

    public ChunkBiomeContainer getContainerForColumn(int columnX, int columnZ) {
        return containers[columnZ * IBigCube.DIAMETER_IN_SECTIONS + columnX];
    }

    @Override public int[] writeBiomes() {
        int totalLength = 0;
        Biome[][] allBiomes = new Biome[containers.length][];

        for (int i = 0, containersLength = containers.length; i < containersLength; i++) {
            ChunkBiomeContainer container = containers[i];
            allBiomes[i] = ((BiomeContainerAccess) container).getBiomes();
            totalLength += allBiomes[i].length;
        }
        int[] biomeIDs = new int[totalLength];

        int i = 0;
        for (Biome[] biome : allBiomes) {
            for (Biome biome1 : biome) {
                biomeIDs[i] = ((BiomeContainerAccess) this).getBiomeRegistry().getId(biome1);
                i++;
            }
        }
        return biomeIDs;
    }

    @Override public Biome getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        int chunkX = Coords.cubeLocalSection(QuartPos.toSection(biomeX));
        int chunkZ = Coords.cubeLocalSection(QuartPos.toSection(biomeZ));

        ChunkBiomeContainer container = containers[chunkZ * IBigCube.DIAMETER_IN_SECTIONS + chunkX];
        return container.getNoiseBiome(biomeX, biomeY, biomeZ);
    }
}
