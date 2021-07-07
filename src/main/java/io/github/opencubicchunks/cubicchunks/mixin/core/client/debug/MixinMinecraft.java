package io.github.opencubicchunks.cubicchunks.mixin.core.client.debug;

import java.util.function.Function;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Function4;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.BlockPosLoadFailureScreen;
import io.github.opencubicchunks.cubicchunks.debug.DebugVisualization;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BlockPosAccess;
import io.github.opencubicchunks.cubicchunks.server.CCServerSavedData;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow @Nullable public ClientLevel level;

    @Shadow public abstract void setScreen(@org.jetbrains.annotations.Nullable Screen screen);

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void unloadWorld(ClientLevel clientLevel, CallbackInfo ci) {
        if (this.level != null) {
            if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
                return;
            }
            DebugVisualization.onWorldUnload(this.level);
        }
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void unloadWorld(Screen screen, CallbackInfo ci) {
        if (this.level != null) {
            if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
                return;
            }

            DebugVisualization.onWorldUnload(this.level);
        }
    }

    @Inject(method = "doLoadLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;"),
        locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true, require = 0)
    private void blockPosLongPackWarning(String worldName, RegistryAccess.RegistryHolder registryTracker,
                                         Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> dataPackSettingsGetter,
                                         Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> savePropertiesGetter,
                                         boolean safeMode, Minecraft.ExperimentalDialogType worldLoadAction, CallbackInfo ci, LevelStorageSource.LevelStorageAccess levelStorageAccess2,
                                         Minecraft.ServerStem serverStem2, WorldData worldData, boolean bl) {

        CCServerSavedData ccServerSavedData = (CCServerSavedData) worldData;
        if (ccServerSavedData.blockPosLongNoMatch()) {
            this.setScreen(new BlockPosLoadFailureScreen(worldName, ccServerSavedData.getServerPackedXZ()));
            CubicChunks.LOGGER.error(String.format("Could not start the server because this server's XZ size does not match the XZ size set in the config.\n Server's XZ size: %s"
                + "\nConfig XZ size: %s", MathUtil.unpackXZSize(ccServerSavedData.getServerPackedXZ()), MathUtil.unpackXZSize(BlockPosAccess.getPackedXLength())));
            ci.cancel();
        }
    }
}
