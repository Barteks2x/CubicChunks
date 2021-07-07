package io.github.opencubicchunks.cubicchunks.mixin.core.common.server;

import java.util.List;

import io.github.opencubicchunks.cubicchunks.chunk.IVerticalView;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BlockPosAccess;
import io.github.opencubicchunks.cubicchunks.network.PacketBlockPosLongIncorrect;
import io.github.opencubicchunks.cubicchunks.network.PacketCCLevelInfo;
import io.github.opencubicchunks.cubicchunks.network.PacketCubeCacheRadius;
import io.github.opencubicchunks.cubicchunks.network.PacketDispatcher;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList implements IVerticalView {

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private List<ServerPlayer> players;
    private int verticalViewDistance;
    private int incomingVerticalViewDistance;


    @Override public void setIncomingVerticalViewDistance(int verticalDistance) {
        this.incomingVerticalViewDistance = verticalDistance;
    }

    @Override public int getVerticalViewDistance() {
        return this.verticalViewDistance;
    }

    @Inject(method = "setViewDistance", at = @At("HEAD"), cancellable = true)
    private void doVerticalChangesAswell(int viewDistance, CallbackInfo ci) {
        this.verticalViewDistance = incomingVerticalViewDistance;

        PacketDispatcher.sendTo(new PacketCubeCacheRadius(viewDistance, verticalViewDistance), this.players);

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            if (serverLevel != null) {
                if (((CubicLevelHeightAccessor) serverLevel).isCubic()) {
                    ((IVerticalView) serverLevel.getChunkSource()).setIncomingVerticalViewDistance(this.verticalViewDistance);
                }
            }
        }
    }

    @Inject(method = "sendLevelInfo", at = @At("HEAD"))
    private void sendCubeInfo(ServerPlayer player, ServerLevel world, CallbackInfo ci) {
        PacketDispatcher.sendTo(new PacketCCLevelInfo(((CubicLevelHeightAccessor) world).worldStyle()), player);
        PacketDispatcher.sendTo(new PacketBlockPosLongIncorrect(BlockPosAccess.getPackedXLength()), player);
    }

    @Inject(method = "placeNewPlayer", at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundLoginPacket"))
    private void sendCCInfo(Connection connection, ServerPlayer player, CallbackInfo ci) {
    }
}
