package io.github.opencubicchunks.cubicchunks.world.lighting;

import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;

public interface ISkyLightColumnChecker {
    /** all parameters are global coordinates */
    void checkSkyLightColumn(CubeMapGetter chunk, int x, int z, int oldHeight, int newHeight);
}
