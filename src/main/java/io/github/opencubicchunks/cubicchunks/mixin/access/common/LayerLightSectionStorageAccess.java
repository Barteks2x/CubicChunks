package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageAccess {

    @Invoker("enableLightSources") void invokeSetColumnEnabled(long seed, boolean enable);

    @Invoker boolean invokeStoringLightForSection(long sectionPos);

    @Invoker void invokeRunAllUpdates();

    @Accessor LightChunkGetter getChunkSource();
}