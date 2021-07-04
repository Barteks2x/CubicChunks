package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import static io.github.opencubicchunks.cubicchunks.chunk.util.Utils.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.CubePlayerProvider;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.IChunkManager;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeHolder;
import io.github.opencubicchunks.cubicchunks.chunk.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimerWrapper;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkManagerAccess;
import io.github.opencubicchunks.cubicchunks.network.PacketCubeBlockChanges;
import io.github.opencubicchunks.cubicchunks.network.PacketDispatcher;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
public abstract class MixinChunkHolder implements ICubeHolder {

    @Shadow private int ticketLevel;
    @Mutable @Final @Shadow private ChunkPos pos;

    // these are using java type erasure as a feature - because the generic type information
    // doesn't exist at runtime, we can shadow those fields with different generic types
    // and as long as we are consistent, we can use them with different types than the declaration in the original class
    @Shadow @Final private AtomicReferenceArray<CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>>> futures;
    @Shadow private volatile CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>> tickingChunkFuture;
    @Shadow private volatile CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>> entityTickingChunkFuture;
    @Shadow private CompletableFuture<IBigCube> chunkToSave;


    @Shadow @Final private BitSet skyChangedLightSectionFilter;
    @Shadow @Final private BitSet blockChangedLightSectionFilter;
    @Shadow private int queueLevel;

    @Shadow @Final private ChunkHolder.PlayerProvider playerProvider;
    @Shadow private CompletableFuture<Void> pendingFullStateConfirmation;
    @Shadow @Final private LevelHeightAccessor levelHeightAccessor;

    @Shadow private boolean hasChangedSections;

    private CubePos cubePos; // set from ASM

    private final ShortArraySet[] changedLocalBlocks = new ShortArraySet[IBigCube.SECTION_COUNT];

    //@formatter:off SPLITTING THIS LINE BREAKS MIXIN https://github.com/SpongePowered/Mixin/issues/418
    @SuppressWarnings("LineLengthCode") private final AtomicReferenceArray<ArrayList<BiConsumer<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>, Throwable>>> listenerLists = new AtomicReferenceArray<>(ChunkStatus.getStatusList().size());
    //@formatter:on

    @Shadow protected abstract void broadcastBlockEntity(Level worldIn, BlockPos posIn);

    @Shadow protected abstract void updateChunkToSave(
        CompletableFuture<? extends Either<? extends ChunkAccess, ChunkHolder.ChunkLoadingFailure>> eitherChunk, String string);

    @Shadow public static ChunkStatus getStatus(int level) {
        throw new Error("Mixin failed to apply");
    }

    @Shadow public abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getFutureIfPresentUnchecked(ChunkStatus p_219301_1_);

    @Shadow public abstract CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getOrScheduleFuture(ChunkStatus chunkStatus, ChunkMap chunkManager);

    @Shadow @Nullable public abstract LevelChunk getTickingChunk();

    //BEGIN INJECTS:

    // targetting <init>* seems to break when running with gradle for the copied constructor


    @Shadow protected abstract void broadcastBlockEntityIfNeeded(Level world, BlockPos pos, BlockState state);

