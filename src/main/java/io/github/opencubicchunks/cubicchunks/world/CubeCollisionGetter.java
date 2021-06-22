package io.github.opencubicchunks.cubicchunks.world;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import org.jetbrains.annotations.Nullable;

public interface CubeCollisionGetter extends CollisionGetter {

    @Nullable
    BlockGetter getCubeForCollisions(int cubeX, int cubeY, int cubeZ);
}
