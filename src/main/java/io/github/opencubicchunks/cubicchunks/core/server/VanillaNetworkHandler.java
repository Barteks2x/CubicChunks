/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2019 OpenCubicChunks
 *  Copyright (c) 2015-2019 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.core.server;

import static io.github.opencubicchunks.cubicchunks.api.util.Coords.localToBlock;

import gnu.trove.list.TShortList;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.core.common.vanillaclient.ISPacketChunkData;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.core.common.vanillaclient.ISPacketMultiBlockChange;
import io.github.opencubicchunks.cubicchunks.core.util.AddressTools;
import io.github.opencubicchunks.cubicchunks.core.world.cube.Cube;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketUnloadChunk;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VanillaNetworkHandler {
    private static final Set<String> VANILLA_ENTITY_PROPERTIES = new HashSet<>(Arrays.asList(
            "generic.maxHealth",
            "generic.followRange",
            "generic.knockbackResistance",
            "generic.movementSpeed",
            "generic.attackDamage",
            "generic.attackSpeed",
            "generic.armor",
            "generic.armorToughness",
            "generic.luck",
            "generic.flyingSpeed",
            "horse.jumpStrength",
            "zombie.spawnReinforcements"
    ));

    private static final Map<Class<?>, Field[]> packetFields = new HashMap<>();
    private final WorldServer world;
    private Object2IntMap<EntityPlayerMP> playerYOffsets = new Object2IntOpenHashMap<>();

    public VanillaNetworkHandler(WorldServer world) {
        this.world = world;
    }

    // TODO: more efficient way?
    public static Packet<?> copyPacket(Packet<?> packetIn) {
        try {
            Field[] fields = packetFields.computeIfAbsent(packetIn.getClass(), VanillaNetworkHandler::collectFields);
            Constructor<?> constructor = packetIn.getClass().getConstructor();
            Packet<?> newPacket = (Packet<?>) constructor.newInstance();
            for (Field field : fields) {
                Object v = field.get(packetIn);
                field.set(newPacket, v);
            }
            return newPacket;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new Error(e);
        }
    }


    private static Field[] collectFields(Class<?> aClass) {
        return collectFieldList(aClass).toArray(new Field[0]);
    }

    private static List<Field> collectFieldList(Class<?> aClass) {
        List<Field> fields = new ArrayList<>();
        do {
            Field[] f = aClass.getDeclaredFields();
            for (Field field : f) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            aClass = aClass.getSuperclass();
        } while (aClass != Object.class);
        return fields;
    }

    public static boolean hasFML(EntityPlayerMP player) {
        NetHandlerPlayServer connection = player.connection;
        NetworkManager netManager = connection.netManager;
        Channel channel = netManager.channel();
        return channel.attr(NetworkRegistry.FML_MARKER).get();
    }

    public static boolean isVanillaEntityProperty(String propertyName)  {
        return VANILLA_ENTITY_PROPERTIES.contains(propertyName);
    }

    public void sendCubeLoadPackets(Collection<? extends ICube> cubes, EntityPlayerMP player) {
        Map<ChunkPos, List<ICube>> columns = cubes.stream().collect(Collectors.groupingBy(c -> c.getCoords().chunkPos()));
        for (Map.Entry<ChunkPos, List<ICube>> chunkPosListEntry : columns.entrySet()) {
            ChunkPos pos = chunkPosListEntry.getKey();
            List<ICube> column = chunkPosListEntry.getValue();
            SPacketChunkData chunkData = constructChunkData(pos, column, playerYOffsets.getOrDefault(player, 0), world.provider.hasSkyLight());
            player.connection.sendPacket(chunkData);
        }

    }

    public void sendColumnLoadPacket(Chunk chunk, EntityPlayerMP player) {
        player.connection.sendPacket(constructChunkData(chunk));
    }

    public void sendCubeUnloadPacket(CubePos pos, EntityPlayerMP player) {
        // TODO: do we have to do this?
    }

    public void sendColumnUnloadPacket(ChunkPos pos, EntityPlayerMP player) {
        player.connection.sendPacket(new SPacketUnloadChunk(pos.x, pos.z));
    }

    @SuppressWarnings("ConstantConditions")
    public void sendBlockChanges(TShortList dirtyBlocks, Cube cube, EntityPlayerMP player) {
        int yOffset = playerYOffsets.getOrDefault(player, 0);
        int idx = cube.getY() + yOffset;
        if (idx < 0 || idx >= 16) {
            return;
        }
        SPacketMultiBlockChange.BlockUpdateData[] updates = new SPacketMultiBlockChange.BlockUpdateData[dirtyBlocks.size()];
        SPacketMultiBlockChange packet = new SPacketMultiBlockChange();
        for (int i = 0; i < dirtyBlocks.size(); i++) {
            int localAddress = dirtyBlocks.get(i);
            int x = AddressTools.getLocalX(localAddress);
            int localY = AddressTools.getLocalY(localAddress);
            int y = localY + Coords.cubeToMinBlock(idx);
            int z = AddressTools.getLocalZ(localAddress);
            short vanillaPos = (short)(x << 12 | z << 8 | y);
            updates[i] = packet.new BlockUpdateData(vanillaPos,
                    cube.getBlockState(
                            localToBlock(cube.getX(), x),
                            localToBlock(cube.getY(), localY),
                            localToBlock(cube.getZ(), z)));
        }
        ((ISPacketMultiBlockChange) packet).setChangedBlocks(updates);
        ((ISPacketMultiBlockChange) packet).setChunkPos(cube.getCoords().chunkPos());

        player.connection.sendPacket(packet);
    }

    public void updatePlayerPosition(PlayerCubeMap cubeMap, EntityPlayerMP player, int managedPosY) {
        if (!playerYOffsets.containsKey(player)) {
            playerYOffsets.put(player, 0);
        }
        int yOffset = playerYOffsets.get(player);
        if (managedPosY + yOffset < 2 || managedPosY + yOffset >= 14) {
            int newYOffset = 8 - managedPosY;
            playerYOffsets.put(player, newYOffset);
            switchPlayerOffset(cubeMap, player, yOffset, newYOffset);
        }
    }

    public void removePlayer(EntityPlayerMP player) {
        playerYOffsets.remove(player);
    }

    private void switchPlayerOffset(PlayerCubeMap cubeMap, EntityPlayerMP player, int yOffset, int newYOffset) {
        Set<Chunk> toUnload = new HashSet<>();
        List<ICube> cubesToSend = new ArrayList<>();
        for (CubeWatcher cubeWatcher : cubeMap.cubeWatchers) {
            if (!cubeWatcher.isSentToPlayers()) {
                continue;
            }
            toUnload.add(cubeWatcher.getCube().getColumn());
            cubesToSend.add(cubeWatcher.getCube());
        }
        toUnload.forEach(chunk -> sendColumnUnloadPacket(chunk.getPos(), player));
        toUnload.forEach(chunk -> sendColumnLoadPacket(chunk, player));
        player.setPositionAndUpdate(player.posX, player.posY, player.posZ);
        sendCubeLoadPackets(cubesToSend, player);
    }

    private static SPacketChunkData constructChunkData(ChunkPos pos, Iterable<ICube> cubes, int yOffset, boolean hasSkyLight) {
        ICube[] cubesToSend = new ICube[16];
        int mask = 0;
        for (ICube cube : cubes) {
            int idx = cube.getY() + yOffset;
            if (idx < 0 || idx >= 16) {
                continue;
            }
            if (cube.isEmpty()) {
                continue;
            }
            cubesToSend[idx] = cube;
            mask |= 1 << idx;
        }

        SPacketChunkData chunkData = new SPacketChunkData();
        @SuppressWarnings("ConstantConditions")
        ISPacketChunkData dataAccess = (ISPacketChunkData) chunkData;
        dataAccess.setChunkX(pos.x);
        dataAccess.setChunkZ(pos.z);
        dataAccess.setFullChunk(false);
        byte[] dataBuffer = new byte[computeBufferSize(cubesToSend, hasSkyLight, mask)];
        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(dataBuffer));
        buf.writerIndex(0);
        int availableSections = writeData(buf, cubesToSend, hasSkyLight, mask);
        dataAccess.setAvailableSections(availableSections);
        dataAccess.setBuffer(dataBuffer);


        List<NBTTagCompound> teList = new ArrayList<>();
        for (ICube c : cubesToSend) {
            if (c!=null) {
                for (TileEntity value : c.getTileEntityMap().values()) {
                    NBTTagCompound updateTag = value.getUpdateTag();
                    if (updateTag.hasKey("y")) {
                        updateTag.setInteger("y", updateTag.getInteger("y") + Coords.cubeToMinBlock(yOffset));
                    }
                    teList.add(updateTag);
                }
            }
        }
        dataAccess.setTileEntityTags(teList);
        return chunkData;
    }

    private SPacketChunkData constructChunkData(Chunk chunk) {
        SPacketChunkData chunkData = new SPacketChunkData();
        @SuppressWarnings("ConstantConditions")
        ISPacketChunkData dataAccess = (ISPacketChunkData) chunkData;
        dataAccess.setChunkX(chunk.x);
        dataAccess.setChunkZ(chunk.z);
        dataAccess.setAvailableSections(0);
        dataAccess.setFullChunk(true);
        byte[] dataBuffer = new byte[computeBufferSize(chunk)];
        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(dataBuffer));
        buf.writerIndex(0);
        writeData(buf, chunk);
        dataAccess.setBuffer(dataBuffer);
        dataAccess.setTileEntityTags(new ArrayList<>());
        return chunkData;
    }

    private static int computeBufferSize(Chunk chunk) {
        return 256;
    }

    private static void writeData(PacketBuffer buf, Chunk chunk) {
        buf.writeBytes(chunk.getBiomeArray());
    }

    private static int computeBufferSize(ICube[] cubesToSend, boolean hasSkyLight, int mask) {
        int total = 0;
        int cubeCount = cubesToSend.length;

        for (int j = 0; j < cubeCount; j++) {
            ExtendedBlockStorage storage = cubesToSend[j] == null ? null : cubesToSend[j].getStorage();

            if (storage != Chunk.NULL_BLOCK_STORAGE && (mask & 1 << j) != 0) {
                total += storage.getData().getSerializedSize();
                total += storage.getBlockLight().getData().length;

                if (hasSkyLight) {
                    total += storage.getSkyLight().getData().length;
                }
            }
        }

        return total;
    }

    private static int writeData(PacketBuffer buf, ICube[] cubes, boolean hasSkylight, int mask) {
        int sentSections = 0;

        int cubeCount = cubes.length;
        for (int j = 0; j < cubeCount; ++j) {
            ExtendedBlockStorage storage = cubes[j] == null ? null : cubes[j].getStorage();

            if (storage != Chunk.NULL_BLOCK_STORAGE && (mask & 1 << j) != 0) {
                sentSections |= 1 << j;
                storage.getData().write(buf);
                buf.writeBytes(storage.getBlockLight().getData());

                if (hasSkylight) {
                    buf.writeBytes(storage.getSkyLight().getData());
                }
            }
        }

        return sentSections;
    }

    public boolean hasCubicChunks(EntityPlayerMP player) {
        if (!hasFML(player))    {
            return false;
        }
        NetHandlerPlayServer connection = player.connection;
        NetworkManager netManager = connection.netManager;
        Channel channel = netManager.channel();
        Attribute<NetworkDispatcher> attr = channel.attr(NetworkDispatcher.FML_DISPATCHER);
        NetworkDispatcher networkDispatcher = attr.get();
        Map<String, String> modList = networkDispatcher.getModList();
        return modList.containsKey("cubicchunks");
    }

    public BlockPos modifyPositionC2S(BlockPos position, EntityPlayerMP player) {
        return new BlockPos(
                position.getX(),
                position.getY() - Coords.cubeToMinBlock(playerYOffsets.getOrDefault(player, 0)),
                position.getZ()
        );
    }

    public double modifyPositionC2S(double y, EntityPlayerMP player) {
        return y - Coords.cubeToMinBlock(playerYOffsets.getOrDefault(player, 0));
    }

    public int getS2COffset(EntityPlayerMP player) {
        return Coords.cubeToMinBlock(playerYOffsets.getOrDefault(player, 0));
    }
}
