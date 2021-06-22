package io.github.opencubicchunks.cubicchunks.mixin.core.common.entity;

import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.server.IServerChunkProvider;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow public Level level;

    @Shadow public abstract void teleportTo(double destX, double destY, double destZ);

    @Shadow public abstract int getId();

    @Inject(method = "teleportToWithTicket", at = @At(value = "HEAD"), cancellable = true)
    private void cubeTeleportWithTicket(double destX, double destY, double destZ, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) this.level).isCubic() && this.level instanceof ServerLevel) {
            int cubeX = Coords.blockToCube(destX);
            int cubeY = Coords.blockToCube(destY);
            int cubeZ = Coords.blockToCube(destZ);
            ((IServerChunkProvider) ((ServerLevel) this.level).getChunkSource()).addCubeRegionTicket(TicketType.POST_TELEPORT, CubePos.of(cubeX, cubeY, cubeZ), 0, this.getId());
            ((ICubicWorld) this.level).getCube(cubeX, cubeY, cubeZ);
            this.teleportTo(destX, destY, destZ);
            ci.cancel();
        }
    }
}