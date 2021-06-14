package io.github.opencubicchunks.cubicchunks.chunk.cube;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ProtoChunkAccess;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeProtoTickList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoTickList;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

@SuppressWarnings("deprecation")
public class CubePrimerWrapper extends CubePrimer {

    private final BigCube wrappedCube;

    public CubePrimerWrapper(BigCube cubeIn) {
        super(cubeIn.getCubePos(), UpgradeData.EMPTY, cubeIn);
        this.wrappedCube = cubeIn;
    }

    public BigCube getCube() {
        return this.wrappedCube;
    }

    @Deprecated @Override public ChunkPos getPos() {
        throw new UnsupportedOperationException("This function should never be called!");
    }

    @Override public CubePos getCubePos() {
        return this.wrappedCube.getCubePos();
    }

    @Deprecated @Override public LevelChunkSection[] getSections() {
        return getCubeSections();
    }

    @Override public LevelChunkSection[] getCubeSections() {
        return wrappedCube.getCubeSections();
    }

    //STATUS
    @Deprecated @Override public ChunkStatus getStatus() {
        return this.wrappedCube.getStatus();
    }

    //BLOCK
    @Override @Nullable public BlockState setBlock(BlockPos pos, BlockState state, boolean isMoving) {
        return null;
    }

    @Override public BlockState getBlockState(int x, int y, int z) {
        return this.wrappedCube.getBlockState(x, y, z);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return this.wrappedCube.getFluidState(pos);
    }

    //ENTITY
    @Deprecated @Override public void addEntity(Entity entityIn) {
    }

    @Override public void addCubeEntity(Entity entityIn) {
    }

    //TILE ENTITY
    @Deprecated @Override public void setBlockEntityNbt(CompoundTag nbt) {
    }

    @Override public void setCubeBlockEntity(CompoundTag nbt) {
    }

    @Deprecated @Override public void removeBlockEntity(BlockPos pos) {
    }

    @Override public void removeCubeBlockEntity(BlockPos pos) {
    }

    @Deprecated @Override public void setBlockEntity(BlockEntity tileEntity) {
    }

    @Override public void setCubeBlockEntity(BlockEntity tileEntityIn) {
    }

    @Override @Nullable public BlockEntity getBlockEntity(BlockPos pos) {
        return this.wrappedCube.getBlockEntity(pos);
    }

    @Deprecated @Override @Nullable public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        return this.getCubeBlockEntityNbtForSaving(pos);
    }

    @Override @Nullable public CompoundTag getCubeBlockEntityNbtForSaving(BlockPos pos) {
        return this.wrappedCube.getCubeBlockEntityNbtForSaving(pos);
    }

    @Deprecated @Override @Nullable public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.getCubeDeferredTileEntity(pos);
    }

    @Override @Nullable public CompoundTag getCubeDeferredTileEntity(BlockPos pos) {
        return this.wrappedCube.getCubeDeferredTileEntity(pos);
    }

    //LIGHTING
    @Override public void setLightCorrect(boolean lightOn) {
        this.wrappedCube.setLightCorrect(lightOn);
    }

    @Override public void setCubeLight(boolean lightCorrectIn) {
        this.wrappedCube.setCubeLight(lightCorrectIn);
    }

    @Override public boolean isLightCorrect() {
        return this.wrappedCube.isLightCorrect();
    }

    @Override public boolean hasCubeLight() {
        return this.wrappedCube.hasCubeLight();
    }

    @Override public int getMaxLightLevel() {
        return this.wrappedCube.getMaxLightLevel();
    }

    @Override public Stream<BlockPos> getLights() {
        return this.wrappedCube.getLights();
    }

    @Override public List<BlockPos> getLightsRaw() {
        return ((ProtoChunkAccess) this.wrappedCube).getLights();
    }

    //MISC
    @Deprecated @Override public void setUnsaved(boolean modified) {

    }

    @Override public void setDirty(boolean modified) {

    }

    @Deprecated @Override public boolean isUnsaved() {
        return false;
    }

    @Override public boolean isDirty() {
        return false;
    }

    @Override public boolean isEmptyCube() {
        return this.wrappedCube.isEmptyCube();
    }

    @Override public ChunkBiomeContainer getBiomes() {
        return this.wrappedCube.getBiomes();
    }

    @Override public void setHeightmap(Heightmap.Types type, long[] data) {
    }

    private Heightmap.Types fixType(Heightmap.Types type) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG) {
            return Heightmap.Types.WORLD_SURFACE;
        } else {
            return type == Heightmap.Types.OCEAN_FLOOR_WG ? Heightmap.Types.OCEAN_FLOOR : type;
        }
    }

    @Override public int getHeight(Heightmap.Types types, int x, int z) {
        return this.wrappedCube.getHeight(this.fixType(types), x, z);
    }

    @Override public BlockPos getHeighestPosition(Heightmap.Types type) {
        return this.wrappedCube.getHeighestPosition(this.fixType(type));
    }

    // getStructureStart
    @Override @Nullable public StructureStart<?> getStartForFeature(StructureFeature<?> var1) {
        return this.wrappedCube.getStartForFeature(var1);
    }

    @Override public void setStartForFeature(StructureFeature<?> structureIn, StructureStart<?> structureStartIn) {
    }

    @Override public Map<StructureFeature<?>, StructureStart<?>> getAllCubeStructureStarts() {
        return this.wrappedCube.getAllCubeStructureStarts();
    }

    @Override
    public Map<StructureFeature<?>, StructureStart<?>> getAllStarts() {
        return this.getAllCubeStructureStarts();
    }

    @Override public void setAllStarts(Map<StructureFeature<?>, StructureStart<?>> structureStartsIn) {
    }

    @Override public LongSet getReferencesForFeature(StructureFeature<?> structureIn) {
        return this.wrappedCube.getReferencesForFeature(structureIn);
    }

    @Override public void addReferenceForFeature(StructureFeature<?> structure, long reference) {
    }

    @Override public Map<StructureFeature<?>, LongSet> getAllReferences() {
        return this.wrappedCube.getAllReferences();
    }

    @Override public void setAllReferences(Map<StructureFeature<?>, LongSet> p_201606_1_) {
    }

    @Override public void markPosForPostprocessing(BlockPos pos) {
    }


    public ProtoTickList<Block> getBlockTicks() {
        return new CubeProtoTickList<>((block) -> {
            return block == null || block.defaultBlockState().isAir();
        }, new ImposterChunkPos(this.getCubePos()), new CubeProtoTickList.CubeProtoTickListHeightAccess(this.getCubePos(), this));
    }

    public ProtoTickList<Fluid> getLiquidTicks() {
        return new CubeProtoTickList<>((fluid) -> {
            return fluid == null || fluid == Fluids.EMPTY;
        }, new ImposterChunkPos(this.getCubePos()), new CubeProtoTickList.CubeProtoTickListHeightAccess(this.getCubePos(), this));
    }

    @Override public BitSet getCarvingMask(GenerationStep.Carving type) {
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }

    @Override
    public BitSet getOrCreateCarvingMask(GenerationStep.Carving type) {
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }

    @Override
    public void setCarvingMask(GenerationStep.Carving type, BitSet mask) {
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }
}

