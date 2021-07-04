package io.github.opencubicchunks.cubicchunks.chunk.heightmap;

import java.util.Arrays;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceTrackerSection {
    // TODO: This currently covers y = -2^28 to 2^28 or so. One more would allow us to cover the entire integer block range
    public static final int MAX_SCALE = 6;
    /**
     * Number of bits needed to represent the children nodes (i.e. log2(NODE_COUNT)) This is also the number of bits that are added on each scale increase.
     */
    public static final int NODE_COUNT_BITS = 4;
    /** Number of children nodes */
    public static final int NODE_COUNT = 1 << NODE_COUNT_BITS;

    // Use width of 16 to match columns.
    public static final int WIDTH_BLOCKS = 16;

    private static final Heightmap.Types[] HEIGHTMAP_TYPES = Heightmap.Types.values();

    /** Number of bits needed to represent height (excluding null) at scale zero (i.e. log2(scale0 height)) */
    private static final int BASE_SIZE_BITS = IBigCube.SIZE_BITS;

    protected final BitStorage heights;
    protected final long[] dirtyPositions; // bitset has 100% memory usage overhead due to pointers and object headers
    protected SurfaceTrackerSection parent;
    protected Object cubeOrNodes;
    /**
     * Position of this section, within all sections of this size e.g. with 64-block sections, y=0-63 would be section 0, y=64-127 would be section 1, etc.
     */
    protected final int scaledY;
    protected final byte scale;
    private final byte heightmapType;

    public SurfaceTrackerSection(Heightmap.Types types) {
        this(MAX_SCALE, 0, null, types);
    }

    public SurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent, Heightmap.Types types) {
        this(scale, scaledY, parent, null, types);
    }

    public SurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent, IBigCube cube, Heightmap.Types types) {
//      super((ChunkAccess) cube, types);
        // +1 in bit size to make room for null values
        this.heights = new BitStorage(BASE_SIZE_BITS + 1 + scale * NODE_COUNT_BITS, WIDTH_BLOCKS * WIDTH_BLOCKS);
        this.dirtyPositions = new long[WIDTH_BLOCKS * WIDTH_BLOCKS / Long.SIZE];
        this.parent = parent;
        this.cubeOrNodes = scale == 0 ? cube : new SurfaceTrackerSection[NODE_COUNT];
        this.scaledY = scaledY;
        this.scale = (byte) scale;
        this.heightmapType = (byte) types.ordinal();
    }

    /**
     * Get the height for a given position. Recomputes the height if the column is marked dirty in this section.
     * x and z are global coordinates.
     */
    public int getHeight(int x, int z) {
        int idx = index(x, z);
        if (!isDirty(idx)) {
            int relativeY = heights.get(idx);
            return relToAbsY(relativeY, scaledY, scale);
        }

        synchronized(this) {
            int maxY = Integer.MIN_VALUE;
            if (scale == 0) {
                IBigCube cube = (IBigCube) cubeOrNodes;
                Predicate<BlockState> isOpaque = HEIGHTMAP_TYPES[this.heightmapType].isOpaque();
                for (int dy = IBigCube.DIAMETER_IN_BLOCKS - 1; dy >= 0; dy--) {
                    if (isOpaque.test(cube.getBlockState(x, dy, z))) {
                        int minY = scaledY * IBigCube.DIAMETER_IN_BLOCKS;
                        maxY = minY + dy;
                        break;
                    }
                }
            } else {
                SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
                for (int i = nodes.length - 1; i >= 0; i--) {
                    SurfaceTrackerSection node = nodes[i];
                    if (node == null) {
                        continue;
                    }
                    int y = node.getHeight(x, z);
                    if (y != Integer.MIN_VALUE) {
                        maxY = y;
                        break;
                    }
                }
            }
            heights.set(idx, absToRelY(maxY, scaledY, scale));
            clearDirty(idx);
            return maxY;
        }
    }

    protected void clearDirty(int idx) {
        dirtyPositions[idx >> 6] &= ~(1L << idx);
    }

    private void setDirty(int idx) {
        dirtyPositions[idx >> 6] |= 1L << idx;
    }

    protected boolean isDirty(int idx) {
        return (dirtyPositions[idx >> 6] & (1L << idx)) != 0;
    }

    public void onSetBlock(int x, int y, int z, BlockState state) {
        int index = index(x, z);
        if (isDirty(index)) {
            return;
        }

        y = Coords.localToBlock(scaledY, Coords.blockToLocal(y));
        int height = getHeight(x, z);
        if (y < height) {
            return;
        }

        boolean test = HEIGHTMAP_TYPES[heightmapType].isOpaque().test(state);
        if (y > height) {
            if (!test) {
                return;
            }

            if (parent != null) {
                parent.markDirty(x, z);
            }
            this.heights.set(index, absToRelY(y, scaledY, scale));
            return;
        }
        if (test) {
            markDirty(x, z);
        }
    }

    public void markDirty(int x, int z) {
        setDirty(index(x, z));
        if (parent != null) {
            parent.markDirty(x, z);
        }
    }

    public void unloadCube(IBigCube cube) {
        if (this.cubeOrNodes == null) {
            throw new IllegalStateException("Attempting to unload cube " + cube.getCubePos() + " from an unloaded surface tracker section");
        }
        // TODO: handle unloading with saving
        if (scale == 0) {
            this.cubeOrNodes = null;
        } else {
            boolean canUnload = true;
            for (SurfaceTrackerSection section : ((SurfaceTrackerSection[]) this.cubeOrNodes)) {
                if (section != null) {
                    canUnload = false;
                    break;
                }
            }
            if (canUnload && parent != null) {
                this.cubeOrNodes = null;
            }
        }
        if (this.parent == null) {
            return;
        }
        if (this.cubeOrNodes == null) {
            int idx = indexOfRawHeightNode(cube.getCubePos().getY(), parent.scale, parent.scaledY);
            ((SurfaceTrackerSection[]) this.parent.cubeOrNodes)[idx] = null;
        }
        this.parent.unloadCube(cube);
        if (this.cubeOrNodes == null) {
            this.parent = null;
        }
    }

    public void loadCube(int sectionX, int sectionZ, IBigCube newCube, boolean markDirty) {
        if (this.cubeOrNodes == null) {
            throw new IllegalStateException("Attempting to load cube " + newCube.getCubePos() + " into an unloaded surface tracker section");
        }
        if (markDirty) {
            Arrays.fill(dirtyPositions, -1);
        }
        if (this.scale == 0) {
            newCube.loadHeightmapSection(this, sectionX, sectionZ);
            return;
        }
        int idx = indexOfRawHeightNode(newCube.getCubePos().getY(), scale, scaledY);
        SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                continue;
            }
            int newScaledY = indexToScaledY(i, scale, scaledY);
            SurfaceTrackerSection newMap = loadNode(newScaledY, scale - 1, newCube, i == idx);
            nodes[i] = newMap;
        }
        assert nodes[idx] != null;
        nodes[idx].loadCube(sectionX, sectionZ, newCube, markDirty);
    }

    @Nullable
    public SurfaceTrackerSection getParent() {
        return parent;
    }

    public SurfaceTrackerSection getChild(int i) {
        SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
        return nodes[i];
    }

    public SurfaceTrackerSection getCubeNode(int y) {
        if (scale == 0) {
            if (y != scaledY) {
                throw new IllegalArgumentException("Invalid Y: " + y + ", expected " + scaledY);
            }
            return this;
        }
        int idx = indexOfRawHeightNode(y, scale, scaledY);
        SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
        SurfaceTrackerSection node = nodes[idx];
        if (node == null) {
            return null;
        }
        return node.getCubeNode(y);
    }

    public IBigCube getCube() {
        return (IBigCube) cubeOrNodes;
    }

    public Heightmap.Types getType() {
        return Heightmap.Types.values()[heightmapType];
    }

    @Nullable
    protected SurfaceTrackerSection loadNode(int newScaledY, int sectionScale, IBigCube newCube, boolean create) {
        // TODO: loading from disk
        if (!create) {
            return null;
        }
        if (sectionScale == 0) {
            return new SurfaceTrackerSection(sectionScale, newScaledY, this, newCube, HEIGHTMAP_TYPES[this.heightmapType]);
        }
        return new SurfaceTrackerSection(sectionScale, newScaledY, this, HEIGHTMAP_TYPES[this.heightmapType]);
    }

    /** Get position x/z index within a column, from global/local pos */
    protected int index(int x, int z) {
        return (z & 0xF) * WIDTH_BLOCKS + (x & 0xF);
    }

    @VisibleForTesting
    static int indexOfRawHeightNode(int y, int nodeScale, int nodeScaledY) {
        if (nodeScale == 0) {
            throw new UnsupportedOperationException("Why?");
        }
        if (nodeScale == MAX_SCALE) {
            return y < 0 ? 0 : 1;
        }
        int scaled = y >> ((nodeScale - 1) * NODE_COUNT_BITS);
        return scaled - (nodeScaledY << NODE_COUNT_BITS);
    }

    @VisibleForTesting
    static int indexToScaledY(int index, int nodeScale, int nodeScaledY) {
        if (nodeScale == 0) {
            throw new UnsupportedOperationException("Why?");
        }
        if (nodeScale == MAX_SCALE) {
            return index == 0 ? -1 : 0;
        }
        return (nodeScaledY << NODE_COUNT_BITS) + index;
    }

    /** Get the lowest cube y coordinate for a given scaledY and scale */
    @VisibleForTesting
    static int scaledYBottomY(int scaledY, int scale) {
        if (scale == MAX_SCALE) {
            return -(1 << ((scale - 1) * NODE_COUNT_BITS));
        }
        return scaledY << (scale * NODE_COUNT_BITS);
    }

    /** Get the world y coordinate for a given relativeY, scaledY and scale */
    @VisibleForTesting
    static int relToAbsY(int relativeY, int scaledY, int scale) {
        if (relativeY == 0) {
            return Integer.MIN_VALUE;
        }
        return relativeY - 1 + scaledYBottomY(scaledY, scale) * IBigCube.DIAMETER_IN_BLOCKS;
    }

    /** Get the relative y coordinate for a given absoluteY, scaledY and scale */
    @VisibleForTesting
    static int absToRelY(int absoluteY, int scaledY, int scale) {
        if (absoluteY == Integer.MIN_VALUE) {
            return 0;
        }
        return absoluteY + 1 - scaledYBottomY(scaledY, scale) * IBigCube.DIAMETER_IN_BLOCKS;
    }

    public void writeData(int mainX, int mainZ, BitStorage data, int minValue) {
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int y = getHeight(mainX + dx, mainZ + dz) + 1;
                if (y < minValue) {
                    y = minValue;
                }
                y -= minValue;
                data.set(dx + dz * 16, y);
            }
        }
    }
}
