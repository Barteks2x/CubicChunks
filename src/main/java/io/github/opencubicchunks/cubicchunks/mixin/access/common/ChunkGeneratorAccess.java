package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccess {

    @Invoker Codec<? extends ChunkGenerator> invokeCodec();
}
