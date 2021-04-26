package io.github.opencubicchunks.cubicchunks.world.storage;

import static net.minecraft.nbt.NbtIo.writeCompressed;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.impl.SaveCubeColumns;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.apache.logging.log4j.Logger;

public class RegionCubeIO {

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final Logger LOGGER = CubicChunks.LOGGER;

    private final File storageFolder;

    private SaveCubeColumns saveCubeColumns;
    private final Map<ChunkPos, SaveEntry> pendingChunkWrites = Maps.newLinkedHashMap();
    private final Map<CubePos, SaveEntry> pendingCubeWrites = Maps.newLinkedHashMap();

    private final DataFixer fixerUpper;
    private final ProcessorMailbox<StrictQueue.IntRunnable> chunkExecutor;
    private final ProcessorMailbox<StrictQueue.IntRunnable> cubeExecutor;

    private final AtomicBoolean shutdownRequested = new AtomicBoolean();

    public RegionCubeIO(File storageFolder, DataFixer dataFixer, @Nullable String chunkWorkerName, String cubeWorkerName) throws IOException {
        this.storageFolder = storageFolder;
        this.fixerUpper = dataFixer;


        this.chunkExecutor = new ProcessorMailbox<>(new StrictQueue.FixedPriorityQueue(Priority.values().length), Util.ioPool(), "RegionCubeIO-" + chunkWorkerName);
        this.cubeExecutor = new ProcessorMailbox<>(new StrictQueue.FixedPriorityQueue(Priority.values().length), Util.ioPool(), "RegionCubeIO-" + cubeWorkerName);

        initSave();
    }

    private void initSave() throws IOException {
        this.saveCubeColumns = SaveCubeColumns.create(storageFolder.toPath());
    }

    private synchronized void closeSave() throws IOException {
        try {
            if (saveCubeColumns != null) {
                this.saveCubeColumns.close();
            }
        } finally {
            this.saveCubeColumns = null;
        }
    }

    @Nonnull
    private synchronized SaveCubeColumns getSave() throws IOException {
        if (saveCubeColumns == null) {
            initSave();
        }
        return saveCubeColumns;
    }


    public void flush() {
        try {
            this.closeSave();
        } catch (IllegalStateException alreadyClosed) {
            // ignore
        } catch (Exception ex) {
            CubicChunks.LOGGER.catching(ex);
        }
    }


    public CompletableFuture<Void> saveCubeNBT(CubePos cubePos, CompoundTag cubeNBT) {
        return this.submitCubeTask(() -> {
            SaveEntry entry = this.pendingCubeWrites.computeIfAbsent(cubePos, (p_235977_1_) -> new SaveEntry(cubeNBT));
            entry.data = cubeNBT;
            return Either.left(entry.result);
        }).thenCompose(Function.identity());
    }

