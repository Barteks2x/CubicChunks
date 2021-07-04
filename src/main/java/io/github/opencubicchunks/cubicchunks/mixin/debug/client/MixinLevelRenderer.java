package io.github.opencubicchunks.cubicchunks.mixin.debug.client;

import java.lang.reflect.Field;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.client.CubicWorldLoadScreen;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    private static final boolean DEBUG_STATUS_RENDERER = System.getProperty("cubicchunks.debug.statusrenderer", "false").equalsIgnoreCase("true");

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;"
        + "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V"))
    public void render(PoseStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                       Matrix4f matrix4f, CallbackInfo ci) {
        if (!DEBUG_STATUS_RENDERER) {
            return;
        }

        ServerLevel levelAccessor = Minecraft.getInstance().getSingleplayerServer().getLevel(Level.OVERWORLD);
        RenderSystem.disableBlend();
        RenderSystem.disableTexture();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y;
        double cameraZ = camera.getPosition().z;
        BlockPos blockPos = new BlockPos(cameraX, cameraY, cameraZ);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        Object2IntMap<ChunkStatus> colors = getField(
            CubicWorldLoadScreen.class, null, "STATUS_COLORS" // TODO: intermediary name
        );

        int renderRadius = 5;
        int chunkRenderRadius = renderRadius * IBigCube.DIAMETER_IN_SECTIONS;
        Long2ObjectLinkedOpenHashMap<ChunkHolder> loadedColumns = getField(ChunkMap.class, levelAccessor.getChunkSource().chunkMap, "updatingChunkMap");

        Object[] data = getField(Long2ObjectLinkedOpenHashMap.class, loadedColumns, "value");
        long[] keys = getField(Long2ObjectLinkedOpenHashMap.class, loadedColumns, "key");
        for (int i = 0, keysLength = keys.length; i < keysLength; i++) {
            long pos = keys[i];
            ChunkPos chunkPos = new ChunkPos(pos);

            if (Math.abs((blockPos.getX() >> 4) - chunkPos.x) > chunkRenderRadius || Math.abs((blockPos.getZ() >> 4) - chunkPos.z) > chunkRenderRadius) {
                continue;
            }

            if (pos == 0) {
                continue;
            }
            ChunkHolder holder = (ChunkHolder) data[i];
            if (holder == null) {
                continue;
            }
            ChunkStatus status = holder.getLastAvailableStatus();

            int color = colors.getOrDefault(status, 0);

            Vector3f vector3f = new Vector3f(((color >> 16) & 0xff) / 256f, ((color >> 8) & 0xff) / 256f, (color & 0xff) / 256f); //this.getColor(types);

            int xPos = SectionPos.sectionToBlockCoord(chunkPos.x, 0);
            int zPos = SectionPos.sectionToBlockCoord(chunkPos.z, 0);
            LevelRenderer.addChainedFilledBoxVertices(bufferBuilder, xPos + 4.25F - cameraX, 108 - cameraY, zPos + 4.25F - cameraZ, xPos + 11.75F - cameraX, 108 - cameraY + 0.09375F,
                zPos + 11.75F - cameraZ, vector3f.x(), vector3f.y(), vector3f.z(), 1.0F);
        }

        Long2ObjectLinkedOpenHashMap<ChunkHolder> loadedCubes = getField(ChunkMap.class, levelAccessor.getChunkSource().chunkMap, "updatingCubeMap");

        data = getField(Long2ObjectLinkedOpenHashMap.class, loadedCubes, "value");
        keys = getField(Long2ObjectLinkedOpenHashMap.class, loadedCubes, "key");
        for (int i = 0, keysLength = keys.length; i < keysLength; i++) {
            long pos = keys[i];
            CubePos cubePos = CubePos.from(pos);

            int cubeX = cubePos.getX();
            int cubeY = cubePos.getY();
            int cubeZ = cubePos.getZ();

            if (Math.abs(Coords.blockToCube(blockPos.getX()) - cubeX) > renderRadius
                || Math.abs(Coords.blockToCube(blockPos.getY()) - cubeY) > renderRadius
                || Math.abs(Coords.blockToCube(blockPos.getZ()) - cubeZ) > renderRadius) {
                continue;
            }

            if (pos == 0) {
                continue;
            }
            ChunkHolder holder = (ChunkHolder) data[i];
            if (holder == null) {
                continue;
            }
            ChunkStatus status = holder.getLastAvailableStatus();

            int color = colors.getOrDefault(status, 0);

            Vector3f vector3f = new Vector3f(((color >> 16) & 0xff) / 256f, ((color >> 8) & 0xff) / 256f, (color & 0xff) / 256f); //this.getColor(types);

            int xPos = Coords.cubeToMinBlock(cubeX);
            int yPos = Coords.cubeToMinBlock(cubeY);
            int zPos = Coords.cubeToMinBlock(cubeZ);
            drawBox(bufferBuilder, cameraX, cameraY, cameraZ, xPos + 16, yPos + 16, zPos + 16, 4, vector3f);
        }

        tesselator.end();
        RenderSystem.enableTexture();
    }

    private void drawBox(BufferBuilder bufferBuilder, double cameraX, double cameraY, double cameraZ, float x, float y, float z, float radius, Vector3f color) {
        LevelRenderer.addChainedFilledBoxVertices(bufferBuilder, x - radius - cameraX, y - radius - cameraY, z - radius - cameraZ,
            x + radius - cameraX, y + radius - cameraY, z + radius - cameraZ, color.x(), color.y(), color.z(), 1.0F);
    }

    private static <T> T getField(Class<?> cl, Object obj, String name) {
        try {
            Field f = cl.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
