package io.github.opencubicchunks.cubicchunks.mixin.core.client.world;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.ClientChunkProviderCubeArray;
import io.github.opencubicchunks.cubicchunks.chunk.IClientCubeProvider;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.cube.EmptyCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.client.ClientChunkProviderChunkArrayAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.client.IClientWorld;
import io.github.opencubicchunks.cubicchunks.world.lighting.IWorldLightManager;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class MixinClientChunkProvider implements IClientCubeProvider {

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final private ClientLevel level;

    @Shadow private volatile ClientChunkCache.Storage storage;

    private volatile ClientChunkProviderCubeArray cubeArray;
    private EmptyCube emptyCube;

    @Shadow public abstract int getLoadedChunksCount();

    @Shadow public abstract LevelLightEngine getLightEngine();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(ClientLevel clientWorldIn, int viewDistance, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) clientWorldIn).isCubic()) {
            return;
        }

        this.cubeArray = new ClientChunkProviderCubeArray(adjustCubeViewDistance(viewDistance), adjustCubeViewDistance(CubicChunks.config().client.verticalViewDistance), this.level);
        this.emptyCube = new EmptyCube(level, CubePos.of(0, 0, 0));
    }

    private int adjustCubeViewDistance(int viewDistance) {
        return Math.max(2, Coords.sectionToCubeCeil(viewDistance)) + 3;
    }

    private static boolean isCubeValid(@Nullable BigCube cube, int x, int y, int z) {
        if (cube == null) {
            return false;
        }
        CubePos cubePos = cube.getCubePos();
        return cubePos.getX() == x && cubePos.getY() == y && cubePos.getZ() == z;
    }

    @Override
    public void drop(int x, int y, int z) {
        if (!this.cubeArray.inView(x, y, z)) {
            return;
        }
        int index = this.cubeArray.getIndex(x, y, z);
        BigCube cube = this.cubeArray.get(index);
        if (isCubeValid(cube, x, y, z)) {
            // TODO: forge cube unload event
            // net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));
            this.cubeArray.unload(index, cube, null);
        }

    }

    @Nullable
    @Override
    public BigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean load) {
        if (this.cubeArray.inView(cubeX, cubeY, cubeZ)) {
            BigCube chunk = this.cubeArray.get(this.cubeArray.getIndex(cubeX, cubeY, cubeZ));
            if (isCubeValid(chunk, cubeX, cubeY, cubeZ)) {
                return chunk;
            }
        }

        return load ? this.emptyCube : null;
    }

    @Override
    public BigCube replaceWithPacketData(int cubeX, int cubeY, int cubeZ,
                                         @Nullable ChunkBiomeContainer biomes, FriendlyByteBuf readBuffer, CompoundTag nbtTagIn, boolean cubeExists) {

        if (!this.cubeArray.inView(cubeX, cubeY, cubeZ)) {
            LOGGER.warn("Ignoring cube since it's not in the view range: {}, {}, {}", cubeX, cubeY, cubeZ);
            return null;
        }
        int index = this.cubeArray.getIndex(cubeX, cubeY, cubeZ);
        BigCube cube = this.cubeArray.cubes.get(index);
        if (!isCubeValid(cube, cubeX, cubeY, cubeZ)) {
            if (biomes == null) {
                LOGGER.warn("Ignoring cube since we don't have complete data: {}, {}, {}", cubeX, cubeY, cubeZ);
                return null;
            }

            cube = new BigCube(this.level, CubePos.of(cubeX, cubeY, cubeZ), biomes);
            cube.read(biomes, readBuffer, nbtTagIn, cubeExists);
            this.cubeArray.replace(index, cube);
        } else {
            cube.read(biomes, readBuffer, nbtTagIn, cubeExists);
        }

        LevelLightEngine worldlightmanager = this.getLightEngine();
        ((IWorldLightManager) worldlightmanager).enableLightSources(CubePos.of(cubeX, cubeY, cubeZ), true);

        LevelChunkSection[] cubeSections = cube.getCubeSections();
        for (int i = 0; i < cubeSections.length; ++i) {
            LevelChunkSection chunksection = cubeSections[i];
            worldlightmanager.updateSectionStatus(Coords.sectionPosByIndex(cube.getCubePos(), i), LevelChunkSection.isEmpty(chunksection));
        }

        ((IClientWorld) this.level).onCubeLoaded(cubeX, cubeY, cubeZ);
        // TODO: forge client cube load event
        // net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkEvent.Load(cube));
        return cube;
    }

    @Override public void setCenter(int sectionX, int sectionY, int sectionZ) {
        this.cubeArray.centerX = Coords.sectionToCube(sectionX);
        this.cubeArray.centerY = Coords.sectionToCube(sectionY);
        this.cubeArray.centerZ = Coords.sectionToCube(sectionZ);
    }

    @Override
    public void updateCubeViewRadius(int hDistance, int vDistance) {
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                throw new UnsupportedOperationException("Attempting to set a cubeViewRange in a noncubic world.");
            }
        }

        int oldHDistance = this.cubeArray.horizontalViewDistance;
        int oldVDistance = this.cubeArray.verticalViewDistance;

        int newHDistance = adjustCubeViewDistance(hDistance);
        int newVDistance = adjustCubeViewDistance(vDistance);

        if (oldHDistance == newHDistance && oldVDistance == newVDistance) {
            return;
        }
        ClientChunkProviderCubeArray array = new ClientChunkProviderCubeArray(newHDistance, newVDistance, this.level);
        array.centerX = this.cubeArray.centerX;
        array.centerY = this.cubeArray.centerY;
        array.centerZ = this.cubeArray.centerZ;

        for (int k = 0; k < this.cubeArray.cubes.length(); ++k) {
            BigCube chunk = this.cubeArray.cubes.get(k);
            if (chunk == null) {
                continue;
            }
            CubePos cubePos = chunk.getCubePos();
            if (array.inView(cubePos.getX(), cubePos.getY(), cubePos.getZ())) {
                array.replace(array.getIndex(cubePos.getX(), cubePos.getY(), cubePos.getZ()), chunk);
            }
        }

        this.cubeArray = array;
    }

    /**
     * @author Barteks2x
     * @reason Change the debug string
     */
    @Inject(method = "gatherStats", at = @At("HEAD"), cancellable = true)
    public void gatherStats(CallbackInfoReturnable<String> cir) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }

        //noinspection ConstantConditions
        cir.setReturnValue("Client Chunk Cache: " + ((ClientChunkProviderChunkArrayAccess) (Object) this.storage).getChunks().length() + ", " + this.getLoadedChunksCount() +
            " | " + this.cubeArray.cubes.length() + ", " + getLoadedCubesCount());
    }

    public int getLoadedCubesCount() {
        return this.cubeArray.loaded;
    }
}