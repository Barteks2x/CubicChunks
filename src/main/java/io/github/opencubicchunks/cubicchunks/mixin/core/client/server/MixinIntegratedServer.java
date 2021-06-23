package io.github.opencubicchunks.cubicchunks.mixin.core.client.server;

import java.util.function.BooleanSupplier;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.IVerticalView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class MixinIntegratedServer {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"), cancellable = true)
    private void tickServer(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        int horizontalViewDistance = Math.max(2, this.minecraft.options.renderDistance + -1);

        int verticalViewDistance = Math.max(2, CubicChunks.commonConfig().getVerticalViewDistance() + -1);
        int currentVerticalViewDistance1 = ((IVerticalView) ((IntegratedServer) (Object) this).getPlayerList()).getVerticalViewDistance();

        if (verticalViewDistance != currentVerticalViewDistance1) {
            CubicChunks.LOGGER.info("Changing vertical view distance to {}, from {}", verticalViewDistance, currentVerticalViewDistance1);
            ((IVerticalView) ((IntegratedServer) (Object) this).getPlayerList()).setIncomingVerticalViewDistance(verticalViewDistance);
            ((IntegratedServer) (Object) this).getPlayerList().setViewDistance(horizontalViewDistance);
        }
    }
}
