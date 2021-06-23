package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.config.reloadlisteners.WorldStyleReloadListener;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class MixinWorld implements ICubicWorld, LevelReader {

    private boolean isCubic;
    private boolean generates2DChunks;
    private WorldStyle worldStyle;

    @Shadow @Final public boolean isClientSide;

    @Shadow public abstract ResourceKey<Level> dimension();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setCubic(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, DimensionType dimensionType, Supplier<ProfilerFiller> supplier, boolean isClient, boolean bl2,
                          long l, CallbackInfo ci) {
        worldStyle = this.isClientSide ? CubicChunks.currentClientStyle : WorldStyleReloadListener.WORLD_WORLD_STYLE.getOrDefault(resourceKey.location(), WorldStyle.CHUNK);
        isCubic = worldStyle.isCubic();
        generates2DChunks = worldStyle.generates2DChunks();
    }

    @Override public int getHeight() {
        if (!isCubic()) {
            return LevelReader.super.getHeight();
        }

        return 40000000;
    }

    /**
     * @author Setadokalo
     * @reason Allows teleporting outside +/-20000000 blocks on the Y axis
     */
    @Overwrite private static boolean isOutsideSpawnableHeight(int y) {
        return CubicChunks.MIN_SUPPORTED_HEIGHT > y || y > CubicChunks.MAX_SUPPORTED_HEIGHT;
    }

    @Override public int getMinBuildHeight() {
        if (!isCubic()) {
            return LevelReader.super.getMinBuildHeight();
        }

        return -20000000;
    }

    @Inject(method = "blockEntityChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;markUnsaved()V"))
    private void onBlockEntityChanged(BlockPos blockPos, CallbackInfo ci) {
        if (!isCubic()) {
            return;
        }

        this.getCubeAt(blockPos).setDirty(true);
    }

    public IBigCube getCubeAt(BlockPos pos) {
        return this.getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
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

    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, true);
    }

    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status) {
        return this.getCube(cubeX, cubeY, cubeZ, status, true);
    }

    //The method .getWorld() No longer exists
    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean nonnull) {
        IBigCube icube = ((ICubeProvider) ((Level) (Object) this).getChunkSource()).getCube(cubeX, cubeY, cubeZ, requiredStatus, nonnull);
        if (icube == null && nonnull) {
            throw new IllegalStateException("Should always be able to create a cube!");
        } else {
            return icube;
        }
    }
}