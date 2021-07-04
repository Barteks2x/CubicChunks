package io.github.opencubicchunks.cubicchunks.mixin.debug.common;

import io.github.opencubicchunks.cubicchunks.chunk.graph.CCTicketType;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public class MixinDistanceManager {
    private static final boolean DEBUG_LOAD_ORDER_ENABLED = System.getProperty("cubicchunks.debug.loadorder", "false").equals("true");

    @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"), cancellable = true)
    private void cancelPlayerTickets(long position, Ticket<?> ticket, CallbackInfo ci) {
        if (!DEBUG_LOAD_ORDER_ENABLED) {
            return;
        }
        if (ticket.getType() == TicketType.PLAYER || ticket.getType() == CCTicketType.CCPLAYER) {
            ci.cancel();
        }
    }
}
