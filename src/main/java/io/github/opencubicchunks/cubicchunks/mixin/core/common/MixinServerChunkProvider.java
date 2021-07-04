package io.github.opencubicchunks.cubicchunks.mixin.core.common;

import static io.github.opencubicchunks.cubicchunks.chunk.util.Utils.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.IChunkManager;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeHolder;
import io.github.opencubicchunks.cubicchunks.chunk.IVerticalView;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubeStatus;
import io.github.opencubicchunks.cubicchunks.chunk.graph.CCTicketType;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.ITicketManager;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkManagerAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.IServerChunkProvider;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubicNaturalSpawner;
import io.github.opencubicchunks.cubicchunks.world.lighting.ICubeLightProvider;
import io.github.opencubicchunks.cubicchunks.world.server.IServerWorld;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkProvider implements IServerChunkProvider, ICubeLightProvider, IVerticalView {
    @Shadow @Final private static List<ChunkStatus> CHUNK_STATUSES;

    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private DistanceManager distanceManager;
    @Shadow @Final private ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    @Shadow @Final private Thread mainThread;

    @Shadow private boolean spawnEnemies;
    @Shadow private boolean spawnFriendlies;

    private final long[] recentCubePositions = new long[4];
    private final ChunkStatus[] recentCubeStatuses = new ChunkStatus[4];
    private final IBigCube[] recentCubes = new IBigCube[4];

    @Shadow protected abstract boolean chunkAbsent(@Nullable ChunkHolder chunkHolderIn, int p_217224_2_);

    @Shadow public abstract int getLoadedChunksCount();

    @Shadow @org.jetbrains.annotations.Nullable protected abstract ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow abstract boolean runDistanceManagerUpdates();

    @Override
    public <T> void addCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value) {
        ((ITicketManager) this.distanceManager).addCubeRegionTicket(type, pos, distance, value);
    }

    public <T> void removeCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value) {
        ((ITicketManager) this.distanceManager).removeCubeRegionTicket(type, pos, distance, value);
    }

    @Override public int getTickingGeneratedCubes() {
        return ((IChunkManager) chunkMap).getTickingGeneratedCubes();
    }

    @Nullable
    @Override
    public IBigCube getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean load) {
        if (Thread.currentThread() != this.mainThread) {
            return CompletableFuture.supplyAsync(() -> {
                return this.getCube(cubeX, cubeY, cubeZ, requiredStatus, load);
            }, this.mainThreadProcessor).join();
        } else {
            ProfilerFiller iprofiler = this.level.getProfiler();
            iprofiler.incrementCounter("getCube");
            long i = CubePos.asLong(cubeX, cubeY, cubeZ);

            for (int j = 0; j < 4; ++j) {
                if (i == this.recentCubePositions[j] && requiredStatus == this.recentCubeStatuses[j]) {
                    IBigCube icube = this.recentCubes[j];
                    if (icube != null || !load) {
                        return icube;
                    }
                }
            }

            iprofiler.incrementCounter("getChunkCacheMiss");
            CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> completablefuture = this.getCubeFutureMainThread(cubeX, cubeY, cubeZ,
                requiredStatus,
                load);
            this.mainThreadProcessor.managedBlock(completablefuture::isDone);
            IBigCube icube = completablefuture.join().map((cube) -> cube, (chunkLoadingFailure) -> {
                if (load) {
                    throw Util.pauseInIde(new IllegalStateException("Cube not there when requested: " + chunkLoadingFailure));
                } else {
                    return null;
                }
            });
            this.storeCubeInCache(i, icube, requiredStatus);
            return icube;
        }
    }

    @Nullable
    public BigCube getCubeNow(int cubeX, int cubeY, int cubeZ) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        } else {
            this.level.getProfiler().incrementCounter("getChunkNow");
            long posAsLong = CubePos.asLong(cubeX, cubeY, cubeZ);

            for (int j = 0; j < 4; ++j) {
                if (posAsLong == this.recentCubePositions[j] && this.recentCubeStatuses[j] == ChunkStatus.FULL) {
                    IBigCube icube = this.recentCubes[j];
                    return icube instanceof BigCube ? (BigCube) icube : null;
                }
            }

            ChunkHolder chunkholder = this.getVisibleCubeIfPresent(posAsLong);
            if (chunkholder == null) {
                return null;
            } else {
                Either<IBigCube, ChunkHolder.ChunkLoadingFailure> either =
                    ((ICubeHolder) chunkholder).getCubeFutureIfPresent(ChunkStatus.FULL).getNow(null);
                if (either == null) {
                    return null;
                } else {
                    IBigCube icube1 = either.left().orElse(null);
                    if (icube1 != null) {
                        this.storeCubeInCache(posAsLong, icube1, ChunkStatus.FULL);
                        if (icube1 instanceof BigCube) {
                            return (BigCube) icube1;
                        }
                    }
                    return null;
                }
            }
        }
    }

    // forceChunk
    @Override
    public void forceCube(CubePos pos, boolean add) {
        ((ITicketManager) this.distanceManager).updateCubeForced(pos, add);
    }


    @Override public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getColumnFutureForCube(CubePos cubepos, int chunkX, int chunkZ, ChunkStatus leastStatus,
                                                                                                                    boolean create) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        long l = chunkPos.toLong();
        int i = 33 + ChunkStatus.getDistance(leastStatus);
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
        if (create) {
            this.distanceManager.addTicket(CCTicketType.CCCOLUMN, chunkPos, i, cubepos);
            if (this.chunkAbsent(chunkHolder, i)) {
                ProfilerFiller profilerFiller = this.level.getProfiler();
                profilerFiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                chunkHolder = this.getVisibleChunkIfPresent(l);
                profilerFiller.pop();
                if (this.chunkAbsent(chunkHolder, i)) {
                    throw Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }
        return this.chunkAbsent(chunkHolder, i) ? ChunkHolder.UNLOADED_CHUNK_FUTURE : chunkHolder.getOrScheduleFuture(leastStatus, this.chunkMap);
    }

    // func_217233_c, getChunkFutureMainThread
    private CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getCubeFutureMainThread(int cubeX, int cubeY, int cubeZ,
                                                                                                         ChunkStatus requiredStatus, boolean load) {
        CubePos cubePos = CubePos.of(cubeX, cubeY, cubeZ);
        long i = cubePos.asLong();
        int j = 33 + CubeStatus.getDistance(requiredStatus);
        ChunkHolder chunkholder = this.getVisibleCubeIfPresent(i);
        if (load) {
            ((ITicketManager) this.distanceManager).addCubeTicket(CCTicketType.CCUNKNOWN, cubePos, j, cubePos);
            if (this.chunkAbsent(chunkholder, j)) {
                ProfilerFiller iprofiler = this.level.getProfiler();
                iprofiler.push("chunkLoad");
                this.runCubeDistanceManagerUpdates();
                chunkholder = this.getVisibleCubeIfPresent(i);
                iprofiler.pop();
                if (this.chunkAbsent(chunkholder, j)) {
                    throw Util.pauseInIde(new IllegalStateException("No cube holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(chunkholder, j) ? ICubeHolder.MISSING_CUBE_FUTURE : ((ICubeHolder) chunkholder).getOrScheduleCubeFuture(requiredStatus,
            this.chunkMap);
    }

    // func_217213_a, getVisibleChunkIfPresent
    @Nullable
    private ChunkHolder getVisibleCubeIfPresent(long cubePosIn) {
        return ((IChunkManager) this.chunkMap).getImmutableCubeHolder(cubePosIn);
    }

    // func_225315_a, storeInCache
    private void storeCubeInCache(long newPositionIn, IBigCube newCubeIn, ChunkStatus newStatusIn) {
        for (int i = 3; i > 0; --i) {
            this.recentCubePositions[i] = this.recentCubePositions[i - 1];
            this.recentCubeStatuses[i] = this.recentCubeStatuses[i - 1];
            this.recentCubes[i] = this.recentCubes[i - 1];
        }

        this.recentCubePositions[0] = newPositionIn;
        this.recentCubeStatuses[0] = newStatusIn;
        this.recentCubes[0] = newCubeIn;
    }

    @Override public void setIncomingVerticalViewDistance(int verticalDistance) {
        ((IVerticalView) this.chunkMap).setIncomingVerticalViewDistance(verticalDistance);
    }

    @Inject(method = "runDistanceManagerUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;clearCache()V"))
    private void onRefreshAndInvalidate(CallbackInfoReturnable<Boolean> cir) {

        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }
        this.clearCubeCache();
    }

    // func_217235_l, runDistanceManagerUpdates
    private boolean runCubeDistanceManagerUpdates() {
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = ((ChunkManagerAccess) this.chunkMap).invokePromoteChunkMap();
        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCubeCache();
            return true;
        }
    }

    private void clearCubeCache() {
        Arrays.fill(this.recentCubePositions, CubicChunks.SECTIONPOS_SENTINEL);
        Arrays.fill(this.recentCubeStatuses, null);
        Arrays.fill(this.recentCubes, null);
    }

    @Override
    @Nullable
    public BlockGetter getCubeForLighting(int sectionX, int sectionY, int sectionZ) {
        long cubePosAsLong = CubePos.of(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY), Coords.sectionToCube(sectionZ)).asLong();
        ChunkHolder chunkholder = ((IChunkManager) this.chunkMap).getImmutableCubeHolder(cubePosAsLong);
        if (chunkholder == null) {
            return null;
        } else {
            int j = CHUNK_STATUSES.size() - 1;

            while (true) {
                ChunkStatus chunkstatus = CHUNK_STATUSES.get(j);
                Optional<IBigCube> optional = ((ICubeHolder) chunkholder).getCubeFutureIfPresentUnchecked(chunkstatus).getNow(ICubeHolder.MISSING_CUBE).left();
                if (optional.isPresent()) {
                    return optional.get();
                }

                if (chunkstatus == ChunkStatus.LIGHT.getParent()) {
                    return null;
                }

                --j;
            }
        }
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void setChunkManagerServerChunkCache(ServerLevel serverLevel, LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer dataFixer, StructureManager structureManager,
                                                 Executor executor, ChunkGenerator chunkGenerator, int i, boolean bl, ChunkProgressListener chunkProgressListener,
                                                 ChunkStatusUpdateListener chunkStatusUpdateListener, Supplier<DimensionDataStorage> supplier, CallbackInfo ci) {
        ((IChunkManager) this.chunkMap).setServerChunkCache((ServerChunkCache) (Object) this);
    }
    /**
     * @author Barteks2x
     * @reason sections
     */
    @Inject(method = "blockChanged", at = @At("RETURN"))
    public void onBlockChanged(BlockPos pos, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }

        ChunkHolder chunkholder = ((IChunkManager) this.chunkMap).getCubeHolder(CubePos.from(pos).asLong());
        if (chunkholder != null) {
            // markBlockChanged
            chunkholder.blockChanged(new BlockPos(Coords.localX(pos), Coords.localY(pos), Coords.localZ(pos)));
        }
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;)"
            + "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
    private NaturalSpawner.SpawnState cubicChunksSpawnState(int spawningChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkSource) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return NaturalSpawner.createState(spawningChunkCount, entities, chunkSource);
        }

        int naturalSpawnCountForColumns = ((ITicketManager) this.distanceManager).getNaturalSpawnCubeCount()
            * IBigCube.DIAMETER_IN_SECTIONS * IBigCube.DIAMETER_IN_SECTIONS / (CubicNaturalSpawner.SPAWN_RADIUS * 2 / IBigCube.DIAMETER_IN_BLOCKS + 1);

        return CubicNaturalSpawner.createState(naturalSpawnCountForColumns, entities, this::getFullCube);
    }

    @Inject(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getChunks()Ljava/lang/Iterable;"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void tickSections(CallbackInfo ci, long l, long timePassed, LevelData levelData, boolean bl, boolean doMobSpawning, int randomTicks, boolean bl3, int j,
                              NaturalSpawner.SpawnState cubeSpawnState) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }

        ((IChunkManager) this.chunkMap).getCubes().forEach((cubeHolder) -> {
            Optional<BigCube> optional =
                ((ICubeHolder) cubeHolder).getCubeEntityTickingFuture().getNow(ICubeHolder.UNLOADED_CUBE).left();
            if (optional.isPresent()) {
                BigCube cube = optional.get();
                this.level.getProfiler().push("broadcast");
                ((ICubeHolder) cubeHolder).broadcastChanges(cube);
                this.level.getProfiler().pop();

                if (!((IChunkManager) this.chunkMap).noPlayersCloseForSpawning(cube.getCubePos())) {
                    // TODO probably want to make sure column-based inhabited time works too
                    cube.setCubeInhabitedTime(cube.getCubeInhabitedTime() + timePassed);

                    if (this.level.random.nextInt(((CubicNaturalSpawner.SPAWN_RADIUS / IBigCube.DIAMETER_IN_BLOCKS) * 2) + 1) == 0) {
                        if (doMobSpawning && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(cube.getCubePos().asChunkPos())) {
                            CubicNaturalSpawner.spawnForCube(this.level, cube, cubeSpawnState, this.spawnFriendlies, this.spawnEnemies, bl3);
                        }
                    }
                    ((IServerWorld) this.level).tickCube(cube, randomTicks);
                }
            }
        });
    }

    private void getFullCube(long pos, Consumer<ChunkAccess> chunkConsumer) {
        ChunkHolder chunkHolder = this.getVisibleCubeIfPresent(pos);
        if (chunkHolder != null) {
            CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>> o = unsafeCast((chunkHolder.getFullChunkFuture()));
            o.getNow(ICubeHolder.UNLOADED_CUBE).left().ifPresent(chunkConsumer);
        }

    }

    @Override public boolean checkCubeFuture(long cubePosLong, Function<ChunkHolder, CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>>> futureFunction) {
        ChunkHolder chunkHolder = this.getVisibleCubeIfPresent(cubePosLong);
        if (chunkHolder == null) {
            return false;
        } else {
            return futureFunction.apply(chunkHolder).getNow(unsafeCast(ChunkHolder.UNLOADED_LEVEL_CHUNK)).left().isPresent();
        }
    }

    /**
     * @author Barteks2x
     * @reason debug string
     */
    @Inject(method = "gatherStats", at = @At("HEAD"), cancellable = true)
    public void gatherStats(CallbackInfoReturnable<String> cir) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }
        cir.setReturnValue("ServerChunkCache: " + this.getLoadedChunksCount() + " | " + ((IChunkManager) chunkMap).sizeCubes());
    }

    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void isTickingCube(long pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }
        cir.setReturnValue(this.checkCubeFuture(pos, (chunkHolder) -> unsafeCast(chunkHolder.getTickingChunkFuture())));
    }

    @Nullable
    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "lambda$onLightUpdate$6(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/LightLayer;)V", at = @At(value = "HEAD"), cancellable = true)
    private void onlyCubes(SectionPos pos, LightLayer type, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.level).isCubic()) {
            return;
        }
        ci.cancel();
        ChunkHolder chunkHolder = this.getVisibleCubeIfPresent(pos.asLong());
        if (chunkHolder != null) {
            chunkHolder.sectionLightChanged(type, Coords.sectionToIndex(pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}