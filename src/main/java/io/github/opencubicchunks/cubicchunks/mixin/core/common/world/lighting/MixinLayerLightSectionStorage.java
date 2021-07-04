package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.lighting;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.LevelBasedGraphAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.lighting.ISectionLightStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerLightSectionStorage.class)
public abstract class MixinLayerLightSectionStorage<M extends DataLayerStorageMap<M>> extends SectionTracker implements ISectionLightStorage {

    @Shadow @Final private static Direction[] DIRECTIONS;

    @Shadow @Final protected Long2ObjectMap<DataLayer> queuedSections;
    @Shadow @Final protected M updatingSectionData;
    @Shadow protected volatile boolean hasToRemove;
    @Shadow @Final protected LongSet changedSections;
    @Shadow @Final private LongSet toRemove;
    @Shadow @Final private LightChunkGetter chunkSource;

    private final LongSet cubesToRetain = new LongOpenHashSet();

    protected MixinLayerLightSectionStorage(int p_i50706_1_, int p_i50706_2_, int p_i50706_3_) {
        super(p_i50706_1_, p_i50706_2_, p_i50706_3_);
    }

    @Shadow protected abstract void clearQueuedSectionBlocks(LayerLightEngine<?, ?> engine, long sectionPosIn);

    @Shadow protected abstract void onNodeRemoved(long p_215523_1_);

    @Shadow protected abstract boolean storingLightForSection(long sectionPosIn);

    @Shadow protected abstract boolean hasInconsistencies();


    @Override
    public void retainCubeData(long cubeSectionPos, boolean retain) {
        if (retain) {
            this.cubesToRetain.add(cubeSectionPos);
        } else {
            this.cubesToRetain.remove(cubeSectionPos);
        }
    }

    /**
     * @author NotStirred
     * @reason entire method was chunk based
     */
    @Inject(method = "markNewInconsistencies", at = @At("HEAD"), cancellable = true)
    protected void markNewInconsistenciesForCube(LayerLightEngine<M, ?> engine, boolean updateSkyLight, boolean updateBlockLight, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.chunkSource.getLevel()).isCubic()) {
            return;
        }

        ci.cancel();


        if (this.hasInconsistencies() || !this.queuedSections.isEmpty()) {
            for (long noLightPos : this.toRemove) {
                this.clearQueuedSectionBlocks(engine, noLightPos);
                DataLayer nibblearray = this.queuedSections.remove(noLightPos);
                DataLayer nibblearray1 = this.updatingSectionData.removeLayer(noLightPos);
                // FIXME this commented out check is probably important, but also breaks client-side lighting
                //       should investigate why it breaks things instead of just disabling it.
//                if (this.cubesToRetain.contains(CubePos.sectionToCubeSectionLong(noLightPos))) {
                    if (nibblearray != null) {
                        this.queuedSections.put(noLightPos, nibblearray);
                    } else if (nibblearray1 != null) {
                        this.queuedSections.put(noLightPos, nibblearray1);
                    }
//                }
            }

            this.updatingSectionData.clearCache();

            for (long section : this.toRemove) {
                //TODO: implement this for CC
                this.onNodeRemoved(section);
            }

            this.toRemove.clear();
            this.hasToRemove = false;

            for (Long2ObjectMap.Entry<DataLayer> entry : this.queuedSections.long2ObjectEntrySet()) {
                long entryPos = entry.getLongKey();
                // FIXME this commented out check is also probably important, but this one breaks *server-side* lighting
                //       should investigate why it breaks things instead of just disabling it.
//                if (this.storingLightForSection(entryPos)) {
                    DataLayer nibblearray2 = entry.getValue();
                    if (this.updatingSectionData.getLayer(entryPos) != nibblearray2) {
                        this.clearQueuedSectionBlocks(engine, entryPos);
                        this.updatingSectionData.setLayer(entryPos, nibblearray2);
                        this.changedSections.add(entryPos);
                    }
//                }
            }

            LevelBasedGraphAccess engineAccess = ((LevelBasedGraphAccess) engine);

            this.updatingSectionData.clearCache();
            if (!updateBlockLight) {
                for (long newArray : this.queuedSections.keySet()) {
                    if (this.storingLightForSection(newArray)) {
                        int newX = SectionPos.sectionToBlockCoord(SectionPos.x(newArray));
                        int newY = SectionPos.sectionToBlockCoord(SectionPos.y(newArray));
                        int newZ = SectionPos.sectionToBlockCoord(SectionPos.z(newArray));

                        for (Direction direction : DIRECTIONS) {
                            long posOffset = SectionPos.offset(newArray, direction);
                            if (!this.queuedSections.containsKey(posOffset) && this.storingLightForSection(posOffset)) {
                                for (int i1 = 0; i1 < 16; ++i1) {
                                    for (int j1 = 0; j1 < 16; ++j1) {
                                        long k1;
                                        long l1;
                                        switch (direction) {
                                            case DOWN:
                                                k1 = BlockPos.asLong(newX + j1, newY, newZ + i1);
                                                l1 = BlockPos.asLong(newX + j1, newY - 1, newZ + i1);
                                                break;
                                            case UP:
                                                k1 = BlockPos.asLong(newX + j1, newY + 16 - 1, newZ + i1);
                                                l1 = BlockPos.asLong(newX + j1, newY + 16, newZ + i1);
                                                break;
                                            case NORTH:
                                                k1 = BlockPos.asLong(newX + i1, newY + j1, newZ);
                                                l1 = BlockPos.asLong(newX + i1, newY + j1, newZ - 1);
                                                break;
                                            case SOUTH:
                                                k1 = BlockPos.asLong(newX + i1, newY + j1, newZ + 16 - 1);
                                                l1 = BlockPos.asLong(newX + i1, newY + j1, newZ + 16);
                                                break;
                                            case WEST:
                                                k1 = BlockPos.asLong(newX, newY + i1, newZ + j1);
                                                l1 = BlockPos.asLong(newX - 1, newY + i1, newZ + j1);
                                                break;
                                            default:
                                                k1 = BlockPos.asLong(newX + 16 - 1, newY + i1, newZ + j1);
                                                l1 = BlockPos.asLong(newX + 16, newY + i1, newZ + j1);
                                        }

                                        engineAccess.invokeCheckEdge(k1, l1, engineAccess.invokeComputeLevelFromNeighbor(k1, l1, engineAccess.invokeGetLevel(k1)),
                                            false);
                                        engineAccess.invokeCheckEdge(l1, k1, engineAccess.invokeComputeLevelFromNeighbor(l1, k1, engineAccess.invokeGetLevel(l1)),
                                            false);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ObjectIterator<Long2ObjectMap.Entry<DataLayer>> objectiterator = this.queuedSections.long2ObjectEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Long2ObjectMap.Entry<DataLayer> entry1 = objectiterator.next();
                long k2 = entry1.getLongKey();
                if (this.storingLightForSection(k2)) {
                    objectiterator.remove();
                }
            }

        }
    }

}