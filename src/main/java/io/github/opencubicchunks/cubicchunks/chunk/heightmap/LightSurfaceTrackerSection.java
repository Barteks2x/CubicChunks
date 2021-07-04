package io.github.opencubicchunks.cubicchunks.chunk.heightmap;

import java.util.Arrays;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LightSurfaceTrackerSection extends SurfaceTrackerSection {
    public LightSurfaceTrackerSection() {
        this(MAX_SCALE, 0, null);
    }

    public LightSurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent) {
        // type shouldn't actually matter here
        super(scale, scaledY, parent, Heightmap.Types.WORLD_SURFACE);
    }

    public LightSurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent, IBigCube cube) {
        super(scale, scaledY, parent, cube, Heightmap.Types.WORLD_SURFACE);
    }

    private LightSurfaceTrackerSection getRoot() {
        SurfaceTrackerSection section = this;
        while (section.parent != null) {
            section = section.parent;
        }
        return (LightSurfaceTrackerSection) section;
    }

    @Nullable
    @Override
    protected SurfaceTrackerSection loadNode(int newScaledY, int sectionScale, IBigCube newCube, boolean create) {
        // TODO: loading from disk
        if (!create) {
            return null;
        }
        if (sectionScale == 0) {
            return new LightSurfaceTrackerSection(sectionScale, newScaledY, this, newCube);
        }
        return new LightSurfaceTrackerSection(sectionScale, newScaledY, this);
    }

    @Nullable
    private LightSurfaceTrackerSection getSectionAbove() {
        if (scale != 0) {
            throw new IllegalStateException("Attempted to get section above for a non-zero scale section");
        }
        // TODO this can be optimized - don't need to go to the root every time, just the lowest node that is a parent of both this node and the node above.
        return (LightSurfaceTrackerSection) this.getRoot().getCubeNode(scaledY + 1);
    }

    @Override
    public int getHeight(int x, int z) {
        int idx = index(x, z);
        if (!isDirty(idx)) {
            int relativeY = heights.get(idx);
            return relToAbsY(relativeY, scaledY, scale);
        }

        synchronized (this) {
            int maxY = Integer.MIN_VALUE;
            if (scale == 0) {
                IBigCube cube = (IBigCube) cubeOrNodes;
                CubePos cubePos = cube.getCubePos();

                LightSurfaceTrackerSection sectionAbove = this.getSectionAbove();

                int dy = IBigCube.DIAMETER_IN_BLOCKS - 1;

                // TODO unknown behavior for occlusion on a loading boundary (i.e. sectionAbove == null)
                BlockState above = sectionAbove == null ? Blocks.AIR.defaultBlockState() : ((IBigCube) sectionAbove.cubeOrNodes).getBlockState(x, 0, z);
                BlockState state = cube.getBlockState(x, dy, z);

                // note that this BlockPos relies on `cubePos.blockY` returning correct results when the local coord is not inside the cube
                VoxelShape voxelShapeAbove = sectionAbove == null
                        ? Shapes.empty()
                        : this.getShape(above, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy + 1), cubePos.blockZ(z)), Direction.DOWN);
                VoxelShape voxelShape = this.getShape(state, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy), cubePos.blockZ(z)), Direction.UP);

                while (dy >= 0) {
                    int lightBlock = state.getLightBlock(cube, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy), cubePos.blockZ(z)));
                    if (lightBlock > 0 || Shapes.faceShapeOccludes(voxelShapeAbove, voxelShape)) {
                        int minY = scaledY * IBigCube.DIAMETER_IN_BLOCKS;
                        maxY = minY + dy;
                        break;
                    }
                    dy--;
                    if (dy >= 0) {
                        above = state;
                        state = cube.getBlockState(x, dy, z);
                        voxelShapeAbove = this.getShape(above, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy + 1), cubePos.blockZ(z)), Direction.DOWN);
                        voxelShape = this.getShape(state, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy), cubePos.blockZ(z)), Direction.UP);
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

    /**
     * Used when upgrading CubePrimers to BigCubes; should never be used elsewhere.
     */
    public void upgradeCube(IBigCube cube) {
        if (this.scale != 0) {
            throw new IllegalStateException("Attempted to upgrade the cube on a non-zero scale section");
        }
        if (this.cubeOrNodes == null) {
            throw new IllegalStateException("Attempting to upgrade cube " + cube.getCubePos() + " for an unloaded surface tracker section");
        }
        this.cubeOrNodes = cube;

    }

    @Override
    public void loadCube(int sectionX, int sectionZ, IBigCube newCube, boolean markDirty) {
        if (this.cubeOrNodes == null) {
            throw new IllegalStateException("Attempting to load cube " + newCube.getCubePos() + " into an unloaded surface tracker section");
        }
        if (markDirty) {
            Arrays.fill(dirtyPositions, -1);
        }
        if (this.scale == 0) {
            // TODO merge loadHeightmapSection and loadLightHeightmapSection, and just use instanceof checks in the implementation to figure out if it's a light heightmap?
            newCube.setLightHeightmapSection(this, sectionX, sectionZ);
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

    protected VoxelShape getShape(BlockState blockState, BlockPos pos, Direction facing) {
        return blockState.canOcclude() && blockState.useShapeForLightOcclusion() ? blockState.getFaceOcclusionShape((IBigCube) this.cubeOrNodes, pos, facing) : Shapes.empty();
    }
}
