package io.github.opencubicchunks.cubicchunks.world.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.impl.SaveCubeColumns;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.nbt.NbtIo.writeCompressed;

public class RegionCubeIO {

    private static final long kB = 1024;
    private static final long MB = kB * 1024;
    private static final Logger LOGGER = CubicChunks.LOGGER;

    @Nonnull private final Level world;
    private final File storageFolder;

    private SaveCubeColumns saveCubeColumns;
    private final Map<ChunkPos, SaveEntry> pendingChunkWrites = Maps.newLinkedHashMap();
    private final Map<CubePos, SaveEntry> pendingCubeWrites = Maps.newLinkedHashMap();

    private final ProcessorMailbox<StrictQueue.IntRunnable> chunkExecutor;
    private final ProcessorMailbox<StrictQueue.IntRunnable> cubeExecutor;

    private final AtomicBoolean shutdownRequested = new AtomicBoolean();

    public RegionCubeIO(Level world, File storageFolder) throws IOException {
        this.world = world;
        this.storageFolder = storageFolder;

        this.chunkExecutor = new ProcessorMailbox<>(new StrictQueue.FixedPriorityQueue(Priority.values().length), Util.ioPool(), "RegionCubeIO-chunk");
        this.cubeExecutor = new ProcessorMailbox<>(new StrictQueue.FixedPriorityQueue(Priority.values().length), Util.ioPool(), "RegionCubeIO-cube");

        initSave();
    }

    private void initSave() throws IOException {
        File file;
        if (world instanceof ServerLevel) {
            file = storageFolder;
        } else {
            //TODO: implement client world
            throw new IOException("NOT IMPLEMENTED");
            //            Path path = Paths.get(".").toAbsolutePath().resolve("clientCache").resolve("DIM" + world.dimension());
        }
        this.saveCubeColumns = SaveCubeColumns.create(file.toPath());
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
                    if(!buf.isPresent())
                        return Either.left(null);

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
                throw (IOException)completionexception.getCause();
            } else {
                throw completionexception;
            }
        }
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
                    if(!buf.isPresent())
                        return Either.left(null);

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
                throw (IOException)completionexception.getCause();
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
        } catch (IOException e) {
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

        public SaveEntry(CompoundTag p_i231891_1_) {
            this.data = p_i231891_1_;
        }
    }

    private enum Priority {
        HIGH,
        LOW
    }
}