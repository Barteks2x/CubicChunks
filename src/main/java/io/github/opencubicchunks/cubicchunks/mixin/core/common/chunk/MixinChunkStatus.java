package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import static io.github.opencubicchunks.cubicchunks.chunk.util.Utils.*;
import static net.minecraft.core.Registry.BIOME_REGISTRY;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.chunk.SectionSizeCubeAccessWrapper;
import io.github.opencubicchunks.cubicchunks.chunk.biome.ColumnBiomeContainer;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.chunk.cube.FillFromNoiseProtoChunkHelper;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.StructureFeatureManagerAccess;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.server.IServerWorldLightManager;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
public class MixinChunkStatus {
    // lambda$static$0 == NOOP_LOADING_WORKER
    // lambda$static$1 == EMPTY
    // lambda$static$2 == STRUCTURE_STARTS
    // lambda$static$3 == STRUCTURE_REFERENCES
    // lambda$static$4 == BIOMES
    // lambda$static$5 == NOISE
    // lambda$static$6 == SURFACE
    // lambda$static$7 == CARVERS
    // lambda$static$8 == LIQUID_CARVERS
    // lambda$static$9 == FEATURES
    // lambda$static$10 == LIGHT
    // lambda$static$11 == LIGHT (loading worker)
    // lambda$static$12 == SPAWM
    // lambda$static$13 == HEIGHTMAPS
    // lambda$static$14 == FULL
    // lambda$static$15 == FULL (loading worker)

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })

    //(Lnet/minecraft/world/chunk/ChunkStatus;Lnet/minecraft/world/server/ServerWorld;Lnet/minecraft/world/gen/feature/template/TemplateManager;
    // Lnet/minecraft/world/server/ServerWorldLightManager;Ljava/util/function/Function;Lnet/minecraft/world/chunk/IChunk;
    // Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V

    @Inject(
        method = "lambda$static$0(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD")
    )
    private static void noopLoadingWorker(
        ChunkStatus status, ServerLevel world, StructureManager templateManager,
        ThreadedLevelLightEngine lightManager,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {

        if (chunk instanceof CubePrimer && !chunk.getStatus().isOrAfter(status)) {
            ((CubePrimer) chunk).setCubeStatus(status);
        }
    }

    // EMPTY -> does nothing already

    // structure starts - replace setStatus, handled by MixinChunkGenerator
    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$2(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;"
            + "Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true
    )
    private static void generateStructureStatus(
        ChunkStatus status, ServerLevel world, ChunkGenerator generator,
        StructureManager templateManager, ThreadedLevelLightEngine lightManager,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        List<ChunkAccess> chunks, ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
        if (!(chunk instanceof IBigCube)) {
            //vanilla
            return;
        }
        //cc
        if (!((IBigCube) chunk).getCubeStatus().isOrAfter(status)) {
            if (world.getServer().getWorldData().worldGenSettings().generateFeatures()) { // check if structures are enabled
                // structureFeatureManager ==  getStructureManager?
                generator.createStructures(world.registryAccess(), world.structureFeatureManager(), chunk, templateManager, world.getSeed());
            }

            if (chunk instanceof CubePrimer) {
                ((CubePrimer) chunk).setCubeStatus(status);
            }
        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$3(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksStructureReferences(ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                                       CallbackInfo ci) {

        ci.cancel();
        if (chunk instanceof IBigCube) {
            generator.createReferences(new CubeWorldGenRegion(world, unsafeCast(neighbors)), world.structureFeatureManager(), chunk);
        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$4(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksBiome(ServerLevel serverLevel, ChunkGenerator chunkGenerator, List<ChunkAccess> neighbors, ChunkAccess chunkAccess, CallbackInfo ci) {
        if (chunkAccess instanceof IBigCube) {
            chunkGenerator.createBiomes(serverLevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), chunkAccess);
            return;
        }
        ci.cancel();
        //noinspection ConstantConditions
        ColumnBiomeContainer biomeContainer = new ColumnBiomeContainer(serverLevel.registryAccess().registryOrThrow(BIOME_REGISTRY), serverLevel, serverLevel);
        ((ProtoChunk) chunkAccess).setBiomes(biomeContainer);
    }

    // biomes -> handled by MixinChunkGenerator
    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$5(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksNoise(ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                         CallbackInfo ci) {
        ci.cancel();
        if (chunk instanceof IBigCube) {
            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors));

            StructureFeatureManager structureFeatureManager =
                new StructureFeatureManager(cubeWorldGenRegion, ((StructureFeatureManagerAccess) world.structureFeatureManager()).getWorldGenSettings());

            CubePrimer cubeAbove = new CubePrimer(CubePos.of(((IBigCube) chunk).getCubePos().getX(), ((IBigCube) chunk).getCubePos().getY() + 1,
                ((IBigCube) chunk).getCubePos().getZ()), UpgradeData.EMPTY, cubeWorldGenRegion);


            FillFromNoiseProtoChunkHelper chunkHelper = new FillFromNoiseProtoChunkHelper(((IBigCube) chunk).getCubePos().asChunkPos(0, 0),
                UpgradeData.EMPTY,
                world,
                (CubePrimer) chunk);


            FillFromNoiseProtoChunkHelper cubeAboveChunkHelper = new FillFromNoiseProtoChunkHelper(cubeAbove.getCubePos().asChunkPos(0, 0),
                UpgradeData.EMPTY,
                world, cubeAbove);

            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                    chunkHelper.moveColumn(columnX, columnZ);
                    cubeAboveChunkHelper.moveColumn(columnX, columnZ);

                    generator.fillFromNoise(cubeWorldGenRegion, structureFeatureManager, chunkHelper);
                    generator.buildSurfaceAndBedrock(cubeWorldGenRegion, chunkHelper);
                }
            }
        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$6(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksSurface(ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                           CallbackInfo ci) {

        ci.cancel();
        //if (chunk instanceof IBigCube) {
        //   generator.generateSurface(new CubeWorldGenRegion(world, unsafeCast(neighbors)), chunk);
        //}
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$7(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksCarvers(ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                           CallbackInfo ci) {

        ci.cancel();
        //if (chunk instanceof IBigCube) {
        //    generator.func_225550_a_(world.getBiomeManager().copyWithProvider(generator.getBiomeProvider()), chunk, GenerationStage.Carving.AIR);
        //}
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$8(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksLiquidCarvers(ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                                 CallbackInfo ci) {

        ci.cancel();
        //if (chunk instanceof IBigCube) {
        //    generator.func_225550_a_(world.getBiomeManager().copyWithProvider(generator.getBiomeProvider()), chunk, GenerationStage.Carving.LIQUID);
        //}
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$9(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;"
            + "Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "HEAD"), cancellable = true
    )
    private static void featuresSetStatus(
        ChunkStatus status, ServerLevel world, ChunkGenerator generator,
        StructureManager templateManager, ThreadedLevelLightEngine lightManager,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        List<ChunkAccess> chunks, ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (!(chunk instanceof CubePrimer)) {
            // cancel column population for now
            ((ProtoChunk) chunk).setStatus(status);
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }
        CubePrimer cubePrimer = (CubePrimer) chunk;
        cubePrimer.setCubeLightManager(lightManager);
        if (!cubePrimer.getCubeStatus().isOrAfter(status)) {
            // TODO: reimplement heightmaps
            //Heightmap.updateChunkHeightmaps(chunk, EnumSet
            //        .of(Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Heightmap.Type.OCEAN_FLOOR,
            //        Heightmap.Type.WORLD_SURFACE));

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(chunks));
            StructureFeatureManager structureFeatureManager =
                new StructureFeatureManager(cubeWorldGenRegion, ((StructureFeatureManagerAccess) world.structureFeatureManager()).getWorldGenSettings());

//            if (cubePrimer.getCubePos().getY() >= 0)
            ((ICubeGenerator) generator).decorate(cubeWorldGenRegion, structureFeatureManager);
            cubePrimer.setCubeStatus(status);
        }
        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
    }

    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private static void lightChunkCC(ChunkStatus status, ThreadedLevelLightEngine lightManager, ChunkAccess chunk,
                                     CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if (!(chunk instanceof CubePrimer)) {
            if (!chunk.getStatus().isOrAfter(status)) {
                ((ProtoChunk) chunk).setStatus(status);
            }
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }
        boolean flag = ((CubePrimer) chunk).getCubeStatus().isOrAfter(status) && ((CubePrimer) chunk).hasCubeLight();
        if (!chunk.getStatus().isOrAfter(status)) {
            ((CubePrimer) chunk).setCubeStatus(status);
        }
        cir.setReturnValue(unsafeCast(((IServerWorldLightManager) lightManager).lightCube((IBigCube) chunk, flag).thenApply(Either::left)));
    }

    //lambda$static$12
    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$12(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksSpawnMobs(ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                             CallbackInfo ci) {

        ci.cancel();
        //if (chunk instanceof IBigCube) {
        //    generator.spawnMobs(new CubeWorldGenRegion(world, unsafeCast(neighbors)));
        //}
    }
}