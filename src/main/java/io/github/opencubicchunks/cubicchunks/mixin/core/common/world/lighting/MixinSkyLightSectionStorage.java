package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.lighting;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.cube.EmptyCube;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LayerLightSectionStorageAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.lighting.ICubeLightProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightSectionStorage.class)
public abstract class MixinSkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
    private boolean isCubic;

    private MixinSkyLightSectionStorage(LightLayer lightLayer, LightChunkGetter lightChunkGetter,
                                        SkyLightSectionStorage.SkyDataLayerStorageMap dataLayerStorageMap) {
        super(lightLayer, lightChunkGetter, dataLayerStorageMap);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(LightChunkGetter lightChunkGetter, CallbackInfo ci) {
        isCubic = ((CubicLevelHeightAccessor) lightChunkGetter.getLevel()).isCubic();
    }

    @Inject(method = "getLightValue(JZ)I", cancellable = true, at = @At("HEAD"))
    private void onGetLightValue(long blockPos, boolean cached, CallbackInfoReturnable<Integer> cir) {
        if (!isCubic) {
            return;
        }

        // Replace this method with an equivalent of BlockLightSectionStorage.getLightValue,
        // since we don't need sky light logic
        long l = SectionPos.blockToSection(blockPos);
        DataLayer dataLayer = this.getDataLayer(l, cached);
        int x = BlockPos.getX(blockPos);
        int y = BlockPos.getY(blockPos);
        int z = BlockPos.getZ(blockPos);
        if (dataLayer == null) {

            int chunkX = Coords.blockToSection(x);
            int chunkZ = Coords.blockToSection(z);
            BlockGetter chunk = ((LayerLightSectionStorageAccess) this).getChunkSource().getChunkForLighting(chunkX, chunkZ);

            Level level = (Level) ((LayerLightSectionStorageAccess) this).getChunkSource().getLevel();

            if (chunk == null) {
                //TODO: re-enable asserts and remove temporary check once mojang fix mushroom generation https://bugs.mojang.com/browse/MC-229557
                //mushroom generation checking light values before light stage has been reached causes these assets to trigger false positives
                System.out.println("getLightValue: Missing chunk for lighting.");
                if (level.isClientSide) {
                    assert (((ICubicWorld) level).getCube(new BlockPos(x, y, z)) instanceof EmptyCube);
                }

                //Client can have null chunks when trying to render an entity in a chunk that hasn't arrived yet (neither has the cube at that point)
//                assert level.isClientSide;
//                assert (((ICubicWorld) level).getCube(new BlockPos(x, y, z)) instanceof EmptyCube);

                //Used as a default light value, eg for rendering entities that are not within a chunk/cube
                cir.setReturnValue(15);
                return;
            }

            //TODO: Optimize
            BlockGetter cube = ((ICubeLightProvider) ((LayerLightSectionStorageAccess) this).getChunkSource()).getCubeForLighting(
                chunkX, Coords.blockToSection(y), chunkZ);
            if (cube == null || !((IBigCube) cube).getStatus().isOrAfter(ChunkStatus.LIGHT)) {
                cir.setReturnValue(0);
                return;
            }

            Heightmap lightHeightmap = ((LightHeightmapGetter) chunk).getLightHeightmap();
            int height = lightHeightmap.getFirstAvailable(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
            cir.setReturnValue(height <= y ? 15 : 0);
        } else {
            cir.setReturnValue(dataLayer.get(
                SectionPos.sectionRelative(x),
                SectionPos.sectionRelative(y),
                SectionPos.sectionRelative(z)));
        }
    }

    @Inject(method = "onNodeAdded", cancellable = true, at = @At("HEAD"))
    private void onOnNodeAdded(long sectionPos, CallbackInfo ci) {
        if (!isCubic) return;
        ci.cancel();
    }

    @Inject(method = "onNodeRemoved", cancellable = true, at = @At("HEAD"))
    private void onOnNodeRemoved(long sectionPos, CallbackInfo ci) {
        if (!isCubic) return;
        ci.cancel();
    }

    @Inject(method = "enableLightSources", cancellable = true,
        at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;runAllUpdates()V"))
    private void onEnableLightSources(long columnPos, boolean enabled, CallbackInfo ci) {
        if (!isCubic) return;
        if (enabled) {
            // We handle skylight emission differently anyway, so we don't need vanilla's sky light source system
            ci.cancel();
        }
    }

    @Inject(method = "createDataLayer", cancellable = true, at = @At("HEAD"))
    private void onCreateDataLayer(long sectionPos, CallbackInfoReturnable<DataLayer> cir) {
        if (!isCubic) return;
        cir.setReturnValue(super.createDataLayer(sectionPos));
    }

    @Inject(method = "markNewInconsistencies", cancellable = true, at = @At("HEAD"))
    private void onMarkNewInconsistencies(LayerLightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, ?> lightProvider, boolean doSkylight, boolean skipEdgeLightPropagation,
                                          CallbackInfo ci) {
        if (!isCubic) return;
        ci.cancel();
        super.markNewInconsistencies(lightProvider, doSkylight, skipEdgeLightPropagation);
    }

    @Inject(method = "hasSectionsBelow", cancellable = true, at = @At("HEAD"))
    private void onHasSectionsBelow(int sectionY, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubic) return;
        cir.setReturnValue(true);
    }

    @Inject(method = "isAboveData", cancellable = true, at = @At("HEAD"))
    private void onIsAboveData(long sectionPos, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubic) return;
        cir.setReturnValue(false);
    }
}
