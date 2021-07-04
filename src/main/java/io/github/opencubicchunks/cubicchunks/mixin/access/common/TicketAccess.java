package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Ticket.class)
public interface TicketAccess {
    @Invoker("<init>") static <T> Ticket<T> createNew(TicketType<T> ticketType, int i, T object) {
        throw new Error("Mixin did not apply");
    }
    @Invoker boolean invokeTimedOut(long currentTime);
    @Invoker void invokeSetCreatedTick(long time);

    @Accessor <T> T getKey();
}