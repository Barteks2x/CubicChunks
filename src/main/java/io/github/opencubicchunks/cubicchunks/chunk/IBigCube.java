package io.github.opencubicchunks.cubicchunks.chunk;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerSection;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.SurfaceTrackerSection;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.meta.EarlyConfig;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.FeatureAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.apache.logging.log4j.LogManager;

public interface IBigCube extends BlockGetter, ChunkAccess, FeatureAccess {

    int SECTION_DIAMETER = 16;
    int DIAMETER_IN_SECTIONS = EarlyConfig.getDiameterInSections();
    int SECTION_COUNT = DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS;
    int CHUNK_COUNT = DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS;
    int DIAMETER_IN_BLOCKS = SECTION_DIAMETER * DIAMETER_IN_SECTIONS;
    int BLOCK_COUNT = DIAMETER_IN_BLOCKS * DIAMETER_IN_BLOCKS * DIAMETER_IN_BLOCKS;
    int BLOCK_COLUMNS_PER_SECTION = SECTION_DIAMETER * SECTION_DIAMETER;
    int SIZE_BITS = (int) Math.round(Math.log(DIAMETER_IN_BLOCKS) / Math.log(2.0D));

    CubePos getCubePos();
    LevelChunkSection[] getCubeSections();

    ChunkStatus getCubeStatus();


    default void setFeatureBlocks(BlockPos pos, BlockState state) {
        setBlock(pos, state, false);
    }

    //BLOCK
    // this can't be setBlockState because the implementations also implement IChunk which already has setBlockState and this breaks obfuscation
    @Nullable BlockState setBlock(BlockPos pos, BlockState state, boolean isMoving);

    @Override default BlockState getBlockState(BlockPos pos) {
        return getBlockState(Coords.localX(pos), Coords.localY(pos), Coords.localZ(pos));
    }
    //TODO: remove this getBlockState from IBigCube to match IChunk
    BlockState getBlockState(int x, int y, int z);

    //TILE ENTITY
    // can't be add/removeTileEntity due to obfuscation issues with IChunk
    default void setCubeBlockEntity(CompoundTag nbt) {
        LogManager.getLogger().warn("Trying to set a BlockEntity, but this operation is not supported.");
    }
    void setCubeBlockEntity(BlockEntity tileEntity);
    void removeCubeBlockEntity(BlockPos pos);

    Set<BlockPos> getCubeTileEntitiesPos();

    @Nullable CompoundTag getCubeBlockEntityNbtForSaving(BlockPos pos);

    @Nullable CompoundTag getCubeDeferredTileEntity(BlockPos pos);

    Map<StructureFeature<?>, StructureStart<?>> getAllCubeStructureStarts();

    //LIGHTING
    //can't be set/hasLight due to obfuscation issues with IChunk
    boolean hasCubeLight();
    void setCubeLight(boolean lightCorrectIn);

    Stream<BlockPos> getCubeLightSources();

    //MISC
    // can't be isModified due to obfuscation issues with IChunk
    void setDirty(boolean modified);
    boolean isDirty();

    //TODO: remove isEmptyCube from IBigCube to match IChunk
    boolean isEmptyCube();

    void setCubeInhabitedTime(long newCubeInhabitedTime);
    long getCubeInhabitedTime();

    int getCubeLocalHeight(Heightmap.Types heightmapType, int x, int z);

    default void loadHeightmapSection(SurfaceTrackerSection section, int localSectionX, int localSectionZZ) {
    }

    default void setLightHeightmapSection(LightSurfaceTrackerSection section, int localSectionX, int localSectionZZ) {
    }
}