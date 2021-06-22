package io.github.opencubicchunks.cubicchunks.chunk.cube;

import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class EmptyCube extends BigCube {

    public EmptyCube(Level worldIn, CubePos cubePos) {
        super(worldIn, cubePos, null);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.VOID_AIR.defaultBlockState();
    }

    @Nullable
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        return null;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
    }

    public void markUnsaved() {
    }

    public boolean isEmpty() {
        return true;
    }

    public boolean isYSpaceEmpty(int lowerHeight, int upperHeight) {
        return true;
    }

    public ChunkHolder.FullChunkStatus getFullStatus() {
        return ChunkHolder.FullChunkStatus.BORDER;
    }

    @Override public LevelChunkSection[] getCubeSections() {
        throw new UnsupportedOperationException("This is empty cube!");
    }
}