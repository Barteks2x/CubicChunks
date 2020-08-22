package io.github.opencubicchunks.cubicchunks.mixin.core.common.ticket;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.IChunkManager;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeHolder;
import io.github.opencubicchunks.cubicchunks.chunk.cube.BigCube;
import io.github.opencubicchunks.cubicchunks.chunk.graph.CCTicketType;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.CubeTaskPriorityQueueSorter;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.CubeTicketTracker;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.ITicketManager;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.PlayerCubeTicketTracker;
import io.github.opencubicchunks.cubicchunks.chunk.ticket.PlayerCubeTracker;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkHolderAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.TicketAccess;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.concurrent.ITaskExecutor;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.Ticket;
import net.minecraft.world.server.TicketManager;
import net.minecraft.world.server.TicketType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(TicketManager.class)
public abstract class MixinTicketManager implements ITicketManager {
    private final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> cubeTickets = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<ObjectSet<ServerPlayerEntity>> playersByCubePos = new Long2ObjectOpenHashMap<>();

    private final Set<ChunkHolder> cubeHolders = new HashSet<>();
    private final LongSet cubePositions = new LongOpenHashSet();


    //@Final @Shadow private Long2ObjectMap<ObjectSet<ServerPlayerEntity>> playersByChunkPos;
    //@Final @Shadow private LongSet chunkPositions;
    @Final @Shadow private Executor field_219388_p;
    @Shadow private long currentTime;
    //@Final @Shadow private Set<ChunkHolder> chunkHolders;

    // getLevel
    @Shadow private static int getLevel(SortedArraySet<Ticket<?>> p_229844_0_) {
        throw new Error("Mixin did not apply correctly");
    }

    @Shadow protected abstract void register(long chunkPosIn, Ticket<?> ticketIn);

    private final CubeTicketTracker cubeTicketTracker = new CubeTicketTracker(this);
    private final PlayerCubeTracker playerCubeTracker = new PlayerCubeTracker(this, 8);
    private final PlayerCubeTicketTracker playerCubeTicketTracker = new PlayerCubeTicketTracker(this, 33);
    private CubeTaskPriorityQueueSorter cubeTaskPriorityQueueSorter;
    private ITaskExecutor<CubeTaskPriorityQueueSorter.FunctionEntry<Runnable>> playerCubeTicketThrottler;
    private ITaskExecutor<CubeTaskPriorityQueueSorter.RunnableEntry> playerCubeTicketThrottlerSorter;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void init(Executor executor, Executor executor2, CallbackInfo ci) {
        ITaskExecutor<Runnable> itaskexecutor = ITaskExecutor.inline("player ticket throttler", executor2::execute);
        CubeTaskPriorityQueueSorter
                cubeTaskPriorityQueueSorter = new CubeTaskPriorityQueueSorter(ImmutableList.of(itaskexecutor), executor, 4);
        this.cubeTaskPriorityQueueSorter = cubeTaskPriorityQueueSorter;
        this.playerCubeTicketThrottler = cubeTaskPriorityQueueSorter.createExecutor(itaskexecutor, true);
        this.playerCubeTicketThrottlerSorter = cubeTaskPriorityQueueSorter.createSorterExecutor(itaskexecutor);
    }

