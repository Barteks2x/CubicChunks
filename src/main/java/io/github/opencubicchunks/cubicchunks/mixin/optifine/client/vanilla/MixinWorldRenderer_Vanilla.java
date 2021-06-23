package io.github.opencubicchunks.cubicchunks.mixin.optifine.client.vanilla;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.client.ViewFrustumAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class MixinWorldRenderer_Vanilla {

    @Shadow private int lastViewDistance;

    @Shadow private ViewArea viewArea;

    @Shadow private ClientLevel level;

    @Shadow @Final private Minecraft minecraft;

    private int lastVerticalViewDistance = -1;

    @Shadow public abstract void allChanged();


    /**
     * @author AidanLovelace
     * @reason Make sure we load the renderers again if the vertical view distance changed but not if the normal render distance changed because then they'll be loaded again anyways.
     */
    @Inject(method = "setupRender", at = @At("HEAD"))
    private void setupVerticalViewDistance(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci) {
        if (this.minecraft.options.renderDistance != this.lastViewDistance) return;
        if (this.lastVerticalViewDistance != CubicChunks.commonConfig().getVerticalViewDistance()) {
            this.allChanged();
        }
    }

    @Inject(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;setFancy(Z)V"))
    private void setLastVerticalViewDistance(CallbackInfo ci) {
        this.lastVerticalViewDistance = CubicChunks.commonConfig().getVerticalViewDistance();
    }

    /**
     * @author Barteks2x
     * @reason Vanilla doesn't use y pos, and constrains between 0 and 256
     */
    @Nullable
    @Inject(method = "getRelativeFrom", at = @At("HEAD"), cancellable = true)
    private void getRelativeFrom(BlockPos playerPos, ChunkRenderDispatcher.RenderChunk renderChunkBase, Direction facing,
                                 CallbackInfoReturnable<ChunkRenderDispatcher.RenderChunk> cir) {

        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }

        cir.cancel();
        BlockPos blockpos = renderChunkBase.getRelativeOrigin(facing);
        if (Mth.abs(playerPos.getX() - blockpos.getX()) <= this.lastViewDistance * 16
            && Mth.abs(playerPos.getY() - blockpos.getY()) <= this.lastVerticalViewDistance * 16
            && Mth.abs(playerPos.getZ() - blockpos.getZ()) <= this.lastViewDistance * 16) {
            cir.setReturnValue(((ViewFrustumAccess) this.viewArea).invokeGetRenderChunkAt(blockpos));
        } else {
            cir.setReturnValue(null);
        }
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;"
        + "Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZ)V"))
    private float modifyFogDistance(float hFogDistance) {
        int verticalViewDistance = CubicChunks.commonConfig().getVerticalViewDistance();
        float verticalFogDistance;
        if (verticalViewDistance >= 4) {
            verticalFogDistance = verticalViewDistance * 16;
        } else {
            verticalFogDistance = Math.max((verticalViewDistance * 16) - 16.0F, 32.0F);
        }
        return Math.max(hFogDistance, verticalFogDistance);
    }

    @Redirect(method = "updateRenderChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"))
    private double considerVerticalRenderDistance(double value, double min, double max) {
        return Mth.clamp(Math.max(value, CubicChunks.commonConfig().getVerticalViewDistance()), min, max);
    }
}