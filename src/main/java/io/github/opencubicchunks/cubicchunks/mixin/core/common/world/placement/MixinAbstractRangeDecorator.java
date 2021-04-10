package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.placement;

import java.util.Random;

import net.minecraft.world.level.levelgen.feature.configurations.RangeDecoratorConfiguration;
import net.minecraft.world.level.levelgen.placement.AbstractRangeDecorator;
import net.minecraft.world.level.levelgen.placement.DecorationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//TODO: Handle this when heightproviders decorators(ores) are finalized
@Mixin(value = AbstractRangeDecorator.class)
public abstract class MixinAbstractRangeDecorator {

    @Shadow protected abstract int y(Random random, int i, int j);

    @Inject(
        method = "y(Lnet/minecraft/world/level/levelgen/placement/DecorationContext;Ljava/util/Random;"
            + "Lnet/minecraft/world/level/levelgen/feature/configurations/RangeDecoratorConfiguration;I)I",
        at = @At("HEAD"), cancellable = true)
    private void shutupLogger(DecorationContext decorationContext, Random random, RangeDecoratorConfiguration biasedRangeDecoratorConfiguration, int i,
                              CallbackInfoReturnable<Integer> cir) {
        int j = biasedRangeDecoratorConfiguration.bottomInclusive().resolveY(decorationContext);
        int k = biasedRangeDecoratorConfiguration.topInclusive().resolveY(decorationContext);
        if (j >= k) {
            cir.setReturnValue(j);
        } else {
            cir.setReturnValue(this.y(random, j, k));
        }
    }
}
