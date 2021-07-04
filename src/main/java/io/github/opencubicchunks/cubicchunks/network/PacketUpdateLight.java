package io.github.opencubicchunks.cubicchunks.network;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class PacketUpdateLight {

    private final List<byte[]> skyLightData;
    private final List<byte[]> blockLightData;

    private final BitSet dataExists;

    private final CubePos cubePos;
    private final boolean lightFlag;

    PacketUpdateLight(FriendlyByteBuf buf) {
        this.lightFlag = buf.readBoolean();
        this.cubePos = CubePos.of(buf.readInt(), buf.readInt(), buf.readInt());

        int dataByteCount = MathUtil.ceilDiv(IBigCube.SECTION_COUNT * 2, 8);
        this.dataExists = BitSet.valueOf(buf.readByteArray(dataByteCount));

        this.skyLightData = new ArrayList<>();
        int skyLightDataSize = buf.readInt();
        for (int i = 0; i < skyLightDataSize; i++) {
            this.skyLightData.add(buf.readByteArray(2048));
        }

        this.blockLightData = new ArrayList<>();
        int blockLightDataSize = buf.readInt();
        for (int i = 0; i < blockLightDataSize; i++) {
            this.blockLightData.add(buf.readByteArray(2048));
        }
    }

    public PacketUpdateLight(CubePos pos, LevelLightEngine lightManager, boolean lightFlag) {
        this.cubePos = pos;
        this.lightFlag = lightFlag;
        this.skyLightData = Lists.newArrayList();
        this.blockLightData = Lists.newArrayList();

        this.dataExists = new BitSet(IBigCube.SECTION_COUNT * 2);

        for (int i = 0; i < IBigCube.SECTION_COUNT; ++i) {
            DataLayer skyNibbleArray = lightManager.getLayerListener(LightLayer.SKY).getDataLayerData(Coords.sectionPosByIndex(pos, i));
            DataLayer blockNibbleArray = lightManager.getLayerListener(LightLayer.BLOCK).getDataLayerData(Coords.sectionPosByIndex(pos, i));
            if (skyNibbleArray != null) {
                if (!skyNibbleArray.isEmpty()) {
                    this.dataExists.set(i * 2);
                    this.skyLightData.add(skyNibbleArray.getData().clone());
                }
            }
            if (blockNibbleArray != null) {
                if (!blockNibbleArray.isEmpty()) {
                    this.dataExists.set(i * 2 + 1);
                    this.blockLightData.add(blockNibbleArray.getData().clone());
                }
            }
        }
    }


    void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(lightFlag);
        buf.writeInt(this.cubePos.getX());
        buf.writeInt(this.cubePos.getY());
        buf.writeInt(this.cubePos.getZ());

        byte[] byteArray = new byte[MathUtil.ceilDiv(IBigCube.SECTION_COUNT * 2, 8)];
        byte[] byteArray2 = dataExists.toByteArray();

        System.arraycopy(byteArray2, 0, byteArray, 0, Math.min(byteArray.length, byteArray2.length));

        buf.writeByteArray(byteArray);

        buf.writeInt(this.skyLightData.size());
        for (byte[] array : this.skyLightData) {
            buf.writeByteArray(array);
        }
        buf.writeInt(this.blockLightData.size());
        for (byte[] array : this.blockLightData) {
            buf.writeByteArray(array);
        }
    }

    public static class Handler {
        public static void handle(PacketUpdateLight packet, Level worldIn) {
            if (!(worldIn instanceof ClientLevel)) {
                throw new Error("PacketUpdateLight handle called on server");
            }

            LevelLightEngine worldlightmanager = worldIn.getChunkSource().getLightEngine();

            Iterator<byte[]> skyIterator = packet.skyLightData.iterator();
            Iterator<byte[]> blockIterator = packet.blockLightData.iterator();

            for (int i = 0; i < IBigCube.SECTION_COUNT; ++i) {
                SectionPos sectionPos = Coords.sectionPosByIndex(packet.cubePos, i);

                if (packet.dataExists.get(i * 2)) {
                    worldlightmanager.queueSectionData(LightLayer.SKY, sectionPos, new DataLayer(skyIterator.next()), packet.lightFlag);
                    ((ClientLevel) worldIn).setSectionDirtyWithNeighbors(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
                }
                if (packet.dataExists.get(i * 2 + 1)) {
                    worldlightmanager.queueSectionData(LightLayer.BLOCK, sectionPos, new DataLayer(blockIterator.next()), packet.lightFlag);
                    ((ClientLevel) worldIn).setSectionDirtyWithNeighbors(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
                }
            }
        }
    }
}