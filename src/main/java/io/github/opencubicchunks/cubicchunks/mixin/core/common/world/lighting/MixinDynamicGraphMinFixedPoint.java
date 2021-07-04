package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.lighting;

import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DynamicGraphMinFixedPoint.class)
public abstract class MixinDynamicGraphMinFixedPoint {
    @Shadow protected abstract void checkNeighbor(long sourceId, long targetId, int level, boolean decrease);

    @Shadow
    protected abstract void checkEdge(long sourceId, long id, int level, boolean decrease);
}
