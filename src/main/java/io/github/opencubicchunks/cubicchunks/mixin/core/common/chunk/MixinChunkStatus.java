package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import static io.github.opencubicchunks.cubicchunks.chunk.util.Utils.*;
import static net.minecraft.core.Registry.BIOME_REGISTRY;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.chunk.NoiseAndSurfaceBuilderHelper;
import io.github.opencubicchunks.cubicchunks.chunk.biome.ColumnBiomeContainer;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.chunk.util.CCWorldGenUtils;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.config.ChunkGeneratorSettings;
import io.github.opencubicchunks.cubicchunks.config.reloadlisteners.ChunkGeneratorSettingsReloadListener;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkGeneratorAccess;
import io.github.opencubicchunks.cubicchunks.config.ChunkGeneratorSettings;
import io.github.opencubicchunks.cubicchunks.config.reloadlisteners.ChunkGeneratorSettingsReloadListener;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkGeneratorAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.StructureFeatureManagerAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.server.IServerWorldLightManager;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
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
    @SuppressWarnings({ "target", "UnresolvedMixinReference" })
    @Inject(
        method = "lambda$static$2(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true
    )
    private static void generateStructureStatus(
        ChunkStatus status, Executor executor, ServerLevel world, ChunkGenerator generator,
        StructureManager templateManager, ThreadedLevelLightEngine lightManager,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        List<ChunkAccess> chunks, ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            if (!(chunk instanceof IBigCube)) {
                return;
            }

            if (!((IBigCube) chunk).getCubeStatus().isOrAfter(status)) {
                if (chunk instanceof CubePrimer) {
                    ((CubePrimer) chunk).setCubeStatus(status);
                }
            }
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }

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
        method = "lambda$static$3(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksStructureReferences(ChunkStatus status, ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                                       CallbackInfo ci) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            return;
        }

        ci.cancel();
        if (chunk instanceof IBigCube) {
            generator.createReferences(new CubeWorldGenRegion(world, unsafeCast(neighbors), status, chunk, -1), world.structureFeatureManager(), chunk);
        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$4(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksBiome(ChunkStatus status, ServerLevel world, ChunkGenerator chunkGenerator, List<ChunkAccess> neighbors, ChunkAccess chunkAccess, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            if (chunkAccess instanceof IBigCube) {
                return;
            }
            return;
        }

        if (chunkAccess instanceof CubePrimer) {
            ci.cancel();
            CubePrimer cube = ((CubePrimer) chunkAccess);
            cube.setHeightToCubeBounds(true);
            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                    cube.moveColumns(columnX, columnZ);
                    chunkGenerator.createBiomes(world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), chunkAccess);
                }
            }
            cube.setHeightToCubeBounds(false);
            return;
        }
        ci.cancel();
        ColumnBiomeContainer biomeContainer = new ColumnBiomeContainer(world.registryAccess().registryOrThrow(BIOME_REGISTRY), world, world);
        ((ProtoChunk) chunkAccess).setBiomes(biomeContainer);
    }


    // biomes -> handled by MixinChunkGenerator
    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$6(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksNoise(ChunkStatus status, Executor executor, ServerLevel world, ChunkGenerator generator, StructureManager structureFeatureManager,
                                         ThreadedLevelLightEngine lightEngine, Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> function,
                                         List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            if (chunk instanceof IBigCube) {
                ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            }
            return;
        }

        ci.cancel();
        if (chunk instanceof IBigCube) {
            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors), status, chunk, 0);

            CubePos cubePos = ((IBigCube) chunk).getCubePos();
            int cubeY = cubePos.getY();
            CubePrimer cubeAbove = new CubePrimer(CubePos.of(cubePos.getX(), cubeY + 1,
                cubePos.getZ()), UpgradeData.EMPTY, cubeWorldGenRegion);

            CompletableFuture<ChunkAccess> chainedNoiseFutures = null;

            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                    cubeAbove.moveColumns(columnX, columnZ);
                    if (chunk instanceof CubePrimer) {
                        ((CubePrimer) chunk).moveColumns(columnX, columnZ);
                    }

                    ChunkPos pos = chunk.getPos();

                    NoiseAndSurfaceBuilderHelper cubeAccessWrapper = new NoiseAndSurfaceBuilderHelper((IBigCube) chunk, cubeAbove);
                    cubeAccessWrapper.moveColumn(columnX, columnZ);

                    if (chainedNoiseFutures == null) {
                        chainedNoiseFutures = getNoiseSurfaceCarverFuture(executor, world, generator, cubeWorldGenRegion, cubeY, pos, cubeAccessWrapper);
                    } else {
                        // Wait for first completion stage to complete before getting the next future, as it calls supplyAsync internally
                        chainedNoiseFutures =
                            chainedNoiseFutures.thenCompose(futureIn -> getNoiseSurfaceCarverFuture(executor, world, generator, cubeWorldGenRegion, cubeY, pos, cubeAccessWrapper));
                    }
                }
            }
            assert chainedNoiseFutures != null;
            ci.setReturnValue(chainedNoiseFutures.thenApply(ignored -> {
                if (chunk instanceof CubePrimer) {
                    ((CubePrimer) chunk).setCubeStatus(status);
                }

                return Either.left(chunk);
            }));
        } else {
            ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
        }
    }

    private static CompletableFuture<ChunkAccess> getNoiseSurfaceCarverFuture(Executor executor, ServerLevel world, ChunkGenerator generator, CubeWorldGenRegion cubeWorldGenRegion,
                                                                              int cubeY, ChunkPos pos, NoiseAndSurfaceBuilderHelper cubeAccessWrapper) {
        return generator.fillFromNoise(executor, world.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion), cubeAccessWrapper).thenApply(chunkAccess -> {
            cubeAccessWrapper.applySections();
            ChunkGeneratorSettings chunkGeneratorSettings = ChunkGeneratorSettingsReloadListener.CHUNK_GENERATOR_SETTINGS
                .getOrDefault(((ChunkGeneratorAccess) generator).invokeCodec(), ChunkGeneratorSettings.DEFAULT);

            // Exit early and don't waste time on empty sections.
            if (areSectionsEmpty(cubeY, pos, ((NoiseAndSurfaceBuilderHelper) chunkAccess).getDelegateByIndex(0))) {
                return chunkAccess;
            }

            if (chunkGeneratorSettings.controlsStatus(ChunkStatus.SURFACE)) {
                generator.buildSurfaceAndBedrock(cubeWorldGenRegion, chunkAccess);
            } else {
                ((ICubeGenerator) generator).buildSurfaceAndBedrockCC(cubeWorldGenRegion, chunkAccess);
            }

            if (chunkGeneratorSettings.controlsStatus(ChunkStatus.CARVERS)) {
                ((ICubeGenerator) generator).applyCubicCarvers(world.getSeed(), world.getBiomeManager(), cubeAccessWrapper, GenerationStep.Carving.AIR);
            } else {
                generator.applyCarvers(world.getSeed(), world.getBiomeManager(), cubeAccessWrapper, GenerationStep.Carving.AIR);
            }

            if (chunkGeneratorSettings.controlsStatus(ChunkStatus.LIQUID_CARVERS)) {
                ((ICubeGenerator) generator).applyCubicCarvers(world.getSeed(), world.getBiomeManager(), cubeAccessWrapper, GenerationStep.Carving.LIQUID);
            } else {
                generator.applyCarvers(world.getSeed(), world.getBiomeManager(), cubeAccessWrapper, GenerationStep.Carving.LIQUID);
            }
            return chunkAccess;
        });
    }

    private static boolean areSectionsEmpty(int cubeY, ChunkPos pos, IBigCube cube) {
        int emptySections = 0;
        for (int yScan = 0; yScan < IBigCube.DIAMETER_IN_SECTIONS; yScan++) {
            int sectionY = Coords.cubeToSection(cubeY, yScan);
            int sectionIndex = Coords.sectionToIndex(pos.x, sectionY, pos.z);
            LevelChunkSection cubeSection = cube.getCubeSections()[sectionIndex];
            if (LevelChunkSection.isEmpty(cubeSection)) {
                emptySections++;
            }
            if (emptySections == IBigCube.DIAMETER_IN_SECTIONS) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$7(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
            + "Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksSurface(ChunkStatus status, ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                           CallbackInfo ci) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            return;
        }
        ci.cancel();
        //if (chunk instanceof IBigCube) {
        //   generator.generateSurface(new CubeWorldGenRegion(world, unsafeCast(neighbors)), chunk);
        //}
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$8(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksCarvers(ChunkStatus status, ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                           CallbackInfo ci) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            return;
        }

        ci.cancel();
//        if (chunk instanceof IBigCube) {
//            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors), chunk);
//
//            CubePrimer cubeAbove = new CubePrimer(CubePos.of(((IBigCube) chunk).getCubePos().getX(), ((IBigCube) chunk).getCubePos().getY() + 1,
//                ((IBigCube) chunk).getCubePos().getZ()), UpgradeData.EMPTY, cubeWorldGenRegion);
//
//            NoiseAndSurfaceBuilderHelper noiseAndSurfaceBuilderHelper = new NoiseAndSurfaceBuilderHelper((IBigCube) chunk, cubeAbove);
//
//            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
//                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
//                    cubeAbove.moveColumns(columnX, columnZ);
//                    if (chunk instanceof CubePrimer) {
//                        ((CubePrimer) chunk).moveColumns(columnX, columnZ);
//                    }
//                    noiseAndSurfaceBuilderHelper.moveColumn(columnX, columnZ);
//                    noiseAndSurfaceBuilderHelper.applySections();
//                }
//            }
//        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$9(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksLiquidCarvers(ChunkStatus status, ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                                 CallbackInfo ci) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            return;
        }

        ci.cancel();
//        if (chunk instanceof IBigCube) {
//            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors), chunk);
//
//            CubePrimer cubeAbove = new CubePrimer(CubePos.of(((IBigCube) chunk).getCubePos().getX(), ((IBigCube) chunk).getCubePos().getY() + 1,
//                ((IBigCube) chunk).getCubePos().getZ()), UpgradeData.EMPTY, cubeWorldGenRegion);
//
//            NoiseAndSurfaceBuilderHelper noiseAndSurfaceBuilderHelper = new NoiseAndSurfaceBuilderHelper((IBigCube) chunk, cubeAbove);
//
//            //TODO: Verify liquid carvers are generating appropriately
//            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
//                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
//                    cubeAbove.moveColumns(columnX, columnZ);
//                    if (chunk instanceof CubePrimer) {
//                        ((CubePrimer) chunk).moveColumns(columnX, columnZ);
//                    }
//                    noiseAndSurfaceBuilderHelper.moveColumn(columnX, columnZ);
//                    noiseAndSurfaceBuilderHelper.applySections();
//                }
//            }
//        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "lambda$static$10(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "HEAD"), cancellable = true
    )
    private static void featuresSetStatus(
        ChunkStatus status, Executor executor, ServerLevel world, ChunkGenerator generator,
        StructureManager templateManager, ThreadedLevelLightEngine lightManager,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        List<ChunkAccess> chunks, ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (((CubicLevelHeightAccessor) world).generates2DChunks()) {
            if (!(chunk instanceof CubePrimer)) {
                return;
            }

            CubePrimer cubePrimer = (CubePrimer) chunk;
            if (!cubePrimer.getCubeStatus().isOrAfter(status)) {
                cubePrimer.setCubeStatus(status);
            }
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }

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

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(chunks), status, chunk, 1);
            StructureFeatureManager structureFeatureManager =
                new StructureFeatureManager(cubeWorldGenRegion, ((StructureFeatureManagerAccess) world.structureFeatureManager()).getWorldGenSettings());

