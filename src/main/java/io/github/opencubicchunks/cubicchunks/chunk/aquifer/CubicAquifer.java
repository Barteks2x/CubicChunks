package io.github.opencubicchunks.cubicchunks.chunk.aquifer;

import java.util.Arrays;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.BaseStoneSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Optimized implementation of the NoiseBasedAquifer implementation with additional patches to account for an unlimited height range.
 */
public final class CubicAquifer implements Aquifer {
    private static final double NOISE_MAX = transformBarrierNoise(1.5);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    private static final int X_RANGE = 10;
    private static final int Y_RANGE = 9;
    private static final int Z_RANGE = 10;
    private static final int X_SPACING = 16;
    private static final int Y_SPACING = 12;
    private static final int Z_SPACING = 16;

    private final NormalNoise barrierNoise;
    private final AquiferSourceSampler sourceSampler;

    private final int[] aquiferCache;
    private final long[] aquiferLocationCache;
    private boolean shouldScheduleFluidUpdate;

    private final int minGridX;
    private final int minGridY;
    private final int minGridZ;
    private final int minY;
    private final int sizeY;
    private final BlockState[] typeToBlock;
    private final int gridSizeX;
    private final int gridSizeZ;

    private final AquiferRandom random = new AquiferRandom();

    private double barrierNoiseCache;

    public CubicAquifer(
        ChunkPos chunkPos,
        NormalNoise barrierNoise,
        AquiferSourceSampler sourceSampler,
        int minYInput, BlockState waterState
    ) {
        this.barrierNoise = barrierNoise;
        this.sourceSampler = sourceSampler;

        this.minGridX = gridX(chunkPos.getMinBlockX()) - 1;
        this.minGridY = gridY(minYInput) - 1;
        this.minGridZ = gridZ(chunkPos.getMinBlockZ()) - 1;
        this.minY = minYInput;
        this.sizeY = IBigCube.DIAMETER_IN_BLOCKS;

        typeToBlock = new BlockState[] {
            waterState,
            Blocks.LAVA.defaultBlockState()
        };

        int maxGridX = gridX(chunkPos.getMaxBlockX()) + 1;
        int maxGridY = gridY(minYInput + sizeY) + 1;
        int maxGridZ = gridZ(chunkPos.getMaxBlockZ()) + 1;

        this.gridSizeX = maxGridX - this.minGridX + 1;
        this.gridSizeZ = maxGridZ - this.minGridZ + 1;
        int gridSizeY = maxGridY - this.minGridY + 1;

        int gridSize = this.gridSizeX * gridSizeY * this.gridSizeZ;
        this.aquiferCache = new int[gridSize];
        Arrays.fill(this.aquiferCache, AquiferSample.NONE);
        this.aquiferLocationCache = new long[gridSize];
        Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
    }

    private static double transformBarrierNoise(double noise) {
        return 1.0 + (noise + 0.05) / 4.0;
    }

    private int getIndex(int x, int y, int z) {
        int localX = x - this.minGridX;
        int localY = y - this.minGridY;
        int localZ = z - this.minGridZ;
        return (localY * this.gridSizeZ + localZ) * this.gridSizeX + localX;
    }

