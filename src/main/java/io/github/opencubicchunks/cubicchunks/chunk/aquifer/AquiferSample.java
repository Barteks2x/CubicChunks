package io.github.opencubicchunks.cubicchunks.chunk.aquifer;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class AquiferSample {
    // we leave an extra bit so we can represent null values
    public static final int NONE = Integer.MIN_VALUE;

    public static final int TYPE_BITS = 1;
    public static final int TYPE_MASK = (1 << TYPE_BITS) - 1;

    public static final int WATER = 0;
    public static final int LAVA = 1;

    private static final BlockState[] TYPE_TO_BLOCK = new BlockState[] {
        Blocks.WATER.defaultBlockState(),
        Blocks.LAVA.defaultBlockState()
    };

    public static int pack(int type, int level) {
        return type | level << TYPE_BITS;
    }

    public static int water(int level) {
        return pack(WATER, level);
    }

    public static int lava(int level) {
        return pack(LAVA, level);
    }

    public static int levelOf(int status) {
        return status >> TYPE_BITS;
    }

    public static int typeOf(int status) {
        return status & TYPE_MASK;
    }

    public static BlockState stateOf(int status) {
        return TYPE_TO_BLOCK[typeOf(status)];
    }

    public static boolean isWater(int status) {
        return typeOf(status) == WATER;
    }
}
