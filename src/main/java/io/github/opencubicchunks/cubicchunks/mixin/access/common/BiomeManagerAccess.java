package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeManager.class)
public interface BiomeManagerAccess {

    @Accessor("CHUNK_CENTER_QUART") static int getChunkCenterQuart() {
        throw new Error("Mixin did not apply");
    }
}