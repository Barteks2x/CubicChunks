package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public abstract class MixinViewFrustum {
    @Shadow public ChunkRenderDispatcher.RenderChunk[] chunks;
    @Shadow protected int chunkGridSizeY;
    @Shadow protected int chunkGridSizeX;
    @Shadow protected int chunkGridSizeZ;

    @Shadow protected abstract int getChunkIndex(int x, int y, int z);

    /**
     * @author Barteks2x
     */
    @Inject(method = "setViewDistance", at = @At("HEAD"), cancellable = true)
    protected void setViewDistance(int renderDistanceChunks, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                return;
            }
        }
        ci.cancel();
        int hDistance = renderDistanceChunks * 2 + 1;
        int vDistance = CubicChunks.commonConfig().getVerticalViewDistance() * 2 + 1;

        this.chunkGridSizeX = hDistance;
        this.chunkGridSizeY = vDistance;
        this.chunkGridSizeZ = hDistance;
    }

    @Inject(method = "repositionCamera", at = @At(value = "HEAD"), cancellable = true, require = 1)
    private void repositionCamera(double viewEntityX, double viewEntityZ, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                return;
            }
        }


        Entity view = Minecraft.getInstance().getCameraEntity();
        double x = view.getX();
        double y = view.getY();
        double z = view.getZ();
        int viewX = Mth.floor(x);
        int viewY = Mth.floor(y);
        int viewZ = Mth.floor(z);

        for (int xIndex = 0; xIndex < this.chunkGridSizeX; ++xIndex) {
            int xBase = this.chunkGridSizeX * 16;
            int xTemp = viewX - 8 - xBase / 2;
            int posX = xTemp + Math.floorMod(xIndex * 16 - xTemp, xBase);

            for (int zIndex = 0; zIndex < this.chunkGridSizeZ; ++zIndex) {
                int zBase = this.chunkGridSizeZ * 16;
                int zTemp = viewZ - 8 - zBase / 2;
                int posZ = zTemp + Math.floorMod(zIndex * 16 - zTemp, zBase);

                for (int yIndex = 0; yIndex < this.chunkGridSizeY; ++yIndex) {
                    int yBase = this.chunkGridSizeY * 16;
                    int yTemp = viewY - 8 - yBase / 2;
                    int posY = yTemp + Math.floorMod(yIndex * 16 - yTemp, yBase);
                    ChunkRenderDispatcher.RenderChunk renderChunk = this.chunks[this.getChunkIndex(xIndex, yIndex, zIndex)];
                    renderChunk.setOrigin(posX, posY, posZ);
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "getRenderChunkAt", at = @At(value = "HEAD"), cancellable = true)
    private void getRenderChunkAt(BlockPos pos, CallbackInfoReturnable<ChunkRenderDispatcher.RenderChunk> cir) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                return;
            }
        }

        int x = Mth.intFloorDiv(pos.getX(), 16);
        int y = Mth.intFloorDiv(pos.getY(), 16);
        int z = Mth.intFloorDiv(pos.getZ(), 16);
        x = Mth.positiveModulo(x, this.chunkGridSizeX);
        y = Mth.positiveModulo(y, this.chunkGridSizeY);
        z = Mth.positiveModulo(z, this.chunkGridSizeZ);
        ChunkRenderDispatcher.RenderChunk renderChunk = this.chunks[this.getChunkIndex(x, y, z)];
        cir.setReturnValue(renderChunk);
    }

    /**
     * @author Barteks2x
     * @reason correctly use y position
     */
    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    public void setDirty(int x, int y, int z, boolean important, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                return;
            }
        }
        ci.cancel();
        int xIdx = Math.floorMod(x, this.chunkGridSizeX);
        int yIdx = Math.floorMod(y, this.chunkGridSizeY);
        int zIdx = Math.floorMod(z, this.chunkGridSizeZ);
        ChunkRenderDispatcher.RenderChunk renderChunk = this.chunks[this.getChunkIndex(xIdx, yIdx, zIdx)];
        renderChunk.setDirty(important);
    }
}