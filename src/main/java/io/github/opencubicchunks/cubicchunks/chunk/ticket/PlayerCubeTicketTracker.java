package io.github.opencubicchunks.cubicchunks.chunk.ticket;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.graph.CCTicketType;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.TicketAccess;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.Ticket;

public class PlayerCubeTicketTracker extends PlayerCubeTracker {
    private int viewDistance;
    private final Long2IntMap distances = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
    private final LongSet positionsAffected = new LongOpenHashSet();
    private final ITicketManager iTicketManager;


    public PlayerCubeTicketTracker(ITicketManager iTicketManager, int i) {
        //possibly make this a constant - there is only ever one playercubeticketracker at a time, so this should be fine.
        super(iTicketManager, (32 / IBigCube.DIAMETER_IN_SECTIONS) + 1);
        this.iTicketManager = iTicketManager;
        this.viewDistance = 0;
        this.distances.defaultReturnValue(i + 2);
    }

    protected void chunkLevelChanged(long cubePosIn, int oldLevel, int newLevel) {
        this.positionsAffected.add(cubePosIn);
    }

    public void updateCubeViewDistance(int viewDistanceIn) {
        for (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry entry : this.cubesInRange.long2ByteEntrySet()) {
            byte b0 = entry.getByteValue();
            long i = entry.getLongKey();
            this.updateTicket(i, b0, this.isWithinViewDistance(b0), b0 <= viewDistanceIn - 2);
        }

        this.viewDistance = viewDistanceIn;
    }

    // func_215504_a, onLevelChange
    private void updateTicket(long cubePosIn, int distance, boolean oldWithinViewDistance, boolean withinViewDistance) {
        if (oldWithinViewDistance != withinViewDistance) {
            Ticket<?> ticket = TicketAccess.createNew(CCTicketType.CCPLAYER, ITicketManager.PLAYER_CUBE_TICKET_LEVEL, CubePos.from(cubePosIn));
            if (withinViewDistance) {
                iTicketManager.getCubeTicketThrottlerInput().tell(CubeTaskPriorityQueueSorter.createMsg(() ->
                    iTicketManager.getMainThreadExecutor().execute(() -> {
                        if (this.isWithinViewDistance(this.getLevel(cubePosIn))) {
                            iTicketManager.addCubeTicket(cubePosIn, ticket);
                            iTicketManager.getCubeTicketsToRelease().add(cubePosIn);
                        } else {
                            iTicketManager.getCubeTicketThrottlerReleaser().tell(CubeTaskPriorityQueueSorter.createSorterMsg(() -> {
                            }, cubePosIn, false));
                        }

                    }), cubePosIn, () -> distance));
            } else {
                iTicketManager.getCubeTicketThrottlerReleaser().tell(CubeTaskPriorityQueueSorter.createSorterMsg(() ->
                        iTicketManager.getMainThreadExecutor().execute(() ->
                            iTicketManager.removeCubeTicket(cubePosIn, ticket)),
                    cubePosIn, true));
            }
        }

    }

    public void processAllUpdates() {
        super.processAllUpdates();
        if (!this.positionsAffected.isEmpty()) {
            LongIterator longiterator = this.positionsAffected.iterator();

            while (longiterator.hasNext()) {
                long i = longiterator.nextLong();
                int j = this.distances.get(i);
                int k = this.getLevel(i);
                if (j != k) {
                    iTicketManager.getCubeTicketThrottler().onCubeLevelChange(CubePos.from(i), () -> this.distances.get(i), k, (ix) -> {
                        if (ix >= this.distances.defaultReturnValue()) {
                            this.distances.remove(i);
                        } else {
                            this.distances.put(i, ix);
                        }

                    });
                    this.updateTicket(i, k, this.isWithinViewDistance(j), this.isWithinViewDistance(k));
                }
            }

            this.positionsAffected.clear();
        }

    }

    private boolean isWithinViewDistance(int p_215505_1_) {
        return p_215505_1_ <= this.viewDistance - 2;
    }
}