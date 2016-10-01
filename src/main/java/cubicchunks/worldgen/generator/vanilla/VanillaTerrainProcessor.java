/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.worldgen.generator.vanilla;

import cubicchunks.server.ServerCubeCache;
import cubicchunks.util.processor.CubeProcessor;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.cube.Cube;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraft.world.gen.ChunkProviderSettings;

import static cubicchunks.util.ChunkProviderOverworldAccess.getBiomesForGeneration;
import static cubicchunks.util.ChunkProviderOverworldAccess.getCaveGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.getMapFeaturesEnabled;
import static cubicchunks.util.ChunkProviderOverworldAccess.getMineshaftGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.getOceanMonumentGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.getRand;
import static cubicchunks.util.ChunkProviderOverworldAccess.getRavineGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.getScatteredFeatureGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.getSettings;
import static cubicchunks.util.ChunkProviderOverworldAccess.getStrongholdGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.getVillageGenerator;
import static cubicchunks.util.ChunkProviderOverworldAccess.replaceBiomeBlocks;
import static cubicchunks.util.ChunkProviderOverworldAccess.setBiomesForGeneration;
import static cubicchunks.util.ChunkProviderOverworldAccess.setBlocksInChunk;
import static cubicchunks.util.Coords.blockToLocal;

public class VanillaTerrainProcessor implements CubeProcessor {
	private final ICubicWorldServer world;
	private final ChunkProviderOverworld vanillaGen;

	public VanillaTerrainProcessor(ICubicWorldServer world, ChunkProviderOverworld vanillaGen) {
		this.world = world;
		this.vanillaGen = vanillaGen;
	}

	@Override public void calculate(Cube cube) {
		if (cube.getY() < 0) {
			fillCube(cube, Blocks.STONE.getDefaultState());
			return;
		}
		if (cube.getY() >= 16) {
			return;
		}
		if(!cube.getColumn().isCompatBaseTerrainDone()) {
			//setting it before generation is important as the generator trues to generate more cubes
			//these generated cubes should be empty so that they can be filled with blocks
			//not doing that will cause severe performance issues
			cube.getColumn().setCompatBaseTerrainDone(true);
			this.generateVanillaChunk(cube);

		}
	}

	private void generateVanillaChunk(Cube cube) {
		for(int y = 0; y < 16; y++) {
			//force create empty cubes
			world.getCubeCache().loadCube(cube.getX(), y, cube.getZ(), ServerCubeCache.LoadType.LOAD_OR_GENERATE);
		}
		int x = cube.getX();
		int z = cube.getZ();
		getRand(this.vanillaGen).setSeed((long) x*341873128712L + (long) z*132897987541L);
		ChunkPrimer chunkprimer = new ChunkPrimer();

		setBlocksInChunk(this.vanillaGen, x, z, chunkprimer);
		Biome[] newBiomes = this.world.getBiomeProvider()
				.getBiomes(getBiomesForGeneration(this.vanillaGen), x*16, z*16, 16, 16);
		setBiomesForGeneration(this.vanillaGen, newBiomes);
		replaceBiomeBlocks(this.vanillaGen, x, z, chunkprimer, newBiomes);

		ChunkProviderSettings settings = getSettings(this.vanillaGen);
		if (settings.useCaves) {
			getCaveGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
		}

		if (settings.useRavines) {
			getRavineGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
		}

		if (getMapFeaturesEnabled(this.vanillaGen)) {
			if (settings.useMineShafts) {
				getMineshaftGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
			}

			if (settings.useVillages) {
				getVillageGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
			}

			if (settings.useStrongholds) {
				getStrongholdGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
			}

			if (settings.useTemples) {
				getScatteredFeatureGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
			}

			if (settings.useMonuments) {
				getOceanMonumentGenerator(this.vanillaGen).generate((World) this.world, x, z, chunkprimer);
			}
		}
		for (int cubeY = 0; cubeY < 16; cubeY++) {
			Cube currCube = this.world.getCubeCache().getCube(cube.getX(), cubeY, cube.getZ());
			for(BlockPos pos : BlockPos.getAllInBoxMutable(currCube.getCoords().getMinBlockPos(), currCube.getCoords().getMaxBlockPos())) {
				int localX = blockToLocal(pos.getX());
				int localZ = blockToLocal(pos.getZ());
				IBlockState block = chunkprimer.getBlockState(localX, pos.getY(), localZ);
				if(block.getBlock() == Blocks.AIR) {
					continue;
				}
				if (block.getBlock() == Blocks.BEDROCK) {
					block = Blocks.STONE.getDefaultState();
				}
				currCube.setBlockForGeneration(pos, block);
			}
		}
	}

	private void fillCube(Cube cube, IBlockState state) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < 16; y++) {
					pos.setPos(x, y, z);
					cube.setBlockForGeneration(pos, state);
				}
			}
		}
	}
}