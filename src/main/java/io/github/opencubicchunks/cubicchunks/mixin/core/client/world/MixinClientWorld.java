package io.github.opencubicchunks.cubicchunks.mixin.core.client.world;

import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.ClientChunkProviderCubeArray;
import io.github.opencubicchunks.cubicchunks.chunk.IClientCubeProvider;
import io.github.opencubicchunks.cubicchunks.chunk.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.client.IClientWorld;
import io.github.opencubicchunks.cubicchunks.world.entity.IsCubicEntityContext;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientWorld extends Level implements IClientWorld {

    @Shadow @Final private TransientEntitySectionManager<Entity> entityStorage;

    @Shadow @Final private ClientChunkCache chunkSource;

    protected MixinClientWorld(WritableLevelData p_i231617_1_, ResourceKey<Level> p_i231617_2_, DimensionType p_i231617_4_,
                               Supplier<ProfilerFiller> p_i231617_5_, boolean p_i231617_6_, boolean p_i231617_7_, long p_i231617_8_) {
        super(p_i231617_1_, p_i231617_2_, p_i231617_4_, p_i231617_5_, p_i231617_6_, p_i231617_7_, p_i231617_8_);
    }


    @Inject(method = "<init>", at = @At("RETURN"))
    private void setIsCubicContext(ClientPacketListener clientPacketListener, ClientLevel.ClientLevelData clientLevelData, ResourceKey<Level> resourceKey, DimensionType dimensionType, int i,
                                   Supplier<ProfilerFiller> supplier, LevelRenderer levelRenderer, boolean bl, long l, CallbackInfo ci) {
        ((IsCubicEntityContext) this.entityStorage).setIsCubic(((CubicLevelHeightAccessor) this).isCubic());
    }

    @Override
    public void onCubeLoaded(int cubeX, int cubeY, int cubeZ) {
        //TODO: implement colorCaches in onCubeLoaded
//        this.colorCaches.forEach((p_228316_2_, p_228316_3_) -> {
//            p_228316_3_.invalidateChunk(chunkX, chunkZ);
//        });
        this.entityStorage.startTicking(new ImposterChunkPos(cubeX, cubeY, cubeZ));
    }

    @Override public void onCubeUnload(BigCube cube) {
        this.entityStorage.stopTicking(new ImposterChunkPos(cube.getCubePos()));
    }

    @Redirect(method = "onChunkLoaded",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;startTicking(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void doNothingOnLoadIfCube(TransientEntitySectionManager<?> transientEntitySectionManager, ChunkPos pos) {
        if (!((CubicLevelHeightAccessor) this).isCubic()) {
            transientEntitySectionManager.startTicking(pos);
        }
    }


    @Redirect(method = "unload",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;stopTicking(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void doNothingOnUnloadIfCube(TransientEntitySectionManager<?> transientEntitySectionManager, ChunkPos pos) {
        if (!((CubicLevelHeightAccessor) this).isCubic()) {
            transientEntitySectionManager.stopTicking(pos);
        }
    }

    @Override public int getMinBuildHeight() {
        if (this.chunkSource != null) {
            ClientChunkProviderCubeArray cubeArray = ((IClientCubeProvider) this.chunkSource).getCubeArray();
            return Coords.cubeToMinBlock(cubeArray.centerY - (cubeArray.verticalViewDistance  -4));
        }
        return super.getMinBuildHeight();
    }

    @Override public int getHeight() {
        if (this.chunkSource != null) {
            ClientChunkProviderCubeArray cubeArray = ((IClientCubeProvider) this.chunkSource).getCubeArray();
            return Coords.cubeToMaxBlock((cubeArray.verticalViewDistance - 4) * 2) + 1;
        }
        return super.getHeight();
    }
}