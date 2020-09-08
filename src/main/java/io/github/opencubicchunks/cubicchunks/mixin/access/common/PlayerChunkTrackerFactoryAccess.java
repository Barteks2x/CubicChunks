package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.server.TicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TicketManager.PlayerChunkTracker.class)
public interface PlayerChunkTrackerFactoryAccess {

    @Invoker("<init>")
    static TicketManager.PlayerChunkTracker construct(TicketManager ticketManager, int p_i50684_2_) {
       throw new Error("Mixin did not apply.");
    }
}