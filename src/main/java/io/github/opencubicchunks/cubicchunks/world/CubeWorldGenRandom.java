package io.github.opencubicchunks.cubicchunks.world;

import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;

public class CubeWorldGenRandom extends NonAtomicWorldgenRandom {
    public long setDecorationSeed(long worldSeed, int x, int y, int z) {
        this.setSeed(worldSeed);
        long sX = this.nextLong() | 1L;
        long sY = this.nextLong() | 1L;
        long sZ = this.nextLong() | 1L;
        long result = (long) x * sX + (long) y * sY + (long) z * sZ ^ worldSeed;
        this.setSeed(result);
        return result;
    }

    public long setLargeFeatureSeed(long worldSeed, int regionX, int regionY, int regionZ) {
        this.setSeed(worldSeed);
        long sx = this.nextLong();
        long sy = this.nextLong();
        long sz = this.nextLong();
        long seed = (long) regionX * sx ^ (long) regionY * sy ^ (long) regionZ * sz ^ worldSeed;
        this.setSeed(seed);
        return seed;
    }

    public long setLargeFeatureWithSalt(long worldSeed, int regionX, int regionY, int regionZ, int salt) {
        long l = (long) regionX * 341873128712L + regionY * 526183811341L + (long) regionZ * 132897987541L + worldSeed + (long) salt;
        this.setSeed(l);
        return l;
    }
}
