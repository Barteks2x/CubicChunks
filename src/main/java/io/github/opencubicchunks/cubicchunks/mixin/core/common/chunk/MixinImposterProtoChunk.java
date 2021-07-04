package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import io.github.opencubicchunks.cubicchunks.chunk.CubeMap;
import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
public class MixinImposterProtoChunk implements CubeMapGetter, LightHeightmapGetter {


    @Shadow @Final private LevelChunk wrapped;

    @Override public CubeMap getCubeMap() {
        return ((CubeMapGetter) this.wrapped).getCubeMap();
    }

    @Override public Heightmap getLightHeightmap() {
        return ((LightHeightmapGetter) this.wrapped).getLightHeightmap();
    }
}
