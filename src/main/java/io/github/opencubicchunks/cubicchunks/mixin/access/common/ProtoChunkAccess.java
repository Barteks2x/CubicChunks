package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProtoChunk.class)
public interface ProtoChunkAccess {

    @Accessor LevelHeightAccessor getLevelHeightAccessor();

    @Accessor Map<Heightmap.Types, Heightmap> getHeightmaps();

    @Accessor List<BlockPos> getLights();
}
