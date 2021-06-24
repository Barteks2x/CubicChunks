package io.github.opencubicchunks.cubicchunks.chunk.cube;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.Maps;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.biome.ProtoColumnBiomeContainer;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.DummyHeightmap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoTickList;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

//TODO: Implement this properly for mods. Vanilla is fine.
public class CubeWorldRegionColumn extends ProtoChunk {

    private final int xSectionOffset;
    private final int zSectionOffset;
    private final IBigCube mainCube;
    private final IBigCube[] delegates;
    private final CubeWorldGenRegion cubeWorldGenRegion;
    private final LevelChunkSection[] sections;
    private final Map<Heightmap.Types, Heightmap> heightmaps;
    private final ProtoColumnBiomeContainer protoColumnBiomeContainer;


    public CubeWorldRegionColumn(ChunkPos chunkPos, int xSectionOffset, int zSectionOffset, UpgradeData upgradeData, IBigCube[] delegates, CubeWorldGenRegion cubeWorldGenRegion) {
        super(chunkPos, upgradeData, cubeWorldGenRegion);
        this.xSectionOffset = xSectionOffset;
        this.zSectionOffset = zSectionOffset;

        this.mainCube = delegates[delegates.length / 2];
        this.delegates = delegates;

        this.cubeWorldGenRegion = cubeWorldGenRegion;

        this.heightmaps = Maps.newEnumMap(Heightmap.Types.class);

        LevelChunkSection[] sections = new LevelChunkSection[(delegates.length - 1) * IBigCube.SECTION_COUNT];
        for (int i = 0; i < delegates.length; i++) {
            IBigCube cube = delegates[i];
            for (int ySectionOffset = 0; ySectionOffset < IBigCube.DIAMETER_IN_SECTIONS; ySectionOffset++) {
                LevelChunkSection cubeSection = cube.getCubeSections()[Coords.sectionToIndex(xSectionOffset, ySectionOffset, zSectionOffset)];
                sections[i + ySectionOffset] = cubeSection;
            }
        }
        this.sections = sections;

        this.protoColumnBiomeContainer = new ProtoColumnBiomeContainer(delegates, xSectionOffset, zSectionOffset);
    }

    private IBigCube getCube(BlockPos pos) {
        return getCube(Coords.blockToCube(pos.getY()));
    }

    public IBigCube getCube(int cubeY) {
        int minCubeY = delegates[0].getCubePos().getY();
        int index = Math.abs(cubeY - minCubeY);
        return delegates[index];
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        return getCube(pos).getBlockState(pos);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return getCube(pos).getFluidState(pos);
    }

    @Override public Stream<BlockPos> getLights() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public ShortList[] getPackedLights() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void addLight(short chunkSliceRel, int sectionY) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void addLight(BlockPos pos) {
        ((CubePrimer) getCube(pos)).addLight(pos);
    }

