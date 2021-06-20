package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.IServerChunkProvider;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// TODO: Verify this is correct.
@Mixin(PortalForcer.class)
public class MixinPortalForcer {

    private static final int Y_PORTAL_RADIUS = 128;

    @Shadow @Final private ServerLevel level;

    @SuppressWarnings("UnresolvedMixinReference")
    @Redirect(method = "lambda$findPortalAround$5(Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;)Lnet/minecraft/BlockUtil$FoundRectangle;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private <T> void addCubeRegionTicket(ServerChunkCache serverChunkCache, TicketType<T> ticketType, ChunkPos chunkPos, int radius, T argument, PoiRecord poiRecord) {
        if (!((CubicLevelHeightAccessor) serverChunkCache.getLevel()).isCubic()) {
            serverChunkCache.addRegionTicket(ticketType, chunkPos, radius, argument);
            return;
        }
        serverChunkCache.addRegionTicket(ticketType, chunkPos, radius, argument); // TODO: Load order?
        ((IServerChunkProvider) serverChunkCache).addCubeRegionTicket(ticketType, new CubePos((BlockPos) argument), Coords.sectionToCube(radius), argument);
    }

    @Redirect(method = "createPortal", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 0))
    private int limitYUp(int a, int b, BlockPos pos, Direction.Axis axis) {
        return !((CubicLevelHeightAccessor) this.level).isCubic() ? Math.min(a, b) : pos.getY() + Y_PORTAL_RADIUS;
    }

    @Redirect(method = "createPortal", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private int limitYDown(int a, int b, BlockPos pos, Direction.Axis axis) {
        return !((CubicLevelHeightAccessor) this.level).isCubic() ? Math.max(a, b) : pos.getY() - Y_PORTAL_RADIUS;
    }

    @Redirect(method = "createPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinBuildHeight()I"))
    private int limitYDown(ServerLevel serverLevel, BlockPos pos, Direction.Axis axis) {
        return !((CubicLevelHeightAccessor) serverLevel).isCubic() ? serverLevel.getMinBuildHeight() : pos.getY() - Y_PORTAL_RADIUS;
    }
}
