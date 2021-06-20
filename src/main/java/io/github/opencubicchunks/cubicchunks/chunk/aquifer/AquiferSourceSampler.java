package io.github.opencubicchunks.cubicchunks.chunk.aquifer;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public interface AquiferSourceSampler {
    int sample(int x, int y, int z);

    default boolean isLavaLevel(int y) {
        return false;
    }

    final class Overworld implements AquiferSourceSampler {
        private final NormalNoise levelNoise;
        private final NormalNoise lavaNoise;
        private final NoiseGeneratorSettings noiseGeneratorSettings;

        public Overworld(NormalNoise levelNoise, NormalNoise lavaNoise, NoiseGeneratorSettings noiseGeneratorSettings) {
            this.levelNoise = levelNoise;
            this.lavaNoise = lavaNoise;
            this.noiseGeneratorSettings = noiseGeneratorSettings;
        }

        @Override
        public int sample(int x, int y, int z) {
            if (y > 30) {
                int seaLevel = this.noiseGeneratorSettings.seaLevel();
                return AquiferSample.water(seaLevel);
            }

            int gridY = Math.floorDiv(y, 40);

            double noiseX = x >> 6;
            double noiseY = gridY / 1.4;
            double noiseZ = z >> 6;

            double levelNoise = this.levelNoise.getValue(noiseX, noiseY, noiseZ) * 30.0 - 10.0;
            if (Math.abs(levelNoise) > 8.0) {
                levelNoise *= 4.0;
            }

            int gridMidY = gridY * 40 + 20;
            int level = gridMidY + Mth.floor(levelNoise);
            level = Math.min(56, level);

            boolean lava = Math.abs(this.lavaNoise.getValue(noiseX, noiseY, noiseZ)) > 0.22;
            return lava ? AquiferSample.lava(level) : AquiferSample.water(level);
        }

        @Override
        public boolean isLavaLevel(int y) {
            return y <= CubicChunks.MIN_SUPPORTED_HEIGHT + 12;
        }
    }

    final class Nether implements AquiferSourceSampler {
        private final NormalNoise levelNoise;

        public Nether(NormalNoise levelNoise) {
            this.levelNoise = levelNoise;
        }

        @Override
        public int sample(int x, int y, int z) {
            int gridY = Math.floorDiv(y, 40);

            double noiseX = x >> 6;
            double noiseY = gridY / 1.4;
            double noiseZ = z >> 6;

            double levelNoise = this.levelNoise.getValue(noiseX, noiseY, noiseZ) * 30.0 - 10.0;
            if (Math.abs(levelNoise) > 8.0) {
                levelNoise *= 4.0;
            }

            int gridMidY = gridY * 40 + 20;
            int level = gridMidY + Mth.floor(levelNoise);

            return AquiferSample.lava(level);
        }

        @Override
        public boolean isLavaLevel(int y) {
            return false;
        }
    }
}
