package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {


    @Shadow @Final private Minecraft minecraft;

    @Shadow private float renderDistance;

    @Inject(method = "getDepthFar", at = @At("HEAD"), cancellable = true)
    private void cubicGetDepthFar(CallbackInfoReturnable<Float> cir) {
        if (!((CubicLevelHeightAccessor) this.minecraft.level).isCubic()) {
            return;
        }
        float horizontalRenderDistance = this.renderDistance * 4.0F;
        float verticalRenderDistance = (CubicChunks.commonConfig().getVerticalViewDistance() * 16) * 4.0F;
        cir.setReturnValue(Math.max(horizontalRenderDistance, verticalRenderDistance));
    }
}
