package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import net.minecraft.world.level.BlockGetter;

public interface ICubeLightProvider {

    //TODO: FIX THIS NAME AHHHHHHHHHHHHH
    @Nullable BlockGetter getCubeForLighting(int sectionX, int sectionY, int sectionZ);
}