package io.github.opencubicchunks.cubicchunks.mixin.core.common.progress;

import io.github.opencubicchunks.cubicchunks.chunk.ICubeStatusListener;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkProgressListener.class)
public interface MixinChunkProgressListener extends ICubeStatusListener {
}
