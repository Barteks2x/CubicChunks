package io.github.opencubicchunks.cubicchunks.chunk.graph;

import java.util.Comparator;

import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.TicketTypeAccess;
import net.minecraft.server.level.TicketType;

public class CCTicketType {
    public static final TicketType<CubePos> CCPLAYER = create("player", Comparator.comparingLong(CubePos::asLong));
    public static final TicketType<CubePos> CCFORCED = create("forced", Comparator.comparingLong(CubePos::asLong));
    public static final TicketType<CubePos> CCLIGHT = create("light", Comparator.comparingLong(CubePos::asLong));
    public static final TicketType<CubePos> CCUNKNOWN = create("unknown", Comparator.comparingLong(CubePos::asLong), 1);

    public static final TicketType<CubePos> CCCOLUMN = create("column", Comparator.comparingLong(CubePos::asLong));


    public static <T> TicketType<T> create(String nameIn, Comparator<T> comparator) {
        return TicketTypeAccess.createNew(nameIn, comparator, 0L);
    }

    public static <T> TicketType<T> create(String nameIn, Comparator<T> comparator, int lifespanIn) {
        return TicketTypeAccess.createNew(nameIn, comparator, lifespanIn);
    }
}