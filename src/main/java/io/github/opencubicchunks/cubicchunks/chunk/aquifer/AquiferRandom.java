package io.github.opencubicchunks.cubicchunks.chunk.aquifer;

import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;

public final class AquiferRandom extends NonAtomicWorldgenRandom {
    @Override
    public int nextInt(int bound) {
        // simplify method for aquifer's case to encourage inlining
        // namely, we remove the check for power of two bounds

        int bits;
        int result;
        int mod = bound - 1;

        do {
            long nextSeed = (this.seed * MULTIPLIER + ADDEND) & MASK;
            this.seed = nextSeed;
            bits = (int) (nextSeed >>> (48 - 31));

            result = bits % bound;
        } while (bits + mod < result);

        return result;
    }
}
