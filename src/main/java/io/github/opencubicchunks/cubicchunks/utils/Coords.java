/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2019 OpenCubicChunks
 *  Copyright (c) 2015-2019 contributors
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

package io.github.opencubicchunks.cubicchunks.utils;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * A class that contains helper-methods for many CubicChunks related things.
 * <p>
 * General Notes:
 * <ul>
 * <li>If a parameter is called <b>val</b> such as in the method {@link Coords#blockToLocal} it refers to a single dimension of that coordinate (x, y or z).</li>
 * <li>If a parameter is called <b>pos</b> and is of type <b>long</b> it refers to the entire coordinate compressed into a long. For example {@link SectionPos#asLong()}</li>
 * </ul>
 */
public class Coords {

    public static final int NO_HEIGHT = Integer.MIN_VALUE + 32;

    private static final int LOG2_BLOCK_SIZE = MathUtil.log2(IBigCube.DIAMETER_IN_BLOCKS);

    private static final int BLOCK_SIZE_MINUS_1 = IBigCube.DIAMETER_IN_BLOCKS - 1;
    private static final int BLOCK_SIZE_DIV_2 = IBigCube.DIAMETER_IN_BLOCKS / 2;
    private static final int BLOCK_SIZE_DIV_16 = IBigCube.DIAMETER_IN_BLOCKS / 16;
    private static final int BLOCK_SIZE_DIV_32 = IBigCube.DIAMETER_IN_BLOCKS / 32;

    private static final int POS_TO_INDEX_MASK = getPosToIndexMask();
    private static final int INDEX_TO_POS_MASK = POS_TO_INDEX_MASK >> 4;

    private static final int INDEX_TO_N_X = 0;
    private static final int INDEX_TO_N_Y = LOG2_BLOCK_SIZE - 4;
    private static final int INDEX_TO_N_Z = INDEX_TO_N_Y * 2;

    /**
     * <it><b>CC INTERNAL</b></it> | Mask used for converting BlockPos to ChunkSection index within a {@link BigCube}.
     */
    private static int getPosToIndexMask() {
        int mask = 0;
        for (int i = IBigCube.DIAMETER_IN_BLOCKS / 2; i >= 16; i /= 2) {
            mask += i;
        }
        return mask;
    }

    /**
     * Gets the middle block between two given blocks
     *
     * @return The central position as a BlockPos
     */
    public static BlockPos midPos(BlockPos p1, BlockPos p2) {
        //bitshifting each number and then adding the result - this rounds the number down and prevents overflow
        return new BlockPos((p1.getX() >> 1) + (p2.getX() >> 1) + (p1.getX() & p2.getX() & 1),
            (p1.getY() >> 1) + (p2.getY() >> 1) + (p1.getY() & p2.getY() & 1),
            (p1.getZ() >> 1) + (p2.getZ() >> 1) + (p1.getZ() & p2.getZ() & 1));
    }

    /**
     * Gets the offset of a {@link BlockPos} inside it's {@link BigCube}
     *
     * @param val A single value of the position
     *
     * @return The position relative to the {@link BigCube} this block is in
     */
    public static int blockToLocal(int val) {
        return val & (IBigCube.DIAMETER_IN_BLOCKS - 1);
    }

    /** See {@link Coords#blockToLocal} */
    public static int localX(BlockPos pos) {
        return blockToLocal(pos.getX());
    }

    /** See {@link Coords#blockToLocal} */
    public static int localY(BlockPos pos) {
        return blockToLocal(pos.getY());
    }

    /** See {@link Coords#blockToLocal} */
    public static int localZ(BlockPos pos) {
        return blockToLocal(pos.getZ());
    }

    /**
     * @param val A single dimension of the {@link BlockPos} (eg: {@link BlockPos#getY()})
     *
     * @return That coordinate as a CubePos
     */
    public static int blockToCube(int val) {
        return val >> LOG2_BLOCK_SIZE;
    }

    /** See {@link Coords#blockToCube(int)} */
    public static int blockToCube(double blockPos) {
        return blockToCube(Mth.floor(blockPos));
    }

    /**
     * @param blockVal A single dimension of the {@link BlockPos} (eg: {@link BlockPos#getY()})
     *
     * @return That coordinate as a CubePos
     */
    public static int blockCeilToCube(int blockVal) {
        return -((-blockVal) >> LOG2_BLOCK_SIZE);
    }

    /**
     * @param cubeVal Single dimension of a {@link CubePos}
     * @param localVal Offset of the block from the cube
     *
     * @return Sum of cubeVal as {@link BlockPos} and localVal
     */
    public static int localToBlock(int cubeVal, int localVal) {
        return cubeToMinBlock(cubeVal) + localVal;
    }

    /**
     * @param cubeVal A single dimension of a {@link CubePos}
     *
     * @return The minimum {@link BlockPos} inside that {@link BigCube}
     */
    public static int cubeToMinBlock(int cubeVal) {
        return cubeVal << LOG2_BLOCK_SIZE;
    }

    /**
     * @param cubeVal A single dimension of a {@link CubePos}
     *
     * @return The maximum {@link BlockPos} inside that {@link BigCube}
     */
    public static int cubeToMaxBlock(int cubeVal) {
        return cubeToMinBlock(cubeVal) + BLOCK_SIZE_MINUS_1;
    }

    /**
     * @param entity An entity
     *
     * @return The {@link CubePos} x of the entity
     */
    public static int getCubeXForEntity(Entity entity) {
        return blockToCube(Mth.floor(entity.getX()));
    }

    /**
     * @param entity An entity
     *
     * @return The {@link CubePos} y of the entity
     */
    public static int getCubeYForEntity(Entity entity) {
        // the entity is in the cube it's inside, not the cube it's standing on
        return blockToCube(Mth.floor(entity.getY()));
    }

    /**
     * @param entity An entity
     *
     * @return The {@link CubePos} z of the entity
     */
    public static int getCubeZForEntity(Entity entity) {
        return blockToCube(Mth.floor(entity.getZ()));
    }

    public static int cubeToCenterBlock(int cubeVal) {
        return localToBlock(cubeVal, BLOCK_SIZE_DIV_2);
    }

    /**
     * @param blockXVal X position
     * @param blockYVal Y position
     * @param blockZVal Z position
     *
     * @return The {@link ChunkSection} index inside the {@link BigCube} the position specified falls within
     *     <p>
     *     This uses the internal methods such as {@link Coords#blockToIndex16} to allow the JVM to optimise out the variable bit shifts that would occur otherwise
     */
    public static int blockToIndex(int blockXVal, int blockYVal, int blockZVal) {
        //EXAMPLE:
        //        Given x pos 33 = 0x21 = 0b0100001
        //        1100000
        //        0b0100001 & 0b1100000 = 0b0100000
        //        0b0100000 >> 4 = 0b10 = 0x2 = 2

        //        Given y pos 532 = 0x214 = 0b1000010100
        //        0b0001100000
        //        0b0001100000
        //        0b1000010100 & 0b0001100000 = 0b0
        //        0b0 >> 4 = 0b0 = 0x0 = 0

        //        Given z pos -921 = -0x399 = 0b1110011001
        //        0b0001100000
        //        0b0001100000
        //        0b1000010100 & 0b0001100000 = 0b0
        //        0b0 >> 4 = 0b0 = 0x0 = 0

        //        mask needs to be every power of 2 below IBigCube.BLOCK_SIZE that's > 16

        if (IBigCube.DIAMETER_IN_SECTIONS == 1) {
            return blockToIndex16(blockXVal, blockYVal, blockZVal);
        } else if (IBigCube.DIAMETER_IN_SECTIONS == 2) {
            return blockToIndex32(blockXVal, blockYVal, blockZVal);
        } else if (IBigCube.DIAMETER_IN_SECTIONS == 4) {
            return blockToIndex64(blockXVal, blockYVal, blockZVal);
        } else if (IBigCube.DIAMETER_IN_SECTIONS == 8) {
            return blockToIndex128(blockXVal, blockYVal, blockZVal);
        }
        throw new UnsupportedOperationException("Unsupported cube size " + IBigCube.DIAMETER_IN_SECTIONS);
    }
    public static int blockToIndex(BlockPos pos) {
        return blockToIndex(pos.getX(), pos.getY(), pos.getZ());
    }

    /** See {@link Coords#blockToIndex(int, int, int)} */
    private static int blockToIndex16(int blockXVal, int blockYVal, int blockZVal) {
        return 0;
    }

    /** See {@link Coords#blockToIndex(int, int, int)} */
    private static int blockToIndex32(int blockXVal, int blockYVal, int blockZVal) {
        //1 bit
        final int mask = POS_TO_INDEX_MASK;
        return (blockXVal & mask) >> 4 | (blockYVal & mask) >> 3 | (blockZVal & mask) >> 2;
    }

    /** See {@link Coords#blockToIndex(int, int, int)} */
    @SuppressWarnings("PointlessBitwiseExpression")
    private static int blockToIndex64(int blockXVal, int blockYVal, int blockZVal) {
        //2 bit
        //1011101010001, 1010101011100, 1101011101010
        final int mask = POS_TO_INDEX_MASK;
        return (blockXVal & mask) >> 4 | (blockYVal & mask) >> 2 | (blockZVal & mask) >> 0;
    }

    /** See {@link Coords#blockToIndex(int, int, int)} */
    private static int blockToIndex128(int blockXVal, int blockYVal, int blockZVal) {
        //3 bit
        //1011101010001, 1010101011100, 1101011101010
        final int mask = POS_TO_INDEX_MASK;
        return (blockXVal & mask) >> 4 | (blockYVal & mask) >> 1 | (blockZVal & mask) << 2;
    }

    /**
     * @param idx Index of the {@link ChunkSection} within it's {@link BigCube}
     *
     * @return The X offset (as a  {@link SectionPos}) from it's {@link CubePos} (as a  {@link SectionPos})
     */
    public static int indexToX(int idx) {
        return idx >> INDEX_TO_N_X & INDEX_TO_POS_MASK;
    }

    /**
     * @param idx Index of the {@link ChunkSection} within it's {@link BigCube}
     *
     * @return The Y offset (as a  {@link SectionPos}) from it's {@link CubePos} (as a  {@link SectionPos})
     */
    public static int indexToY(int idx) {
        return idx >> INDEX_TO_N_Y & INDEX_TO_POS_MASK;
    }

    /**
     * @param idx Index of the {@link ChunkSection} within it's {@link BigCube}
     *
     * @return The Z offset (as a  {@link SectionPos}) from it's {@link CubePos} (as a  {@link SectionPos})
     */
    public static int indexToZ(int idx) {
        return idx >> INDEX_TO_N_Z & INDEX_TO_POS_MASK;
    }

    /**
     * @param val Single dimension of a {@link SectionPos}
     *
     * @return That {@link SectionPos} dimension as a single dimension of a {@link CubePos}
     */
    public static int sectionToCube(int val) {
        return val >> (LOG2_BLOCK_SIZE - 4);
    }

    /**
     * @param sectionX A section X
     * @param sectionY A section Y
     * @param sectionZ A section Z
     *
     * @return The index of the {@link ChunkSection} that the specified {@link SectionPos} describes inside it's {@link BigCube}
     */
    public static int sectionToIndex(int sectionX, int sectionY, int sectionZ) {
        return blockToIndex(sectionX << 4, sectionY << 4, sectionZ << 4);
    }

    public static int indexToSectionX(int idx) {
        return indexToX(idx << 4);
    }

    public static int indexToSectionY(int idx) {
        return indexToY(idx << 4);
    }

    public static int indexToSectionZ(int idx) {
        return indexToZ(idx << 4);
    }

    /**
     * @param cubeVal A single dimension of the {@link CubePos}
     * @param sectionOffset The {@link SectionPos} offset from the {@link CubePos} as a {@link SectionPos}. Suggest you use {@link Coords#indexToX}, {@link Coords#indexToY}, {@link
     *     Coords#indexToZ} to get this offset
     *
     * @return The cubeVal as a sectionVal
     */
    public static int cubeToSection(int cubeVal, int sectionOffset) {
        return cubeVal << (LOG2_BLOCK_SIZE - 4) | sectionOffset;
    }

    public static int sectionToCubeCeil(int viewDistance) {
        return MathUtil.ceilDiv(viewDistance, IBigCube.DIAMETER_IN_SECTIONS);
    }

    public static int sectionToCubeRenderDistance(int viewDistance) {
        return Math.max(3, sectionToCubeCeil(viewDistance));
    }

    /**
     * @param blockVal A single dimension of a {@link BlockPos}
     *
     * @return That blockVal as a sectionVal
     */
    public static int blockToSection(int blockVal) {
        return blockVal >> 4;
    }

    /**
     * @param sectionVal A single dimension of a {@link SectionPos}
     *
     * @return That sectionVal as a blockVal
     */
    public static int sectionToMinBlock(int sectionVal) {
        return sectionVal << 4;
    }

    public static int blockToSectionLocal(int pos) {
        return pos & 0xF;
    }

    /**
     * @param cubePos The {@link CubePos}
     * @param i The index of the {@link ChunkSection} inside the {@link CubePos}
     *
     * @return The {@link SectionPos} of the {@link ChunkSection} at index i
     */
    public static SectionPos sectionPosByIndex(CubePos cubePos, int i) {
        return SectionPos.of(cubeToSection(cubePos.getX(), indexToX(i)), cubeToSection(cubePos.getY(), indexToY(i)), cubeToSection(cubePos.getZ(),
            indexToZ(i)));
    }

    /**
     * @param cubePos The {@link CubePos}
     * @param i The index of the {@link ChunkSection} inside the {@link CubePos}
     *
     * @return The {@link ChunkPos} of the column containing the {@link ChunkSection} at index i
     */
    public static ChunkPos chunkPosByIndex(CubePos cubePos, int i) {
        return new ChunkPos(cubeToSection(cubePos.getX(), indexToX(i)), cubeToSection(cubePos.getZ(), indexToZ(i)));
    }

    public static int blockToCubeLocalSection(int x) {
        return (x >> 4) & (IBigCube.DIAMETER_IN_SECTIONS - 1);

    }

    public static int cubeLocalSection(int section) {
        return section & (IBigCube.DIAMETER_IN_SECTIONS - 1);
    }

    public static BlockPos sectionPosToMinBlockPos(SectionPos sectionPos) {
        return new BlockPos(sectionToMinBlock(sectionPos.getX()), sectionToMinBlock(sectionPos.getY()), sectionToMinBlock(sectionPos.getZ()));
    }
}