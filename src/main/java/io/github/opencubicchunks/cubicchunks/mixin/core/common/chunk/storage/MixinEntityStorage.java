package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.storage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.DataFixer;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.chunk.storage.CubicEntityStorage;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.storage.RegionCubeIO;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityStorage.class)
public abstract class MixinEntityStorage implements CubicEntityStorage {

    @Shadow @Final private LongSet emptyChunks;

    @Shadow @Final private ServerLevel level;

    private RegionCubeIO cubeWorker;

    @Shadow protected abstract CompoundTag upgradeChunkTag(CompoundTag chunkTag);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setupCubeIO(ServerLevel serverLevel, File file, DataFixer dataFixer, boolean bl, Executor executor, CallbackInfo ci) throws IOException {
        if (((CubicLevelHeightAccessor) serverLevel).isCubic()) {
            cubeWorker = new RegionCubeIO(file, dataFixer, file.getName(), file.getName());
        }
    }

    @Override public CompletableFuture<ChunkEntities<Entity>> loadCubeEntities(CubePos pos) {
        if (this.emptyChunks.contains(pos.asLong())) {
            return CompletableFuture.completedFuture(emptyCube(pos));
        }

        try {
            CompoundTag compoundTag = this.cubeWorker.loadCubeNBT(pos);
            if (compoundTag == null) {
                this.emptyChunks.add(pos.asLong());
                return CompletableFuture.completedFuture(emptyCube(pos));
            } else {
                try {
                    CubePos cubePos = readCubePos(compoundTag);
                    if (!Objects.equals(pos, cubePos)) {
                        CubicChunks.LOGGER.error("Cube file at {} is in the wrong location. (Expected {}, got {})", pos, pos, cubePos);
                    }
                } catch (Exception e) {
                    CubicChunks.LOGGER.warn("Failed to parse cube {} position info", pos, e);
                }

                CompoundTag compoundTag2 = this.upgradeChunkTag(compoundTag);
                ListTag listTag = compoundTag2.getList("Entities", 10);
                List<Entity> list = EntityType.loadEntitiesRecursive(listTag, this.level).collect(ImmutableList.toImmutableList());
                return CompletableFuture.completedFuture(new ChunkEntities(new ImposterChunkPos(pos), list));
            }
            //TODO: Do Async file loading instead.
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ChunkEntities<Entity> emptyCube(CubePos pos) {
        return new ChunkEntities(new ImposterChunkPos(pos), ImmutableList.of());
    }

    @Inject(method = "storeEntities", at = @At("HEAD"), cancellable = true)
    private void storeEntitiesForCube(ChunkEntities<Entity> dataList, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }

        ci.cancel();

        if (!(dataList.getPos() instanceof ImposterChunkPos)) {
            throw new IllegalStateException(dataList.getPos().getClass().getSimpleName() + " was not an instanceOf " + ImposterChunkPos.class.getSimpleName());
        }

        CubePos cubePos = ((ImposterChunkPos) dataList.getPos()).toCubePos();
        if (dataList.isEmpty()) {
            if (this.emptyChunks.add(cubePos.asLong())) {
                this.cubeWorker.saveCubeNBT(cubePos, new CompoundTag());
            }

        } else {
            ListTag listTag = new ListTag();
            dataList.getEntities().forEach((entity) -> {
                CompoundTag compoundTag = new CompoundTag();
                if (entity.save(compoundTag)) {
                    listTag.add(compoundTag);
                }

            });
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
            compoundTag.put("Entities", listTag);
            writeCubePos(compoundTag, cubePos);
            this.cubeWorker.saveCubeNBT(cubePos, compoundTag).exceptionally((throwable) -> {
                CubicChunks.LOGGER.error("Failed to store chunk {}", cubePos, throwable);
                return null;
            });
            this.emptyChunks.remove(cubePos.asLong());
        }
    }


    private static CubePos readCubePos(CompoundTag chunkTag) {
        int[] cubePositions = chunkTag.getIntArray("Position");
        return CubePos.of(cubePositions[0], cubePositions[1], cubePositions[2]);
    }

    private static void writeCubePos(CompoundTag chunkTag, CubePos pos) {
        chunkTag.put("Position", new IntArrayTag(new int[] { pos.getX(), pos.getY(), pos.getZ() }));
    }


}