    @Override
    public BlockState computeState(BaseStoneSource stoneSource, int x, int y, int z, double density) {
        if (y >= (this.minY + this.sizeY + 1)) {
            return stoneSource.getBaseBlock(x, y, z);
        }

        // we never subtract anything from the world so we can't do anything if it is already solid here
        if (density > 0.0) {
            return this.stone(stoneSource, x, y, z);
        }

        if (this.sourceSampler.isLavaLevel(y)) {
            this.shouldScheduleFluidUpdate = false;
            return LAVA;
        }

        int gridX = Math.floorDiv(x - 5, X_SPACING);
        int gridY = Math.floorDiv(y + 1, Y_SPACING);
        int gridZ = Math.floorDiv(z - 5, Z_SPACING);

        int firstDistance2 = Integer.MAX_VALUE;
        int secondDistance2 = Integer.MAX_VALUE;
        int thirdDistance2 = Integer.MAX_VALUE;
        long firstSource = 0;
        long secondSource = 0;
        long thirdSource = 0;

        for (int offsetX = 0; offsetX <= 1; offsetX++) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetZ = 0; offsetZ <= 1; offsetZ++) {
                    long sourcePos = this.getAquiferSourceIn(gridX + offsetX, gridY + offsetY, gridZ + offsetZ);
                    int deltaX = BlockPos.getX(sourcePos) - x;
                    int deltaY = BlockPos.getY(sourcePos) - y;
                    int deltaZ = BlockPos.getZ(sourcePos) - z;

                    int distance2 = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                    if (distance2 <= firstDistance2) {
                        thirdSource = secondSource;
                        secondSource = firstSource;
                        firstSource = sourcePos;
                        thirdDistance2 = secondDistance2;
                        secondDistance2 = firstDistance2;
                        firstDistance2 = distance2;
                    } else if (distance2 <= secondDistance2) {
                        thirdSource = secondSource;
                        secondSource = sourcePos;
                        thirdDistance2 = secondDistance2;
                        secondDistance2 = distance2;
                    } else if (distance2 <= thirdDistance2) {
                        thirdSource = sourcePos;
                        thirdDistance2 = distance2;
                    }
                }
            }
        }

        int firstAquifer = this.getAquiferAt(firstSource);

        double firstToSecond = similarity(firstDistance2, secondDistance2);

        double barrierDensity = this.computeBarrierDensity(x, y, z, firstDistance2, secondDistance2, thirdDistance2, secondSource, thirdSource, firstAquifer, firstToSecond);
        return getBlockState(stoneSource, x, y, z, density, firstAquifer, firstToSecond, barrierDensity);
    }

    private BlockState stateOf(int status) {
        return this.typeToBlock[AquiferSample.typeOf(status)];
    }

    private BlockState getBlockState(BaseStoneSource stoneSource, int x, int y, int z, double density, int firstAquifer, double firstToSecond, double barrierDensity) {
        if (density + barrierDensity <= 0.0) {
            this.shouldScheduleFluidUpdate = firstToSecond > 0.0;
            if (y >= AquiferSample.levelOf(firstAquifer)) {
                return AIR;
            }
            return this.stateOf(firstAquifer);
        } else {
            return this.stone(stoneSource, x, y, z);
        }
    }

    @Override
    public boolean shouldScheduleFluidUpdate() {
        return this.shouldScheduleFluidUpdate;
    }

    private double computeBarrierDensity(
        int x, int y, int z,
        int firstDistance2, int secondDistance2, int thirdDistance2,
        long secondSource, long thirdSource,
        int firstAquifer,
        double firstToSecond
    ) {
        // create barrier between water and lava level
        if (this.sourceSampler.isLavaLevel(y - 1) && AquiferSample.levelOf(firstAquifer) >= y && AquiferSample.isWater(firstAquifer)) {
            return 1.0;
        }

        if (firstToSecond <= -1.0) {
            return 0.0;
        }

        double firstToThird = similarity(firstDistance2, thirdDistance2);
        double secondToThird = similarity(secondDistance2, thirdDistance2);

        int secondAquifer = this.getAquiferAt(secondSource);
        int thirdAquifer = this.getAquiferAt(thirdSource);

        this.barrierNoiseCache = Double.NaN;

        double firstToSecondPressure = this.getPressureLazyNoise(x, y, z, firstAquifer, secondAquifer);
        double firstToThirdPressure = this.getPressureLazyNoise(x, y, z, firstAquifer, thirdAquifer);
        double secondToThirdPressure = this.getPressureLazyNoise(x, y, z, secondAquifer, thirdAquifer);

        // early exit: we know density will = 0
        if (firstToSecondPressure <= 0.0 && firstToThirdPressure <= 0.0 && secondToThirdPressure <= 0.0) {
            return 0.0;
        }

        double firstToSecondFactor = Math.max(0.0, firstToSecond);
        double firstToThirdFactor = Math.max(0.0, firstToThird);
        double secondToThirdFactor = Math.max(0.0, secondToThird);

        double thirdPressure = Math.max(
            firstToThirdPressure * firstToThirdFactor,
            secondToThirdPressure * secondToThirdFactor
        );

        double pressure = Math.max(firstToSecondPressure, thirdPressure);
        if (pressure > 0.0) {
            return 2.0 * firstToSecondFactor * pressure;
        } else {
            return 0.0;
        }
    }

    private BlockState stone(BaseStoneSource stoneSource, int x, int y, int z) {
        this.shouldScheduleFluidUpdate = false;
        return stoneSource.getBaseBlock(x, y, z);
    }

    private long getAquiferSourceIn(int x, int y, int z) {
        long[] cache = this.aquiferLocationCache;
        int index = this.getIndex(x, y, z);
        long sourcePos = cache[index];

        if (sourcePos == Long.MAX_VALUE) {
            AquiferRandom aquiferRandom = this.random;
            aquiferRandom.setSeed(Mth.getSeed(x, y * 3, z) + 1L);

            sourcePos = BlockPos.asLong(
                x * X_SPACING + aquiferRandom.nextInt(X_RANGE),
                y * Y_SPACING + aquiferRandom.nextInt(Y_RANGE),
                z * Z_SPACING + aquiferRandom.nextInt(Z_RANGE)
            );
            cache[index] = sourcePos;
        }

        return sourcePos;
    }

    private double getPressureLazyNoise(int x, int y, int z, int first, int second) {
        int firstLevel = AquiferSample.levelOf(first);
        int secondLevel = AquiferSample.levelOf(second);
        if (y <= firstLevel && y <= secondLevel && AquiferSample.typeOf(first) != AquiferSample.typeOf(second)) {
            return 1.0;
        }

        double meanLevel = 0.5 * (firstLevel + secondLevel);
        double distanceFromMean = Math.abs(meanLevel - y - 0.5);

        double targetDistanceFromMean = 0.5 * Math.abs(firstLevel - secondLevel);

        // avoid sampling noise if we don't need it
        double noise = this.barrierNoiseCache;
        if (Double.isNaN(noise)) {
            // if with the noise extreme, we will still get a negative density, there is no need to sample noise
            if (targetDistanceFromMean * NOISE_MAX <= distanceFromMean) {
                return 0.0;
            }

            noise = transformBarrierNoise(this.barrierNoise.getValue(x, y, z));
            this.barrierNoiseCache = noise;
        }

        return (targetDistanceFromMean * noise) - distanceFromMean;
    }

    private static double similarity(int a, int b) {
        return 1.0 - Math.abs(b - a) / 25.0;
    }

    private int getAquiferAt(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int index = this.getIndex(gridX(x), gridY(y), gridZ(z));
        int aquifer = this.aquiferCache[index];
        if (aquifer == AquiferSample.NONE) {
            aquifer = this.sourceSampler.sample(x, y, z);
            this.aquiferCache[index] = aquifer;
        }
        return aquifer;
    }

    private static int gridX(int x) {
        return x >> 4;
    }

    private static int gridY(int y) {
        return Math.floorDiv(y, Y_SPACING);
    }

    private static int gridZ(int z) {
        return z >> 4;
    }

    public int getMinY() {
        return minY;
    }

    public int getSizeY() {
        return sizeY;
    }
}
