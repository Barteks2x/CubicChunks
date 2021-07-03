package io.github.opencubicchunks.cubicchunks.chunk;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;

public interface IClientCubeProvider extends ICubeProvider {

    void drop(int x, int y, int z);

    void setCenter(int x, int y, int z);

    int getCenterX();

    int getCenterY();

    int getCenterZ();

    void updateChunkSectionArrays(Direction.AxisDirection axisDirection);

    void resizeAndFillChunkArrays();

    ClientChunkProviderCubeArray getCubeArray();

    void updateCubeViewRadius(int hDistance, int vDistance);

    BigCube replaceWithPacketData(int cubeX, int cubeY, int cubeZ, @Nullable ChunkBiomeContainer biomes, FriendlyByteBuf readBuffer, CompoundTag nbtTagIn,
                                  boolean cubeExists);
}