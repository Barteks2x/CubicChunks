package io.github.opencubicchunks.cubicchunks.mixin.core.client.debug;

import java.util.List;

import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Shadow @Final private Minecraft minecraft;

    @Shadow protected abstract LevelChunk getServerChunk();

    @Inject(method = "getGameInformation",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",
            ordinal = 0),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void onGetGameInformation(CallbackInfoReturnable<List<String>> cir, String string2, BlockPos pos, Entity entity, Direction direction,
                                      String string7, Level level, LongSet longSet, List<String> list, LevelChunk clientChunk, int i) {
        LevelChunk serverChunk = this.getServerChunk();
        if (((CubicLevelHeightAccessor) level).isCubic()) {
            if (this.minecraft.getSingleplayerServer() != null) {
                String serverHeight = "???";
                if (serverChunk != null) {
                    LightSurfaceTrackerWrapper heightmap = ((LightHeightmapGetter) serverChunk).getServerLightHeightmap();
                    int height = heightmap.getFirstAvailable(pos.getX() & 0xF, pos.getZ() & 0xF);
                    serverHeight = "" + height;
                }
                list.add("Server light heightmap height: " + serverHeight);
            }
            int clientHeight = ((LightHeightmapGetter) clientChunk).getClientLightHeightmap().getFirstAvailable(pos.getX() & 0xF, pos.getZ() & 0xF);
            list.add("Client light heightmap height: " + clientHeight);
        }

        // No cubic check here because it's a vanilla feature that was removed anyway
        if (this.minecraft.getSingleplayerServer() != null) {
            if (serverChunk != null) {
                LevelLightEngine lightingProvider = level.getChunkSource().getLightEngine();
                list.add("Server Light: (" + lightingProvider.getLayerListener(LightLayer.SKY).getLightValue(pos) + " sky, "
                    + lightingProvider.getLayerListener(LightLayer.BLOCK).getLightValue(pos) + " block)");
            } else {
                list.add("Server Light: (?? sky, ?? block)");
            }
        }
    }
}