//            if (cubePrimer.getCubePos().getY() >= 0)
            cubePrimer.applyFeatureStates();
            ((ICubeGenerator) generator).decorate(cubeWorldGenRegion, structureFeatureManager, (CubePrimer) chunk);
            cubePrimer.setCubeStatus(status);
        }
        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
    }

    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private static void lightChunkCC(ChunkStatus status, ThreadedLevelLightEngine lightManager, ChunkAccess chunk,
                                     CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return;
        }

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
        method = "lambda$static$13(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    //TODO: Expose the above and bottom cubes via neighbors or thing else. Check if chunk generator overrides "spawnOriginalMobs" and redirect to our spawner instead.
    private static void cubicChunksSpawnMobs(ChunkStatus status, ServerLevel world, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk,
                                             CallbackInfo ci) {

        if (!((CubicLevelHeightAccessor) world).isCubic()) {
            return;
        }


        ci.cancel();
        if (chunk instanceof IBigCube) {
            int cubeY = ((IBigCube) chunk).getCubePos().getY();

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors), status, chunk, -1);
            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                    cubeWorldGenRegion.moveCenterCubeChunkPos(columnX, columnZ);
                    if (CCWorldGenUtils.areSectionsEmpty(cubeY, chunk.getPos(), (IBigCube) chunk)) {
                        continue;
                    }

                    generator.spawnOriginalMobs(cubeWorldGenRegion);
                }
            }
        }
    }
}
