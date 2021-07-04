package io.github.opencubicchunks.cubicchunks.chunk;

import io.github.opencubicchunks.cubicchunks.chunk.heightmap.ClientLightSurfaceTracker;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerWrapper;
import net.minecraft.world.level.levelgen.Heightmap;

public interface LightHeightmapGetter {
    Heightmap getLightHeightmap();

    // Do not override this
    default ClientLightSurfaceTracker getClientLightHeightmap() {
        Heightmap lightHeightmap = this.getLightHeightmap();
        if (!(lightHeightmap instanceof ClientLightSurfaceTracker)) {
            throw new IllegalStateException("Attempted to get client light heightmap on server");
        }
        return (ClientLightSurfaceTracker) lightHeightmap;
    }

    // Do not override this
    default LightSurfaceTrackerWrapper getServerLightHeightmap() {
        Heightmap lightHeightmap = this.getLightHeightmap();
        if (!(lightHeightmap instanceof LightSurfaceTrackerWrapper)) {
            throw new IllegalStateException("Attempted to get server light heightmap on client");
        }
        return (LightSurfaceTrackerWrapper) lightHeightmap;
    }
}