    @Nullable public CompoundTag loadCubeNBT(CubePos cubePos) throws IOException {
        CompletableFuture<CompoundTag> cubeReadFuture = this.submitCubeTask(() -> {
            SaveEntry entry = this.pendingCubeWrites.get(cubePos);

            if (entry != null) {
                return Either.left(entry.data);
            } else {
                try {
                    SaveCubeColumns save = getSave();

                    Optional<ByteBuffer> buf = save.load(new EntryLocation3D(cubePos.getX(), cubePos.getY(), cubePos.getZ()), true);
                    if (!buf.isPresent()) {
                        return Either.left(null);
                    }

                    boolean isOldData = false;
                    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(buf.get().array()))) {
                        // NBT can't begin with byte 255, so this acts as a marker for
                        // 1.12.2 "proto-big-cube" converted data
                        if (in.read() == 255) {
                            // a second byte with value 0 for potential future extension
                            if (in.read() != 0) {
                                throw new RuntimeException("Invalid data, expected 0");
                            }
                            isOldData = true;
                        }
                    }
                    if (isOldData) {
                        return Either.left(loadOldNbt(cubePos, buf.get().array()));
                    }

                    CompoundTag compoundnbt = NbtIo.readCompressed(new ByteArrayInputStream(buf.get().array()));
                    return Either.left(compoundnbt);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read cube {}", cubePos, exception);
                    return Either.right(exception);
                }
            }
        });

        try {
            return cubeReadFuture.join();
        } catch (CompletionException completionexception) {
            if (completionexception.getCause() instanceof IOException) {
                throw (IOException) completionexception.getCause();
            } else {
                throw completionexception;
            }
        }
    }

    @Nullable
    private CompoundTag loadOldNbt(CubePos pos, byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        CompoundTag[] cubeTags = new CompoundTag[8];
        try (InputStream gzip = new BufferedInputStream(new GZIPInputStream(in))) {
            // skip byte 255 and byte 0
            gzip.read();
            gzip.read();
            for (int i = 0; i < 8; i++) {
                cubeTags[i] = NbtIo.read(new DataInputStream(gzip), NbtAccounter.UNLIMITED);
            }
        }

        return mergeOldNbt(pos, cubeTags);
    }

    @Nullable
    private CompoundTag mergeOldNbt(CubePos pos, CompoundTag[] cubeTags) {
        CompoundTag outTag = new CompoundTag();
        for (int i = 0; i < cubeTags.length; i++) {
            CompoundTag level = cubeTags[i].getCompound("Level");
            level.put("TerrainPopulated", level.get("populated"));
            level.put("LightPopulated", level.get("initLightDone"));
            ListTag tileTicks = level.getList("TileTicks", CompoundTag.TAG_COMPOUND);

            // prepare tile ticks to that high bits of "y" coordinate are actually cube section index
            for (Tag tileTick : tileTicks) {
                CompoundTag tick = (CompoundTag) tileTick;
                int x = tick.getInt("x");
                int y = tick.getInt("y");
                int z = tick.getInt("z");
                int idx = Coords.blockToIndex(x, y, z);
                tick.putInt("y", y & 0xF | idx << 4);
            }

            int version = ChunkStorage.getVersion(cubeTags[i]);

            cubeTags[i] = NbtUtils.update(this.fixerUpper, DataFixTypes.CHUNK, cubeTags[i], version, 1493);
            //if (nbt.getCompound("Level").getBoolean("hasLegacyStructureData")) {
            //    if (this.legacyStructureHandler == null) {
            //        this.legacyStructureHandler = LegacyStructureDataHandler.getLegacyStructureHandler(worldKey, (DimensionDataStorage)persistentStateManagerFactory.get());
            //    }
            //    nbt = this.legacyStructureHandler.updateFromLegacy(nbt);
            //}
            cubeTags[i] = NbtUtils.update(this.fixerUpper, DataFixTypes.CHUNK, cubeTags[i], Math.max(1493, version));
            if (cubeTags[i] == null) {
                LOGGER.warn("Dropping incomplete cube at " + pos);
                return null;
            }
        }
        CompoundTag levelOut = new CompoundTag();
        levelOut.putInt("xPos", pos.getX());
        levelOut.putInt("yPos", pos.getY());
        levelOut.putInt("zPos", pos.getZ());

        outTag.put("ToBeTicked", new ListTag());
        outTag.put("Level", levelOut);
        // TODO: biomes
        outTag.putIntArray("Biomes", new int[8 * 8 * 8]);
        for (int i = 0; i < cubeTags.length; i++) {
            CompoundTag cube = cubeTags[i];
            CompoundTag level = cube.getCompound("Level");
            ListTag toBeTicked = cube.getList("ToBeTicked", Tag.TAG_LIST);
            ListTag outToBeTicked = outTag.getList("ToBeTicked", Tag.TAG_LIST);
            for (int i1 = 0; i1 < toBeTicked.size(); i1++) {
                ListTag toTickEntry = toBeTicked.getList(i1);
                ListTag toTickEntryOut = outToBeTicked.getList(i1);
                toTickEntryOut.addAll(toTickEntry);
                outToBeTicked.set(i1, toTickEntryOut);
            }

            level.putLong("LastUpdate", Math.max(level.getLong("LastUpdate"), levelOut.getLong("LastUpdate")));
            level.putLong("InhabitedTime", Math.max(level.getLong("InhabitedTime"), levelOut.getLong("InhabitedTime")));

            ChunkStatus status = ChunkStatus.byName(level.getString("Status"));
            ChunkStatus oldStatus = levelOut.contains("Status") ? ChunkStatus.byName(level.getString("Status")) : null;
            ChunkStatus newStatus = oldStatus == null ? status : oldStatus.isOrAfter(ChunkStatus.SPAWN) ? status : ChunkStatus.EMPTY;
            levelOut.putString("Status", newStatus.getName());

            ListTag sections = levelOut.getList("Sections", Tag.TAG_COMPOUND);
            levelOut.put("Sections", sections);
            CompoundTag section = level.getList("Sections", Tag.TAG_COMPOUND).getCompound(0);
            section.putShort("i", (short) i);
            sections.add(section);

            levelOut.putBoolean("isLightOn", true);

            ListTag tileEntities = levelOut.getList("TileEntities", Tag.TAG_COMPOUND);
            levelOut.put("TileEntities", tileEntities);
            ListTag tileEntitiesOld = level.getList("TileEntities", Tag.TAG_COMPOUND);
            tileEntities.addAll(tileEntitiesOld);

            ListTag entities = levelOut.getList("Entities", Tag.TAG_COMPOUND);
            levelOut.put("Entities", entities);
            ListTag entitiesOld = level.getList("Entities", Tag.TAG_COMPOUND);
            entities.addAll(entitiesOld);

        }
        return outTag;
    }

    public CompoundTag upgradeChunkTag(ResourceKey<Level> worldKey, Supplier<DimensionDataStorage> persistentStateManagerFactory, CompoundTag nbt) {
        int i = ChunkStorage.getVersion(nbt);
        if (i < 1493) {
            throw new IllegalArgumentException("Pre-1.17 version handled elsewhere, but trying " + i);
        }

        nbt = NbtUtils.update(this.fixerUpper, DataFixTypes.CHUNK, nbt, Math.max(1493, i));
        if (i < SharedConstants.getCurrentVersion().getWorldVersion()) {
            nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        }

        return nbt;
    }

    public CompletableFuture<Void> saveChunkNBT(ChunkPos chunkPos, CompoundTag cubeNBT) {
        return this.submitChunkTask(() -> {
            SaveEntry entry = this.pendingChunkWrites.computeIfAbsent(chunkPos, (pos) -> new SaveEntry(cubeNBT));
            entry.data = cubeNBT;
            return Either.left(entry.result);
        }).thenCompose(Function.identity());
    }

    @Nullable public CompoundTag loadChunkNBT(ChunkPos chunkPos) throws IOException {
        CompletableFuture<CompoundTag> cubeReadFuture = this.submitChunkTask(() -> {
            SaveEntry entry = this.pendingChunkWrites.get(chunkPos);

            if (entry != null) {
                return Either.left(entry.data);
            } else {
                try {
                    SaveCubeColumns save = getSave();

                    Optional<ByteBuffer> buf = save.load(new EntryLocation2D(chunkPos.x, chunkPos.z), true);
                    if (!buf.isPresent()) {
                        return Either.left(null);
                    }

                    CompoundTag compoundnbt = NbtIo.readCompressed(new ByteArrayInputStream(buf.get().array()));
                    return Either.left(compoundnbt);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read cube {}", chunkPos, exception);
                    return Either.right(exception);
                }
            }
        });

        try {
            return cubeReadFuture.join();
        } catch (CompletionException completionexception) {
            if (completionexception.getCause() instanceof IOException) {
                throw (IOException) completionexception.getCause();
            } else {
                throw completionexception;
            }
        }
    }

    private void storeCube(CubePos cubePos, SaveEntry entry) {
        try {
            SaveCubeColumns save = getSave();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            writeCompressed(entry.data, outputStream);
            ByteBuffer buf = ByteBuffer.wrap(outputStream.toByteArray());

            save.save3d(new EntryLocation3D(cubePos.getX(), cubePos.getY(), cubePos.getZ()), buf);

            entry.result.complete(null);
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("Failed to store cube {}", cubePos, e);
            entry.result.completeExceptionally(e);
        }
    }

    private void storeChunk(ChunkPos chunkPos, SaveEntry entry) {
        try {
            SaveCubeColumns save = getSave();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            writeCompressed(entry.data, outputStream);
            ByteBuffer buf = ByteBuffer.wrap(outputStream.toByteArray());

            save.save2d(new EntryLocation2D(chunkPos.x, chunkPos.z), buf);

            entry.result.complete(null);
        } catch (IOException e) {
            LOGGER.error("Failed to store chunk {}", chunkPos, e);
            entry.result.completeExceptionally(e);
        }
    }

    private <T> CompletableFuture<T> submitCubeTask(Supplier<Either<T, Exception>> eitherSupplier) {
        return this.cubeExecutor.askEither((taskExecutor) -> new StrictQueue.IntRunnable(Priority.HIGH.ordinal(), () -> {
            if (!this.shutdownRequested.get()) {
                taskExecutor.tell(eitherSupplier.get());
            }
            this.cubeExecutor.tell(new StrictQueue.IntRunnable(Priority.LOW.ordinal(), this::storePendingCube));
        }));
    }

    private <T> CompletableFuture<T> submitChunkTask(Supplier<Either<T, Exception>> eitherSupplier) {
        return this.chunkExecutor.askEither((taskExecutor) -> new StrictQueue.IntRunnable(Priority.HIGH.ordinal(), () -> {
            if (!this.shutdownRequested.get()) {
                taskExecutor.tell(eitherSupplier.get());
            }
            this.chunkExecutor.tell(new StrictQueue.IntRunnable(Priority.LOW.ordinal(), this::storePendingChunk));
        }));
    }

    private void storePendingCube() {
        Iterator<Map.Entry<CubePos, SaveEntry>> iterator = this.pendingCubeWrites.entrySet().iterator();
        if (iterator.hasNext()) {
            Map.Entry<CubePos, SaveEntry> entry = iterator.next();
            iterator.remove();
            this.storeCube(entry.getKey(), entry.getValue());
            this.cubeExecutor.tell(new StrictQueue.IntRunnable(Priority.LOW.ordinal(), this::storePendingCube));
        }
    }

    private void storePendingChunk() {
        Iterator<Map.Entry<ChunkPos, SaveEntry>> iterator = this.pendingChunkWrites.entrySet().iterator();
        if (iterator.hasNext()) {
            Map.Entry<ChunkPos, SaveEntry> entry = iterator.next();
            iterator.remove();
            this.storeChunk(entry.getKey(), entry.getValue());
            this.chunkExecutor.tell(new StrictQueue.IntRunnable(Priority.LOW.ordinal(), this::storePendingChunk));
        }
    }

    static class SaveEntry {
        private CompoundTag data;
        private final CompletableFuture<Void> result = new CompletableFuture<>();

        SaveEntry(CompoundTag p_i231891_1_) {
            this.data = p_i231891_1_;
        }
    }

    private enum Priority {
        HIGH,
        LOW
    }
}