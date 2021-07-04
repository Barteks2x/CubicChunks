package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.lighting;


import io.github.opencubicchunks.cubicchunks.chunk.CubeMap;
import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LayerLightSectionStorageAccess;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.lighting.ICubeLightProvider;
import io.github.opencubicchunks.cubicchunks.world.lighting.ICubicSkyLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.ISkyLightColumnChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightEngine.class)
public abstract class MixinSkyLightEngine extends MixinLayerLightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> implements ISkyLightColumnChecker,
    ICubicSkyLightEngine {
    @Shadow @Final private static Direction[] DIRECTIONS;

    /**
     * @author CursedFlames
     * @reason disable vanilla sky light logic
     */
    @Inject(method = "checkNode", at = @At("HEAD"), cancellable = true)
    protected void checkNode(long id, CallbackInfo ci) {
        if (!this.isCubic) {
            return;
        }
        ci.cancel();
        super.checkNode(id);
    }

    /** all parameters are global coordinates */
    @Override public void checkSkyLightColumn(CubeMapGetter chunk, int x, int z, int oldHeight, int newHeight) {
        ((LayerLightSectionStorageAccess) this.storage).invokeRunAllUpdates();
        CubeMap cubeMap = chunk.getCubeMap();
        int oldHeightCube = Coords.blockToCube(oldHeight - 1);
        int newHeightCube = Coords.blockToCube(newHeight);
        if (oldHeight > newHeight) {
            // not sure if this is necessary - also maybe it should be done inside the loop? not sure if threaded stuff can result in storage becoming out of date inside the loop
            ((LayerLightSectionStorageAccess) this.storage).invokeRunAllUpdates();

            // TODO cube iteration order might still be important here
            //      (int y = oldHeight-1; y >= newHeight; y--)
            for (int cubeY : cubeMap.getLoaded()) {
                if (oldHeightCube <= cubeY && cubeY <= newHeightCube) {
                    for (int dy = IBigCube.DIAMETER_IN_BLOCKS - 1; dy >= 0; dy--) {
                        int y = cubeY * IBigCube.DIAMETER_IN_BLOCKS + dy;

                        if (y >= oldHeight) {
                            continue;
                        }
                        if (y < newHeight) {
                            break;
                        }

                        long pos = new BlockPos(x, y, z).asLong();
                        if (((LayerLightSectionStorageAccess) this.storage).invokeStoringLightForSection(SectionPos.blockToSection(pos))) {
                            addEmissionAtPos(pos);
                        }
                    }
                }
            }
        } else {
            // TODO cube iteration order might still be important here
            //      (int y = oldHeight; y < newHeight; y++)
            for (int cubeY : cubeMap.getLoaded()) {
                if (oldHeightCube <= cubeY && cubeY <= newHeightCube) {
                    for (int dy = 0; dy < IBigCube.DIAMETER_IN_BLOCKS; dy++) {
                        int y = cubeY * IBigCube.DIAMETER_IN_BLOCKS + dy;

                        if (y < oldHeight) {
                            continue;
                        }
                        if (y >= newHeight) {
                            break;
                        }

                        long pos = new BlockPos(x, y, z).asLong();
                        // Don't need to check storing light for pos here, since it's already handled by checkNode
                        this.checkNode(pos);
                    }
                }
            }
        }
    }

    private void addEmissionAtPos(long pos) {
        this.checkEdge(Long.MAX_VALUE, pos, 0, true);
    }

    /**
     * @author CursedFlames
     * @reason Prevent getComputedLevel from ignoring skylight sources and decreasing light level - similar to BlockLightEngine's light source check
     */
    @Inject(method = "getComputedLevel(JJI)I",
        at = @At("HEAD"), cancellable = true)
    private void onGetComputedLevel(long id, long excludedId, int maxLevel, CallbackInfoReturnable<Integer> cir) {
        // TODO do we want this mixin on client side too?
        if (!this.isCubic /*|| this.chunkSource.getLevel() instanceof ClientLevel*/) {
            return;
        }
        BlockPos pos = BlockPos.of(id);

        BlockGetter cube = ((ICubeLightProvider) this.chunkSource).getCubeForLighting(
            Coords.blockToSection(pos.getX()), Coords.blockToSection(pos.getY()), Coords.blockToSection(pos.getZ()));
        if (cube == null || !((IBigCube) cube).getStatus().isOrAfter(ChunkStatus.LIGHT)) {
            return;
        }

        BlockGetter chunk = this.chunkSource.getChunkForLighting(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));

        if (chunk == null) {
            //Client can have null chunks when trying to render an entity in a chunk that hasn't arrived yet (neither has the cube at that point)
            assert ((Level) this.chunkSource.getLevel()).isClientSide;
            return;
        }

        Heightmap heightmap = ((LightHeightmapGetter) chunk).getLightHeightmap();

        int height = heightmap.getFirstAvailable(pos.getX() & 0xF, pos.getZ() & 0xF);
        if (height <= pos.getY()) {
            cir.setReturnValue(0);
        }
    }

    @Override
    public void doSkyLightForCube(IBigCube cube) {
        CubePos cubePos = cube.getCubePos();
        ChunkPos chunkPos = cubePos.asChunkPos();
        int minY = cubePos.minCubeY();
        int maxY = cubePos.maxCubeY();
        for (int sectionX = 0; sectionX < IBigCube.DIAMETER_IN_SECTIONS; sectionX++) {
            for (int sectionZ = 0; sectionZ < IBigCube.DIAMETER_IN_SECTIONS; sectionZ++) {

                BlockGetter chunk = this.chunkSource.getChunkForLighting(chunkPos.x + sectionX, chunkPos.z + sectionZ);

                // the load order guarantees the chunk being present
                assert (chunk != null);

                CubeMap cubeMap = ((CubeMapGetter) chunk).getCubeMap();
                if (!cubeMap.isLoaded(cubePos.getY())) {
                    // This is probably only happening because we don't have load order fixed yet
                    System.out.println(cube.getCubePos() + " : Cube not in cubemap during sky lighting");
                }

                Heightmap heightmap = ((LightHeightmapGetter) chunk).getLightHeightmap();
                if (heightmap == null) {
                    System.out.println("heightmap null");
                    return;
                }
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int height = heightmap.getFirstAvailable(x, z);
                        if (height <= maxY) {
                            height = Math.max(height, minY);
                            for (int y = maxY; y >= height; y--) {
                                long pos = new BlockPos((chunkPos.x + sectionX) * 16 + x, y, (chunkPos.z + sectionZ) * 16 + z).asLong();
                                // Not sure if this is necessary
                                ((LayerLightSectionStorageAccess) this.storage).invokeRunAllUpdates();

                                if (((LayerLightSectionStorageAccess) this.storage).invokeStoringLightForSection(SectionPos.blockToSection(pos))) {
                                    addEmissionAtPos(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @author CursedFlames
     * @reason disable vanilla sky light logic
     */
    @Inject(method = "checkNeighborsAfterUpdate", at = @At("HEAD"), cancellable = true)
    private void onCheckNeighborsAfterUpdate(long blockPos, int level, boolean decrease, CallbackInfo ci) {
        if (!isCubic) {
            return;
        }
        ci.cancel();

        long sectionPos = SectionPos.blockToSection(blockPos);

        for (Direction direction : DIRECTIONS) {
            long offsetBlockPos = BlockPos.offset(blockPos, direction);
            long offsetSectionPos = SectionPos.blockToSection(offsetBlockPos);
            // Check all neighbors that are storing light
            if (sectionPos == offsetSectionPos || (((LayerLightSectionStorageAccess) this.storage)).invokeStoringLightForSection(offsetSectionPos)) {
                this.checkNeighbor(blockPos, offsetBlockPos, level, decrease);
            }
        }
    }

//    /**
//     * @author CursedFlames
//     * @reason prevent infinite downwards skylight propagation
//     */
//    @Inject(method = "computeLevelFromNeighbor", at = @At("RETURN"), cancellable = true)
//    private void onComputeLevelFromNeighbor(long sourceId, long targetId, int level, CallbackInfoReturnable<Integer> cir) {
//        if (isCubic && cir.getReturnValue() == 0) {
//            cir.setReturnValue(1);
//        }
//    }
}
