package io.github.opencubicchunks.cubicchunks.chunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CubeMap {
    private final ConcurrentHashMap.KeySetView<Integer, Boolean> loadedCubes = ConcurrentHashMap.newKeySet();

    public boolean isLoaded(int cubeY) {
        return loadedCubes.contains(cubeY);
    }

    public void markLoaded(int cubeY) {
        loadedCubes.add(cubeY);
    }

    public void markUnloaded(int cubeY) {
        loadedCubes.remove(cubeY);
    }

    public Set<Integer> getLoaded() {
        return loadedCubes;
    }
}