    @Override
    public void registerCube(long cubePosIn, Ticket<?> ticketIn) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getCubeTicketSet(cubePosIn);
        int i = getLevel(sortedarrayset);
        Ticket<?> ticket = sortedarrayset.func_226175_a_(ticketIn);
        ((TicketAccess) ticket).setTimestampCC(this.currentTime);
        if (ticketIn.getLevel() < i) {
            this.cubeTicketTracker.updateSourceLevel(cubePosIn, ticketIn.getLevel(), true);
        }
    }

    @Override
    public void releaseCube(long cubePosIn, Ticket<?> ticketIn) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getCubeTicketSet(cubePosIn);
        sortedarrayset.remove(ticketIn);

        if (sortedarrayset.isEmpty()) {
            this.cubeTickets.remove(cubePosIn);
        }

        this.cubeTicketTracker.updateSourceLevel(cubePosIn, getLevel(sortedarrayset), false);
        // TODO: release chunk tickets when releasing cube tickets
    }

    private SortedArraySet<Ticket<?>> getCubeTicketSet(long cubePos) {
        return this.cubeTickets.computeIfAbsent(cubePos, (p_229851_0_) -> SortedArraySet.newSet(4));
    }

    @Inject(method = "setViewDistance", at = @At("HEAD"))
    protected void setViewDistance(int viewDistance, CallbackInfo ci)
    {
        this.playerCubeTicketTracker.setViewDistance(Coords.sectionToCubeRenderDistance(viewDistance));
    }

    //BEGIN INJECT

    @Inject(method = "processUpdates", at = @At("RETURN"), cancellable = true)
    public void processUpdates(ChunkManager chunkManager, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        // Minecraft.getInstance().getIntegratedServer().getProfiler().startSection("cubeTrackerUpdates");
        this.playerCubeTracker.processAllUpdates();
        // Minecraft.getInstance().getIntegratedServer().getProfiler().endStartSection("cubeTicketTrackerUpdates");
        this.playerCubeTicketTracker.processAllUpdates();
        // Minecraft.getInstance().getIntegratedServer().getProfiler().endStartSection("cubeTicketTracker");
        int i = Integer.MAX_VALUE - this.cubeTicketTracker.update(Integer.MAX_VALUE);
        // Minecraft.getInstance().getIntegratedServer().getProfiler().endStartSection("cubeHolderTick");
        boolean flag = i != 0;
        if (!this.cubeHolders.isEmpty()) {
            this.cubeHolders.forEach((cubeHolder) -> ((ChunkHolderAccess) cubeHolder).processUpdatesCC(chunkManager));
            this.cubeHolders.clear();
            callbackInfoReturnable.setReturnValue(true);
            //Minecraft.getInstance().getIntegratedServer().getProfiler().endSection();// cubeHolderTick
            return;
        } else {
            if (!this.cubePositions.isEmpty()) {
                LongIterator longiterator = this.cubePositions.iterator();

                while (longiterator.hasNext()) {
                    long j = longiterator.nextLong();
                    if (this.getCubeTicketSet(j).stream().anyMatch((p_219369_0_) -> p_219369_0_.getType() == CCTicketType.CCPLAYER)) {
                        ChunkHolder chunkholder = ((IChunkManager) chunkManager).getCubeHolder(j);
                        if (chunkholder == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<Either<BigCube, ChunkHolder.IChunkLoadingError>> sectionEntityTickingFuture =
                                ((ICubeHolder)chunkholder).getCubeEntityTickingFuture();
                        sectionEntityTickingFuture.thenAccept((p_219363_3_) -> this.field_219388_p.execute(() -> {
                            this.playerCubeTicketThrottlerSorter.enqueue(CubeTaskPriorityQueueSorter.createSorterMsg(() -> {
                            }, j, false));
                        }));
                    }
                }
                this.cubePositions.clear();
            }
            callbackInfoReturnable.setReturnValue(flag | callbackInfoReturnable.getReturnValueZ());
        }
        //Minecraft.getInstance().getIntegratedServer().getProfiler().endSection();// cubeHolderTick
    }

    //BEGIN OVERWRITE

    /**
     * @author NotStirred
     * @reason CC must also update it's tracker&tickets
     */
    @Inject(method = "tick", at = @At("RETURN"))
    protected void tickSection(CallbackInfo ci) {
        ObjectIterator<Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>>> objectiterator = this.cubeTickets.long2ObjectEntrySet().fastIterator();
        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry = objectiterator.next();
            if (entry.getValue().removeIf((ticket) -> ((TicketAccess) ticket).cc$isexpired(this.currentTime))) {
                this.cubeTicketTracker.updateSourceLevel(entry.getLongKey(), getLevel(entry.getValue()), false);
            }
            if (entry.getValue().isEmpty()) {
                objectiterator.remove();
            }
        }
    }

    //BEGIN OVERRIDES
    @Override
    public <T> void registerWithLevel(TicketType<T> type, CubePos pos, int level, T value) {
        this.registerCube(pos.asLong(), new Ticket<>(type, level, value));
    }

    @Override
    public <T> void releaseWithLevel(TicketType<T> type, CubePos pos, int level, T value) {
        Ticket<T> ticket = new Ticket<>(type, level, value);
        this.releaseCube(pos.asLong(), ticket);
    }

    @Override
    public <T> void register(TicketType<T> type, CubePos pos, int distance, T value) {
        this.registerCube(pos.asLong(), new Ticket<>(type, 33 - distance, value));
    }

    @Override
    public <T> void release(TicketType<T> type, CubePos pos, int distance, T value) {
        Ticket<T> ticket = new Ticket<>(type, 33 - distance, value);
        this.releaseCube(pos.asLong(), ticket);
    }

    // forceChunk
    @Override
    public void forceCube(CubePos pos, boolean add) {
        Ticket<CubePos> ticket = new Ticket<>(CCTicketType.CCFORCED, 31, pos);
        if (add) {
            this.registerCube(pos.asLong(), ticket);
        } else {
            this.releaseCube(pos.asLong(), ticket);
        }
    }

    @Override
    public void updateCubePlayerPosition(CubePos cubePos, ServerPlayerEntity player) {
        long i = cubePos.asLong();
        this.playersByCubePos.computeIfAbsent(i, (x) -> new ObjectOpenHashSet<>()).add(player);
        this.playerCubeTracker.updateSourceLevel(i, 0, true);
        this.playerCubeTicketTracker.updateSourceLevel(i, 0, true);
    }

    @Override
    public void removeCubePlayer(CubePos cubePosIn, ServerPlayerEntity player) {
        long i = cubePosIn.asLong();
        ObjectSet<ServerPlayerEntity> objectset = this.playersByCubePos.get(i);
        objectset.remove(player);
        if (objectset.isEmpty()) {
            this.playersByCubePos.remove(i);
            this.playerCubeTracker.updateSourceLevel(i, Integer.MAX_VALUE, false);
            this.playerCubeTicketTracker.updateSourceLevel(i, Integer.MAX_VALUE, false);
        }
    }

    /**
     * Returns the number of chunks taken into account when calculating the mob cap
     */
    @Override
    public int getSpawningCubeCount() {
        this.playerCubeTracker.processAllUpdates();
        return this.playerCubeTracker.cubesInRange.size();
    }

    @Override
    public boolean isCubeOutsideSpawningRadius(long cubePosIn) {
        this.playerCubeTracker.processAllUpdates();
        return this.playerCubeTracker.cubesInRange.containsKey(cubePosIn);
    }

    @Override
    public Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getCubeTickets() {
        return cubeTickets;
    }

    @Override
    public ITaskExecutor<CubeTaskPriorityQueueSorter.FunctionEntry<Runnable>> getCubePlayerTicketThrottler() {
        return playerCubeTicketThrottler;
    }

    @Override
    public ITaskExecutor<CubeTaskPriorityQueueSorter.RunnableEntry> getPlayerCubeTicketThrottlerSorter() { return playerCubeTicketThrottlerSorter; }

    @Override
    public LongSet getCubePositions() {
        return cubePositions;
    }

    @Override
    public Set<ChunkHolder> getCubeHolders()
    {
        return this.cubeHolders;
    }

    @Override
    public Executor executor() {
        return field_219388_p;
    }

    @Override
    public CubeTaskPriorityQueueSorter getCubeTaskPriorityQueueSorter() {
        return cubeTaskPriorityQueueSorter;
    }

    @Override
    public Long2ObjectMap<ObjectSet<ServerPlayerEntity>> getPlayersByCubePos()
    {
        return this.playersByCubePos;
    }
}