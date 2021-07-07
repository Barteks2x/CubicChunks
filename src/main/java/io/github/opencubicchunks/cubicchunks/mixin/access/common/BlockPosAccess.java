package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockPos.class)
public interface BlockPosAccess {

    @Accessor("PACKED_X_LENGTH")
    static int getPackedXLength() {
        throw new Error(("Mixin did not apply"));
    }

    @Accessor("PACKED_X_LENGTH")
    static void setPackedXLength(int packedXLength) {
        throw new Error(("Mixin did not apply"));
    }
}
