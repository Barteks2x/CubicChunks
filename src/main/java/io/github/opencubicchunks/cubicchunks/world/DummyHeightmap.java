package io.github.opencubicchunks.cubicchunks.world;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

public class DummyHeightmap extends Heightmap {
    public static final ChunkAccess DUMMY_CHUNK_ACCESS = new ChunkAccess() {
        @org.jetbrains.annotations.Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
            return null;
        }

        @Override public void setBlockEntity(BlockEntity blockEntity) {

        }

        @Override public void addEntity(Entity entity) {

        }

        @Override public Set<BlockPos> getBlockEntitiesPos() {
            return null;
        }

        @Override public LevelChunkSection[] getSections() {
            return new LevelChunkSection[0];
        }

        @Override public Collection<Map.Entry<Types, Heightmap>> getHeightmaps() {
            return null;
        }

        @Override public Heightmap getOrCreateHeightmapUnprimed(Types type) {
            return null;
        }

        @Override public int getHeight(Types type, int x, int z) {
            return 0;
        }

        @Override public BlockPos getHeighestPosition(Types types) {
            return null;
        }

        @Override public ChunkPos getPos() {
            return null;
        }

        @Override public Map<StructureFeature<?>, StructureStart<?>> getAllStarts() {
            return null;
        }

        @Override public void setAllStarts(Map<StructureFeature<?>, StructureStart<?>> structureStarts) {

        }

        @org.jetbrains.annotations.Nullable @Override public ChunkBiomeContainer getBiomes() {
            return null;
        }

        @Override public void setUnsaved(boolean shouldSave) {

        }

        @Override public boolean isUnsaved() {
            return false;
        }

        @Override public ChunkStatus getStatus() {
            return null;
        }

        @Override public void removeBlockEntity(BlockPos pos) {

        }

        @Override public ShortList[] getPostProcessing() {
            return new ShortList[0];
        }

        @org.jetbrains.annotations.Nullable @Override public CompoundTag getBlockEntityNbt(BlockPos pos) {
            return null;
        }

        @org.jetbrains.annotations.Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
            return null;
        }

        @Override public Stream<BlockPos> getLights() {
            return null;
        }

        @Override public TickList<Block> getBlockTicks() {
            return null;
        }

        @Override public TickList<Fluid> getLiquidTicks() {
            return null;
        }

        @Override public UpgradeData getUpgradeData() {
            return null;
        }

        @Override public void setInhabitedTime(long inhabitedTime) {

        }

        @Override public long getInhabitedTime() {
            return 0;
        }

        @Override public boolean isLightCorrect() {
            return false;
        }

        @Override public void setLightCorrect(boolean lightOn) {

        }

        @org.jetbrains.annotations.Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return null;
        }

        @Override public FluidState getFluidState(BlockPos pos) {
            return null;
        }

        @Override public int getHeight() {
            return 31;
        }

        @Override public int getMinBuildHeight() {
            return 0;
        }

        @org.jetbrains.annotations.Nullable @Override public StructureStart<?> getStartForFeature(StructureFeature<?> structure) {
            return null;
        }

        @Override public void setStartForFeature(StructureFeature<?> structure, StructureStart<?> start) {

        }

        @Override public LongSet getReferencesForFeature(StructureFeature<?> structure) {
            return null;
        }

        @Override public void addReferenceForFeature(StructureFeature<?> structure, long reference) {

        }

        @Override public Map<StructureFeature<?>, LongSet> getAllReferences() {
            return null;
        }

        @Override public void setAllReferences(Map<StructureFeature<?>, LongSet> structureReferences) {

        }
    };

    public DummyHeightmap(Types types) {
        super(DUMMY_CHUNK_ACCESS, types);
    }


    @Override public boolean update(int x, int y, int z, BlockState state) {
        return false;
    }

    @Override public int getFirstAvailable(int x, int z) {
        return 0;
    }

    @Override public void setRawData(ChunkAccess clv, Types a, long[] heightmap) {
    }

    @Override public long[] getRawData() {
        return new long[0];
    }
}
