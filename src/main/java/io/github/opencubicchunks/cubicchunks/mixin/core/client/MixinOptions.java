package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class MixinOptions {

    @Shadow public CloudStatus renderClouds;

    @Inject(method = "getCloudsType", at = @At("HEAD"), cancellable = true)
    private void getCloudsForVerticalViewDistance(CallbackInfoReturnable<CloudStatus> cir) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                return;
            }
        }
        cir.setReturnValue(CubicChunks.commonConfig().getVerticalViewDistance() >= 4 ? this.renderClouds : CloudStatus.OFF);

    }
}