    @Dynamic
    @Redirect(
        method = "<init>(Lio/github/opencubicchunks/cubicchunks/chunk/util/CubePos;ILnet/minecraft/world/level/LevelHeightAccessor;"
            + "Lnet/minecraft/world/level/lighting/LevelLightEngine;Lnet/minecraft/server/level/ChunkHolder$LevelChangeListener;"
            + "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"))
    private int getFakeSectionCountCC(LevelHeightAccessor accessor) {
        if (!((CubicLevelHeightAccessor) accessor).isCubic()) {
            return accessor.getSectionsCount();
        }

        return 0;
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;"
            + "Lnet/minecraft/world/level/lighting/LevelLightEngine;Lnet/minecraft/server/level/ChunkHolder$LevelChangeListener;"
            + "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"))
    private int getFakeSectionCountVanilla(LevelHeightAccessor accessor) {
        if (!((CubicLevelHeightAccessor) accessor).isCubic()) {
            return accessor.getSectionsCount();
        }
        return 0;
    }

    // target generated by ASM
    @SuppressWarnings("target")
    @Dynamic("Generated by ASM by copying and transforming original constructor")
    @Inject(method = "<init>(Lio/github/opencubicchunks/cubicchunks/chunk/util/CubePos;I"
        + "Lnet/minecraft/world/level/LevelHeightAccessor;"
        + "Lnet/minecraft/world/level/lighting/LevelLightEngine;"
        + "Lnet/minecraft/server/level/ChunkHolder$LevelChangeListener;"
        + "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;)V",
        at = @At("RETURN")
    )
    public void onConstructCubeHolder(CubePos cubePosIn, int levelIn, LevelHeightAccessor heightAccessor, LevelLightEngine lightManagerIn, ChunkHolder.LevelChangeListener p_i50716_4_,
                                      ChunkHolder.PlayerProvider playerProviderIn, CallbackInfo ci) {

        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) { //TODO: Vanilla Chunks - Figure out how to handle ASM Targets
            return;
        }

        this.pos = cubePosIn.asChunkPos();
    }

    // used from ASM
    private static ChunkStatus getCubeStatus(int cubeLevel) {
        return ICubeHolder.getCubeStatusFromLevel(cubeLevel);
    }

    @Inject(method = "updateFutures", at = @At("HEAD"), cancellable = true)
    void updateFutures(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {

        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }
        /*
         If sectionPos == null, this is a ChunkManager
         else, this is a CubeManager.
         This is being implemented as a mixin, instead of having a specific CubeManager class.
         this.sectionPos is essentially being used as a flag for changing behaviour.
         */
        if (this.cubePos == null) {
            return;
        }
        ci.cancel();
        updateCubeFutures(chunkMap, executor);
    }

    @Redirect(method = "scheduleFullChunkPromotion", at = @At(
        value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> onRunAsyncFullChunkStatusChange(CompletableFuture<?> completableFuture, Runnable action, Executor executor,
                                                                    ChunkMap chunkMap, CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> completableFutureParam,
                                                                    Executor executorParam,
                                                                    ChunkHolder.FullChunkStatus fullChunkStatus) {

        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return completableFuture.thenRunAsync(action, executor);
        }

        if (cubePos == null) {
            return completableFuture.thenRunAsync(action, executor);
        }
        return completableFuture.thenRunAsync(() -> {
            ((ChunkManagerAccess) chunkMap).invokeOnFullChunkStatusChange(new ImposterChunkPos(this.cubePos), fullChunkStatus);
        }, executor);
    }

    @Redirect(method = "demoteFullChunk", at = @At(value = "FIELD",
        target = "Lnet/minecraft/server/level/ChunkHolder;pos:Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos chunkPos(ChunkHolder holder) {
        return (this.cubePos != null) ? new ImposterChunkPos(this.cubePos) : this.pos;
    }

    @Redirect(method = "scheduleFullChunkPromotion", at = @At(
        value = "INVOKE",
        target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> onCompleteScheduleFullChunkPromotion(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> completableFuture,
                                                                         Consumer<? super Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> action) {

        CompletableFuture<Void> f2 = pendingFullStateConfirmation;
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return completableFuture.thenAccept(action);
        }


        if (cubePos == null) {
            return completableFuture.thenAccept(action);
        }
        //noinspection unchecked
        return ((CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>>) unsafeCast(completableFuture)).thenAccept((either) -> {
            either.ifLeft((BigCube levelChunk) -> {
                f2.complete(null);
            });
        });
    }

    @Inject(method = "demoteFullChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;onFullChunkStatusChange"
        + "(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/ChunkHolder$FullChunkStatus;)V"), cancellable = true)
    private void onDemoteFullChunk(ChunkMap chunkMap, ChunkHolder.FullChunkStatus fullChunkStatus, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }

        if (cubePos != null) {
            ci.cancel();
        }
    }

    @Override
    public CubePos getCubePos() {
        return cubePos;
    }

    // getChunkIfComplete, TODO: rename to getTickingCube
    @Nullable
    @Override
    public BigCube getCubeIfComplete() {
        CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>> completablefuture = this.tickingChunkFuture;
        Either<BigCube, ChunkHolder.ChunkLoadingFailure> either = completablefuture.getNow(null);
        return either == null ? null : either.left().orElse(null);
    }

    @Override
    public CompletableFuture<Either<BigCube, ChunkHolder.ChunkLoadingFailure>> getCubeEntityTickingFuture() {
        return this.entityTickingChunkFuture;
    }

    // func_219301_a, getFutureIfPresentUnchecked
    @Override
    public CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getCubeFutureIfPresentUnchecked(ChunkStatus chunkStatus) {
        return unsafeCast(getFutureIfPresentUnchecked(chunkStatus));
    }

    // func_219302_f, getChunkToSave
    @Override
    public CompletableFuture<IBigCube> getCubeToSave() {
        return chunkToSave;
    }

    // func_225410_b, getFutureIfPresent
    @Override public CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getCubeFutureIfPresent(ChunkStatus chunkStatus) {
        return ICubeHolder.getCubeStatusFromLevel(this.ticketLevel).isOrAfter(chunkStatus) ?
            unsafeCast(this.getFutureIfPresentUnchecked(chunkStatus)) : // getFutureIfPresentUnchecked = getFutureByCubeStatus
            MISSING_CUBE_FUTURE;
    }

    // func_219276_a, getOrScheduleFuture
    @Redirect(method = "getOrScheduleFuture", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkHolder;getStatus(I)Lnet/minecraft/world/level/chunk/ChunkStatus;"
    ))
    private ChunkStatus getChunkOrCubeStatus(int level) {

        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return getStatus(level);
        }

        if (cubePos == null) {
            return getStatus(level);
        } else {
            return getCubeStatus(level);
        }
    }

    @Redirect(method = "getOrScheduleFuture", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkMap;schedule(Lnet/minecraft/server/level/ChunkHolder;Lnet/minecraft/world/level/chunk/ChunkStatus;)"
            + "Ljava/util/concurrent/CompletableFuture;"
    ))
    private CompletableFuture<?> scheduleChunkOrCube(ChunkMap chunkManager, ChunkHolder _this, ChunkStatus status) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic() || cubePos == null) {
            return chunkManager.schedule(_this, status);
        } else {
            return ((IChunkManager) chunkManager).scheduleCube(_this, status);
        }
    }

    // func_219276_a, getOrScheduleFuture
    @Override public CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> getOrScheduleCubeFuture(ChunkStatus chunkStatus, ChunkMap chunkManager) {
        return getOrScheduleFuture(chunkStatus, chunkManager);
    }

    public void addCubeStageListener(ChunkStatus status, BiConsumer<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>, Throwable> consumer, ChunkMap chunkManager) {
        CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> future = getOrScheduleFuture(status, chunkManager);

        if (future.isDone()) {
            consumer.accept(future.getNow(null), null);
        } else {
            List<BiConsumer<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>, Throwable>> listenerList = this.listenerLists.get(status.getIndex());
            if (listenerList == null) {

                final ArrayList<BiConsumer<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>, Throwable>> listeners = new ArrayList<>();
                future.whenComplete((either, throwable) -> {
                    for (BiConsumer<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>, Throwable> listener : listeners) {
                        listener.accept(either, throwable);
                    }
                    listeners.clear();
                    listeners.trimToSize();
                });
                this.listenerLists.set(status.getIndex(), listeners);
                listenerList = listeners;
            }

            listenerList.add(consumer);
        }
    }


    // TODO: this needs to be completely replaced for proper section handling

    /**
     * @author Barteks2x**
     * @reason height limits
     */
    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    public void blockChanged(BlockPos blockPos, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }
        int sectionIDX = Coords.blockToIndex(blockPos);

        ci.cancel();

        int localX = blockPos.getX() & 0xF;
        int localZ = blockPos.getZ() & 0xF;

        if (cubePos == null) {
            ChunkAccess chunk = getTickingChunk();
            if (chunk == null) {
                return;
            }
            for (Heightmap.Types value : Heightmap.Types.values()) {
                if (!value.sendToClient()) {
                    continue;
                }
                int topY = chunk.getHeight(value, blockPos.getX(), blockPos.getZ()) - 1;
                // if the block being changed is new top block - heightmap probably was updated
                // if block being changed is above new top block - heightmap was probably decreased
                // TODO: replace heuristics with proper tracking
                if (blockPos.getY() >= topY) {
                    // TODO: don't use heightmap type as "height" for address

                    if (this.changedLocalBlocks[sectionIDX] == null) {
                        this.changedLocalBlocks[sectionIDX] = new ShortArraySet();
                    }
                    changedLocalBlocks[sectionIDX].add(SectionPos.sectionRelativePos(blockPos));
                }
            }
            int topY = ((LightHeightmapGetter) chunk).getLightHeightmap().getFirstAvailable(localX, localZ) - 1;
            // if the block being changed is new top block - heightmap probably was updated
            // if block being changed is above new top block - heightmap was probably decreased
            // TODO: replace heuristics with proper tracking
            if (blockPos.getY() >= topY) {
                // TODO: don't use heightmap type as "height" for address

                if (this.changedLocalBlocks[sectionIDX] == null) {
                    this.changedLocalBlocks[sectionIDX] = new ShortArraySet();
                }
                changedLocalBlocks[sectionIDX].add(SectionPos.sectionRelativePos(blockPos));
            }
            return;
        }

        BigCube cube = getCubeIfComplete();
        if (cube == null) {
            return;
        }

        if (this.changedLocalBlocks[sectionIDX] == null) {
            this.changedLocalBlocks[sectionIDX] = new ShortArraySet();
        }

        this.hasChangedSections = true;

        this.changedLocalBlocks[sectionIDX].add(SectionPos.sectionRelativePos(blockPos));
    }

    /**
     * @author Barteks2x
     * @reason replace packet classes with CC packets
     */
    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void broadcastCubeChanges(LevelChunk levelChunk, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }
        ci.cancel();


        if (cubePos != null) {
            throw new IllegalStateException("Why is this getting called?");
        }

        for (ShortArraySet changedLocalBlock : changedLocalBlocks) {
            if (changedLocalBlock == null || changedLocalBlock.isEmpty()) {
                continue;
            }

            ChunkAccess chunk = this.getTickingChunk();
//            this.sendToTrackingColumn(new PacketHeightmapChanges(chunk, new ShortArrayList(changedLocalBlock)), false); //TODO: Fix sending heightmap
            //java.lang.NullPointerException: Exception ticking world
            //at net.minecraft.world.level.chunk.LevelChunk.getHeight(LevelChunk.java:339)
            //at io.github.opencubicchunks.cubicchunks.network.PacketHeightmapChanges.<init>(PacketHeightmapChanges.java:30)
            //at net.minecraft.server.level.ChunkHolder.handler$zze000$broadcastCubeChanges(ChunkHolder.java:1435)
            //at net.minecraft.server.level.ChunkHolder.broadcastChanges(ChunkHolder.java)
            //at net.minecraft.server.level.ServerChunkCache.lambda$tickChunks$5(ServerChunkCache.java:367)


            changedLocalBlock.clear();
        }
    }

    @Inject(method = "sectionLightChanged", at = @At("HEAD"), cancellable = true)
    private void cubeSectionlightChanged(LightLayer lightType, int i, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) this.levelHeightAccessor).isCubic()) {
            return;
        }

        ci.cancel();
        if (cubePos == null) {
            return;
        }

        BigCube levelChunk = getCubeIfComplete();
        if (levelChunk != null) {
            levelChunk.setUnsaved(true);
            if (lightType == LightLayer.SKY) {
                this.skyChangedLightSectionFilter.set(i);
            } else {
                this.blockChangedLightSectionFilter.set(i);
            }

        }
    }

    @Override
    public void broadcastChanges(BigCube cube) {
        if (cubePos == null) {
            throw new IllegalStateException("broadcastChanges(BigCube) called on column holder!");
        }
        if (!hasChangedSections && this.skyChangedLightSectionFilter.isEmpty() && this.blockChangedLightSectionFilter.isEmpty()) {
            return;
        }
        Level world = cube.getLevel();

        //TODO: Broadcast light engine changes.
        // if (this.skyLightChangeMask != 0 || this.blockLightChangeMask != 0) {
        //     this.sendToTracking(new SUpdateLightPacket(section.getPos(), this.lightManager, this.skyLightChangeMask & ~this.boundaryMask,
        //             this.blockLightChangeMask & ~this.boundaryMask), true);
        //     int i = this.skyLightChangeMask & this.boundaryMask;
        //     int j = this.blockLightChangeMask & this.boundaryMask;
        //     if (i != 0 || j != 0) {
        //         this.sendToTracking(new SUpdateLightPacket(section.getPos(), this.lightManager, i, j), false);
        //     }
        //     this.skyLightChangeMask = 0;
        //     this.blockLightChangeMask = 0;
        //     this.boundaryMask &= ~(this.skyLightChangeMask & this.blockLightChangeMask);
        // }


        for (int idx = 0; idx < changedLocalBlocks.length; idx++) {
            ShortArraySet changedPositions = changedLocalBlocks[idx];
            if (changedPositions == null) {
                continue;
            }

            SectionPos sectionPos = Coords.sectionPosByIndex(this.cubePos, idx);
            int changedBlocks = changedPositions.size();

            if (changedBlocks != 0) {
                this.sendToTracking(new PacketCubeBlockChanges(cube, sectionPos, new ShortArrayList(changedPositions)), false);
                for (short changed : changedPositions) {
                    BlockPos changedPos = sectionPos.relativeToBlockPos(changed);
                    broadcastBlockEntityIfNeeded(world, changedPos, world.getBlockState(changedPos));
                }
            }
            changedPositions.clear();
        }
        this.hasChangedSections = false;

        //FORGE:
//        if (changedPositions.size() >= net.minecraftforge.common.ForgeConfig.SERVER.clumpingThreshold.get()) {
//            this.queueLevel = -1;// boundaryMask
//        }
//
//        if (changedBlocks >= net.minecraftforge.common.ForgeConfig.SERVER.clumpingThreshold.get()) {
//            this.sendToTracking(new PacketCubes(Collections.singletonList(cube)), false);
//        }
    }

    private void sendToTracking(Object packetIn, boolean boundaryOnly) {
        ((CubePlayerProvider) this.playerProvider).getPlayers(this.cubePos, boundaryOnly)
            .forEach(player -> PacketDispatcher.sendTo(packetIn, player));
    }

    private void sendToTrackingColumn(Object packetIn, boolean boundaryOnly) {
        this.playerProvider.getPlayers(this.pos, boundaryOnly)
            .forEach(player -> PacketDispatcher.sendTo(packetIn, player));
    }

    // func_219294_a, replaceProtoChunk
    @Override
    public void replaceProtoCube(CubePrimerWrapper primer) {
        for (int i = 0; i < this.futures.length(); ++i) {
            CompletableFuture<Either<IBigCube, ChunkHolder.ChunkLoadingFailure>> future = this.futures.get(i);
            if (future != null) {
                Optional<IBigCube> optional = future.getNow(MISSING_CUBE).left();
                if (optional.isPresent() && optional.get() instanceof CubePrimer) {
                    this.futures.set(i, CompletableFuture.completedFuture(Either.left(primer)));
                }
            }
        }

        this.updateChunkToSave(unsafeCast(CompletableFuture.completedFuture(Either.left((IBigCube) primer.getCube()))), "replaceProtoCube");
    }
}