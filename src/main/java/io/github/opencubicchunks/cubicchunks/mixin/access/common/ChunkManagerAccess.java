package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkManager.class)
public interface ChunkManagerAccess {

    @Invoker ChunkHolder invokeGetUpdatingChunkIfPresent(long chunkPosIn);
    @Invoker boolean invokePromoteChunkMap();
    @Accessor int getViewDistance();
}