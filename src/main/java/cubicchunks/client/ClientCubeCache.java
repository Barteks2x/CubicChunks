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
package cubicchunks.client;

import cubicchunks.util.CubeCoords;
import cubicchunks.world.ICubeCache;
import cubicchunks.world.ICubicWorldClient;
import cubicchunks.world.column.BlankColumn;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.BlankCube;
import cubicchunks.world.cube.Cube;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class ClientCubeCache extends ChunkProviderClient implements ICubeCache {

	private ICubicWorldClient world;
	private BlankColumn blankColumn;
	private Cube blankCube;

	public ClientCubeCache(ICubicWorldClient world) {
		super((World) world);

		this.world = world;
		this.blankColumn = new BlankColumn(world, 0, 0);
		this.blankCube = new BlankCube(world, blankColumn);
	}

	@Override
	public Column loadChunk(int cubeX, int cubeZ) {

		// is this chunk already loaded?
		Column column = (Column) this.chunkMapping.get(ChunkPos.asLong(cubeX, cubeZ));
		if (column != null) {
			return column;
		}

		// make a new one
		column = new Column(this.world, cubeX, cubeZ);

		this.chunkMapping.put(ChunkPos.asLong(cubeX, cubeZ), column);

		column.setChunkLoaded(true);
		return column;
	}

	@Override
	public void unloadCube(Cube cube) {
		cube.getColumn().removeCube(cube.getY());
	}

	public void unloadColumn(int columnX, int columnZ) {
		//unload even if not empty
		//server sends unload packets, it must be right.
		this.chunkMapping.remove(ChunkPos.asLong(columnX, columnZ));
	}

	@Override
	public Column getColumn(int columnX, int columnZ) {
		return provideChunk(columnX, columnZ);
	}

	@Override//I hope it was provideChunk
	public Column provideChunk(int cubeX, int cubeZ) {
		// is this chunk already loaded?
		Column column = (Column) this.chunkMapping.get(ChunkPos.asLong(cubeX, cubeZ));
		if (column != null) {
			return column;
		}

		return this.blankColumn;
	}

	@Override
	public boolean cubeExists(int cubeX, int cubeY, int cubeZ) {
		// cubes always exist on the client
		return true;
	}

	@Override
	public boolean cubeExists(CubeCoords coords) {
		return this.cubeExists(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	public Cube getCube(int cubeX, int cubeY, int cubeZ) {
		Cube cube = getColumn(cubeX, cubeZ).getCube(cubeY);
		if (cube == null) {
			return this.blankCube;
		}

		return cube;
	}

	public Cube getCube(CubeCoords coords) {
		return this.getCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	public String makeString() {
		return "MultiplayerChunkCache: " + this.chunkMapping.values().stream().map(c->((Column)c).getLoadedCubes().size()).reduce((a,b)->a+b).orElse(-1) + "/" + this.chunkMapping.size();
	}
}