    @Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        return getCube(pos).setBlockState(pos, state, moved);
    }

    @Override public void setBlockEntity(BlockEntity blockEntity) {
        getCube(blockEntity.getBlockPos()).setBlockEntity(blockEntity);
    }

    @Override public Set<BlockPos> getBlockEntitiesPos() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return getCube(pos).getBlockEntity(pos);
    }

    @Override public Map<BlockPos, BlockEntity> getBlockEntities() {
        return super.getBlockEntities();
    }

    @Override public void addEntity(CompoundTag entityTag) {
        ListTag entityPos = entityTag.getList("Pos", 6);
        int y = (int) entityPos.getDouble(1);
        IBigCube cube = this.getCube(Coords.blockToCube(y));
        if (cube instanceof CubePrimer) {
            ((CubePrimer) cube).addEntity(entityTag);
        } else {
            CubicChunks.LOGGER.error("Attempted to add an entity tag when cube was NOT an instance of CubePrimer!");
        }
    }

    @Override public void addEntity(Entity entity) {
        getCube(entity.blockPosition()).addEntity(entity);
    }

    @Override public List<CompoundTag> getEntities() {
        return super.getEntities();
    }

    @Override public void setBiomes(ChunkBiomeContainer biomes) {
        throw new UnsupportedOperationException("This is not supported.");
    }

    @Nullable @Override public ChunkBiomeContainer getBiomes() {
        return this.protoColumnBiomeContainer;
    }

    @Override public void setUnsaved(boolean shouldSave) {

    }

    @Override public boolean isUnsaved() {
        return false;
    }

    @Override public ChunkStatus getStatus() {
        return this.mainCube.getStatus();
    }

    @Override public void setStatus(ChunkStatus status) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    @Override public Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        return Collections.unmodifiableSet(this.heightmaps.entrySet());
    }

    @Override public void setHeightmap(Heightmap.Types type, long[] heightmap) {
        this.getOrCreateHeightmapUnprimed(type).setRawData(heightmap);
    }

    @Override public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return this.heightmaps.computeIfAbsent(type, (typex) -> {
            return new DummyHeightmap(this, typex); //Essentially do nothing here.
        });
    }

    @Override public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        int yStart = cubeToMinBlock(mainCube.getCubePos().getY() + 1);
        int yEnd = cubeToMinBlock(mainCube.getCubePos().getY());

        IBigCube cube1 = getCube(new BlockPos(x, yStart, z));
        if (cube1.getCubeLocalHeight(heightmapType, x, z) >= yStart) {
            return this.cubeWorldGenRegion.getMinBuildHeight() - 1;
        }
        IBigCube cube2 = getCube(new BlockPos(x, yEnd, z));
        int height = cube2.getCubeLocalHeight(heightmapType, x, z);

        //Check whether or not height was found for this cube. If height wasn't found, move to the next cube under the current cube
        if (height <= this.cubeWorldGenRegion.getMinBuildHeight()) {
            return this.cubeWorldGenRegion.getMinBuildHeight() - 1;
        }
        return height + 1;
    }

    @Override public BlockPos getHeighestPosition(Heightmap.Types types) {
        BlockPos.MutableBlockPos mutableBlockPos = null;

        ChunkPos chunkPos = this.delegates[0].getCubePos().asChunkPos(this.xSectionOffset, this.zSectionOffset);
        for (int x = chunkPos.getMinBlockX(); x < chunkPos.getMaxBlockX(); ++x) {
            for (int z = chunkPos.getMinBlockZ(); z < chunkPos.getMaxBlockZ(); ++z) {
                int heightAtPos = this.getHeight(types, x & 15, z & 15);
                if (mutableBlockPos == null) {
                    mutableBlockPos = new BlockPos.MutableBlockPos().set(x, heightAtPos, z);
                }

                if (mutableBlockPos.getY() < heightAtPos) {
                    mutableBlockPos.set(x, heightAtPos, z);
                }
            }
        }
        return mutableBlockPos != null ? mutableBlockPos.immutable() : new BlockPos(chunkPos.getMinBlockX(), this.getHeight(types, chunkPos.getMinBlockX() & 15,
            chunkPos.getMinBlockZ() & 15), chunkPos.getMinBlockZ() & 15);
    }

    @Nullable @Override public StructureStart<?> getStartForFeature(StructureFeature<?> structure) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setStartForFeature(StructureFeature<?> structure, StructureStart<?> start) {
        this.getCube(Coords.blockToCube(start.getBoundingBox().minY())); //TODO: Wait on load order
    }

    @Override public Map<StructureFeature<?>, StructureStart<?>> getAllStarts() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setAllStarts(Map<StructureFeature<?>, StructureStart<?>> structureStarts) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public LongSet getReferencesForFeature(StructureFeature<?> structure) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void addReferenceForFeature(StructureFeature<?> structure, long reference) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public Map<StructureFeature<?>, LongSet> getAllReferences() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setAllReferences(Map<StructureFeature<?>, LongSet> structureReferences) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void markPosForPostprocessing(BlockPos pos) {
        getCube(pos).markPosForPostprocessing(pos);
    }

    @Override public ShortList[] getPostProcessing() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void addPackedPostProcess(short packedPos, int index) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public ProtoTickList<Block> getBlockTicks() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public ProtoTickList<Fluid> getLiquidTicks() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public UpgradeData getUpgradeData() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setInhabitedTime(long inhabitedTime) {
        super.setInhabitedTime(inhabitedTime);
    }

    @Override public long getInhabitedTime() {
        return super.getInhabitedTime();
    }

    @Override public void setBlockEntityNbt(CompoundTag nbt) {
        getCube(nbt.getInt("y")).setBlockEntityNbt(nbt);
    }

    @Override public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return getCube(pos).getBlockEntityNbt(pos);
    }

    @Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        return getCube(pos).getBlockEntityNbt(pos);
    }

    @Override public void removeBlockEntity(BlockPos pos) {
        getCube(pos).getBlockEntityNbt(pos);
    }

    @Nullable @Override public BitSet getCarvingMask(GenerationStep.Carving carver) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public BitSet getOrCreateCarvingMask(GenerationStep.Carving carver) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setCarvingMask(GenerationStep.Carving carver, BitSet mask) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setLightEngine(LevelLightEngine lightingProvider) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    @Override public boolean isLightCorrect() {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public void setLightCorrect(boolean lightOn) {
        throw new UnsupportedOperationException("This is not yet implemented.");
    }

    @Override public LevelChunkSection[] getSections() {
        return this.sections;
    }

    @Override public int getMinBuildHeight() {
        return this.delegates[0].getMinBuildHeight();
    }

    @Override public int getHeight() {
        return Math.abs(this.delegates[delegates.length - 1].getMaxBuildHeight() - this.delegates[0].getMinBuildHeight());
    }
}
