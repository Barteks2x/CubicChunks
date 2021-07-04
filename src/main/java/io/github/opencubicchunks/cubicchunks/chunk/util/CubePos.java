package io.github.opencubicchunks.cubicchunks.chunk.util;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public class CubePos extends Vec3i {
    private CubePos(int xIn, int yIn, int zIn) {
        super(xIn, yIn, zIn);
    }

    // Used from ASM, do not change
    public CubePos(long cubePosIn) {
        this(extractX(cubePosIn), extractY(cubePosIn), extractZ(cubePosIn));
    }

    // Used from ASM, do not change
    public CubePos(BlockPos pos) {
        this(blockToCube(pos.getX()), blockToCube(pos.getY()), blockToCube(pos.getZ()));
    }

    public static CubePos of(int x, int y, int z) {
        return new CubePos(x, y, z);
    }

    public static long asLong(int x, int y, int z) {
        long i = 0L;
        i |= ((long) x & (1 << 21) - 1) << 43;
        i |= ((long) y & (1 << 22) - 1);
        i |= ((long) z & (1 << 21) - 1) << 22;
        return i;
    }

    public static long asLong(BlockPos pos) {
        return asLong(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
    }

    public long asLong() {
        long i = 0L;
        i |= ((long) this.getX() & (1 << 21) - 1) << 43;
        i |= ((long) this.getY() & (1 << 22) - 1);
        i |= ((long) this.getZ() & (1 << 21) - 1) << 22;
        return i;
    }

    public static long asChunkPosLong(long cubePosIn, int localX, int localZ) {
        return ChunkPos.asLong(Coords.cubeToSection(CubePos.extractX(cubePosIn), localX), Coords.cubeToSection(CubePos.extractZ(cubePosIn), localZ));
    }

    public ChunkPos asChunkPos() {
        return new ChunkPos(cubeToSection(this.getX(), 0), cubeToSection(this.getZ(), 0));
    }

    public ChunkPos asChunkPos(int dx, int dz) {
        return new ChunkPos(cubeToSection(this.getX(), dx), cubeToSection(this.getZ(), dz));
    }

    public static CubePos from(long cubePosIn) {
        return new CubePos(cubePosIn);
    }

    public static CubePos from(BlockPos blockPosIn) {
        return new CubePos(blockToCube(blockPosIn.getX()), blockToCube(blockPosIn.getY()), blockToCube(blockPosIn.getZ()));
    }

    public static CubePos from(ChunkPos position, int yPos) {
        return new CubePos(sectionToCube(position.x), yPos, sectionToCube(position.z));
    }

    public static CubePos from(SectionPos sectionPos) {
        return new CubePos(
            Coords.sectionToCube(sectionPos.getX()),
            Coords.sectionToCube(sectionPos.getY()),
            Coords.sectionToCube(sectionPos.getZ()));
    }

    public static CubePos from(Entity p_218157_0_) {
        return new CubePos(blockToCube(Mth.floor(p_218157_0_.getX())),
            blockToCube(Mth.floor(p_218157_0_.getY())),
            blockToCube(Mth.floor(p_218157_0_.getZ())));
    }


    public static int extractX(long packed) {
        return (int) (packed >> 43);
    }

    public static int extractY(long packed) {
        return (int) (packed << 42 >> 42);
    }

    public static int extractZ(long packed) {
        return (int) (packed << 21 >> 43);
    }

    public int minCubeX() {
        return Coords.cubeToMinBlock(getX());
    }

    public int minCubeY() {
        return Coords.cubeToMinBlock(getY());
    }

    public int minCubeZ() {
        return Coords.cubeToMinBlock(getZ());
    }

    public int maxCubeX() {
        return Coords.cubeToMaxBlock(getX());
    }

    public int maxCubeY() {
        return Coords.cubeToMaxBlock(getY());
    }

    public int maxCubeZ() {
        return Coords.cubeToMaxBlock(getZ());
    }


    public SectionPos asSectionPos() {
        return SectionPos.of(cubeToSection(this.getX(), 0), cubeToSection(this.getY(), 0), cubeToSection(this.getZ(), 0));
    }

    public BlockPos asBlockPos() {
        return new BlockPos(minCubeX(), minCubeY(), minCubeZ());
    }

    public BlockPos asBlockPos(int localX, int localY, int localZ) {
        return new BlockPos(minCubeX() + localX, minCubeY() + localY, minCubeZ() + localZ);
    }

    public int blockX(int localX) {
        return Coords.localToBlock(getX(), localX);
    }

    public int blockY(int localY) {
        return Coords.localToBlock(getY(), localY);
    }

    public int blockZ(int localZ) {
        return Coords.localToBlock(getZ(), localZ);
    }

    public int getRegionX() {
        return getX() >> 3;
    }

    public int getRegionY() {
        return getY() >> 3;
    }

    public int getRegionZ() {
        return getZ() >> 3;
    }

    public int getLocalRegionX() {
        return getX() & 15;
    }

    public int getLocalRegionY() {
        return getY() & 15;
    }

    public int getLocalRegionZ() {
        return getZ() & 15;
    }

    public static long sectionToCubeSectionLong(long sectionPosIn) {
        return CubePos.from(SectionPos.of(sectionPosIn)).asSectionPos().asLong();
    }
}