package io.github.opencubicchunks.cubicchunks.network;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class PacketDispatcher {

    private static final String PACKET_LOCATION = CubicChunks.MODID;

    // TODO: network compatibility check on fabric?
    //private static SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
    //        new ResourceLocation(PACKET_LOCATION, "net"),
    //        () -> CubicChunks.PROTOCOL_VERSION,
    //        CubicChunks.PROTOCOL_VERSION::equals, CubicChunks.PROTOCOL_VERSION::equals);;

    private static final Map<Class<?>, BiConsumer<?, FriendlyByteBuf>> ENCODERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ResourceLocation> PACKET_IDS = new ConcurrentHashMap<>();

    public static void register() {
        registerMessage("cubes", PacketCubes.class, PacketCubes::encode,
            PacketCubes::new, PacketCubes.Handler::handle);
        registerMessage("unload", PacketUnloadCube.class, PacketUnloadCube::encode,
            PacketUnloadCube::new, PacketUnloadCube.Handler::handle);
        registerMessage("blocks", PacketCubeBlockChanges.class, PacketCubeBlockChanges::encode,
            PacketCubeBlockChanges::new, PacketCubeBlockChanges.Handler::handle);
        registerMessage("cubepos", PacketUpdateCubePosition.class, PacketUpdateCubePosition::encode,
            PacketUpdateCubePosition::new, PacketUpdateCubePosition.Handler::handle);
        registerMessage("cube_radius", PacketCubeCacheRadius.class, PacketCubeCacheRadius::encode,
            PacketCubeCacheRadius::new, PacketCubeCacheRadius.Handler::handle);
        registerMessage("light", PacketUpdateLight.class, PacketUpdateLight::encode,
            PacketUpdateLight::new, PacketUpdateLight.Handler::handle);
        registerMessage("heightmap", PacketHeightmap.class, PacketHeightmap::encode,
            PacketHeightmap::new, PacketHeightmap.Handler::handle);
        registerMessage("heights", PacketHeightmapChanges.class, PacketHeightmapChanges::encode,
            PacketHeightmapChanges::new, PacketHeightmapChanges.Handler::handle);
        registerMessage("levelinfo", PacketCCLevelInfo.class, PacketCCLevelInfo::encode,
            PacketCCLevelInfo::new, PacketCCLevelInfo.Handler::handle, true);
//                CHANNEL.registerMessage("init", PacketCubicWorldInit.class, PacketCubicWorldInit::encode,
//                        PacketCubicWorldInit::new, PacketCubicWorldInit::handle));
    }

    private static <T> void registerMessage(String id, Class<T> clazz,
                                            BiConsumer<T, FriendlyByteBuf> encode,
                                            Function<FriendlyByteBuf, T> decode,
                                            BiConsumer<T, Level> handler, boolean handleNullLevel) {
        ENCODERS.put(clazz, encode);
        PACKET_IDS.put(clazz, new ResourceLocation(PACKET_LOCATION, id));


        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientProxy.registerClientReceiver(id, decode, handler, handleNullLevel);
        }
    }

    private static <T> void registerMessage(String id, Class<T> clazz,
                                            BiConsumer<T, FriendlyByteBuf> encode,
                                            Function<FriendlyByteBuf, T> decode,
                                            BiConsumer<T, Level> handler) {
        registerMessage(id, clazz, encode, decode, handler, false);
    }

    public static <MSG> void sendTo(MSG packet, ServerPlayer player) {
        ResourceLocation packetId = PACKET_IDS.get(packet.getClass());
        @SuppressWarnings("unchecked")
        BiConsumer<MSG, FriendlyByteBuf> encoder = (BiConsumer<MSG, FriendlyByteBuf>) ENCODERS.get(packet.getClass());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(packet, buf);
        ServerPlayNetworking.send(player, packetId, buf);
    }

    public static <MSG> void sendTo(MSG packet, List<ServerPlayer> players) {
        ResourceLocation packetId = PACKET_IDS.get(packet.getClass());
        @SuppressWarnings("unchecked")
        BiConsumer<MSG, FriendlyByteBuf> encoder = (BiConsumer<MSG, FriendlyByteBuf>) ENCODERS.get(packet.getClass());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(packet, buf);

        players.forEach(player -> {
            if (((CubicLevelHeightAccessor) player.getLevel()).isCubic()) {
                ServerPlayNetworking.send(player, packetId, buf);
            }
        });
    }

    public static class ClientProxy {

        public static <T> void registerClientReceiver(String id, Function<FriendlyByteBuf, T> decode,
                                                      BiConsumer<T, Level> handler, boolean handleNullLevel) {
            ClientPlayNetworking.registerGlobalReceiver(new ResourceLocation(PACKET_LOCATION, id), (client, listener, buf, responseSender) -> {
                buf.retain();
                client.execute(() -> {
                    T packet = decode.apply(buf);
                    ClientLevel level = client.level;
                    if (handleNullLevel || level != null) {
                        try {
                            handler.accept(packet, level);
                        } catch (Throwable throwable) {
                            CubicChunks.LOGGER.error("Packet failed: ", throwable);
                            throw throwable;
                        }
                    }
                    buf.release();
                });
            });
        }
    }
}