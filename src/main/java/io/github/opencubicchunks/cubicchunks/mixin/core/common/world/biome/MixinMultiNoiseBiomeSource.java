package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.biome;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiNoiseBiomeSource.class)
public class MixinMultiNoiseBiomeSource {

    @Mutable @Shadow @Final private boolean useY;

    @SuppressWarnings("InvalidInjectorMethodSignature") @Inject(
        method = "<init>(JLjava/util/List;Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource$NoiseParameters;Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource$NoiseParameters;"
            + "Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource$NoiseParameters;Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource$NoiseParameters;Ljava/util/Optional;)V",
        at = @At("RETURN"))
    private void useY(long l, List<Pair<Biome.ClimateParameters, Supplier<Biome>>> list, @Coerce Object noiseParameters,
                      @Coerce Object noiseParameters2, @Coerce Object noiseParameters3, @Coerce Object noiseParameters4,
                      Optional<Pair<Registry<Biome>, MultiNoiseBiomeSource.Preset>> optional, CallbackInfo ci) {
        this.useY = true;
    }
}
