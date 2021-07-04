package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import io.github.opencubicchunks.cubicchunks.chunk.CubeMap;
import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoTickList;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProtoChunk.class)
public abstract class MixinProtoChunk implements LightHeightmapGetter, LevelHeightAccessor, CubeMapGetter, CubicLevelHeightAccessor {

    private boolean isCubic;
    private boolean generates2DChunks;
    private WorldStyle worldStyle;

    private LightSurfaceTrackerWrapper lightHeightmap;
    private CubeMap cubeMap;

    @Shadow @Final private LevelHeightAccessor levelHeightAccessor;

    @Shadow public abstract ChunkStatus getStatus();

    @Override
    public Heightmap getLightHeightmap() {
        if (!isCubic) {
            throw new UnsupportedOperationException("Attempted to get light heightmap on a non-cubic chunk");
        }
        return lightHeightmap;
    }

    @Override
    public CubeMap getCubeMap() {
        // TODO actually init this properly instead of doing lazy init here
        if (cubeMap == null) {
            cubeMap = new CubeMap();
        }
        return cubeMap;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
        + "Lnet/minecraft/world/level/chunk/ProtoTickList;Lnet/minecraft/world/level/chunk/ProtoTickList;Lnet/minecraft/world/level/LevelHeightAccessor;)V", at = @At("RETURN"))
    private void setCubic(ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkSection[] levelChunkSections, ProtoTickList<Block> protoTickList, ProtoTickList<Fluid> protoTickList2,
                          LevelHeightAccessor heightAccessor, CallbackInfo ci) {
        isCubic = ((CubicLevelHeightAccessor) heightAccessor).isCubic();
        generates2DChunks = ((CubicLevelHeightAccessor) heightAccessor).generates2DChunks();
        worldStyle = ((CubicLevelHeightAccessor) heightAccessor).worldStyle();
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
            + "Lnet/minecraft/world/level/chunk/ProtoTickList;Lnet/minecraft/world/level/chunk/ProtoTickList;Lnet/minecraft/world/level/LevelHeightAccessor;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"))
    private int getFakeSectionCount(LevelHeightAccessor accessor) {
        if (!((CubicLevelHeightAccessor) accessor).isCubic()) {
            return levelHeightAccessor.getSectionsCount();
        }

        if (accessor instanceof CubePrimer.FakeSectionCount) {
            return accessor.getSectionsCount();
        }

        if (accessor instanceof Level) {
            if (((CubicLevelHeightAccessor) accessor).generates2DChunks()) {
                int height = ((Level) accessor).dimensionType().height();
                int minY = ((Level) accessor).dimensionType().minY();

                int minSectionY = SectionPos.blockToSectionCoord(minY);
                int maxSectionY = SectionPos.blockToSectionCoord(minY + height - 1) + 1;

                int sectionCount = maxSectionY - minSectionY;
                return sectionCount;
            }
        }
        if (accessor.getMaxBuildHeight() > 2048) {
            return 16;
        }

        return Math.min(IBigCube.SECTION_COUNT * 2, accessor.getSectionsCount()); // TODO: properly handle ProtoChunk
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void setHeight(CallbackInfoReturnable<Integer> cir) {
        if (this.levelHeightAccessor instanceof Level) {
            if (this.generates2DChunks()) {
                cir.setReturnValue(((Level) levelHeightAccessor).dimensionType().height());
            }
        }
    }

    @Inject(method = "getMinBuildHeight", at = @At("HEAD"), cancellable = true)
    private void setMinHeight(CallbackInfoReturnable<Integer> cir) {
        if (this.levelHeightAccessor instanceof Level) {
            if (this.generates2DChunks()) {
                cir.setReturnValue(((Level) levelHeightAccessor).dimensionType().minY());
            }
        }
    }

    @Override public WorldStyle worldStyle() {
        return worldStyle;
    }

    @Override public boolean isCubic() {
        return isCubic;
    }

    @Override public boolean generates2DChunks() {
        return generates2DChunks;
    }

    @Inject(
        method = "setStatus(Lnet/minecraft/world/level/chunk/ChunkStatus;)V",
        at = @At("RETURN")
    )
    private void onSetStatus(ChunkStatus status, CallbackInfo ci) {
        if (!this.isCubic()) {
            return;
        }
        if (lightHeightmap == null && this.getStatus().isOrAfter(ChunkStatus.FEATURES)) {
            // Lighting only starts happening after FEATURES, so we init here to avoid creating unnecessary heightmaps
            lightHeightmap = new LightSurfaceTrackerWrapper((ChunkAccess) this);
        }
    }
}
