package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import java.util.List;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;
import io.github.opencubicchunks.cubicchunks.chunk.carver.CubicCarvingContext;
import io.github.opencubicchunks.cubicchunks.chunk.cube.CubePrimer;
import io.github.opencubicchunks.cubicchunks.chunk.util.CCWorldGenUtils;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.OverworldBiomeSourceAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRandom;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.ICubicStructureStart;
import io.github.opencubicchunks.cubicchunks.world.biome.BiomeGetter;
import io.github.opencubicchunks.cubicchunks.world.biome.StripedBiomeSource;
import io.github.opencubicchunks.cubicchunks.world.gen.structure.ICubicConfiguredStructureFeature;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.data.worldgen.StructureFeatures;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator implements ICubeGenerator {

    @Mutable @Shadow @Final protected BiomeSource biomeSource;
    @Mutable @Shadow @Final protected BiomeSource runtimeBiomeSource;

    @Shadow @Final private StructureSettings settings;
    @Shadow @Final private List<ChunkPos> strongholdPositions;

    @Shadow protected abstract void generateStrongholds();

    @Inject(at = @At("RETURN"),
        method = "<init>(Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/StructureSettings;J)V")
    private void switchBiomeSource(BiomeSource biomeSourceIn, BiomeSource biomeSourceIn2, StructureSettings structureSettings, long l, CallbackInfo ci) {
        if (System.getProperty("cubicchunks.debug.biomes", "false").equalsIgnoreCase("true")) {
            if (this.biomeSource instanceof OverworldBiomeSource) {
                this.biomeSource = new StripedBiomeSource(((OverworldBiomeSourceAccess) this.biomeSource).getBiomes());
            }
            if (this.runtimeBiomeSource instanceof OverworldBiomeSource) {
                this.runtimeBiomeSource = new StripedBiomeSource(((OverworldBiomeSourceAccess) this.runtimeBiomeSource).getBiomes());
            }
        }
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, SectionPos section,
                                 StructureManager structureManager, long worldSeed) {
        Biome biome = this.biomeSource.getPrimaryBiome(chunkAccess.getPos());
        this.createCcStructure(StructureFeatures.STRONGHOLD, registryAccess, structureFeatureManager, chunkAccess, section, structureManager, worldSeed, biome);

        for (Supplier<ConfiguredStructureFeature<?, ?>> supplier : biome.getGenerationSettings().structures()) {
            this.createCcStructure(supplier.get(), registryAccess, structureFeatureManager, chunkAccess, section, structureManager, worldSeed, biome);
        }
    }

    public void createCcStructure(ConfiguredStructureFeature<?, ?> structure, RegistryAccess registryAccess, StructureFeatureManager structureFeatureManager,
                                  ChunkAccess chunk, SectionPos pos, StructureManager structureManager, long worldSeed, Biome biome) {
        StructureStart<?> previousStart = structureFeatureManager.getStartForFeature(pos, structure.feature, chunk);
        // TODO: is this right?
        int referenceCount = previousStart != null ? previousStart.getReferences() : 0;
        StructureFeatureConfiguration config = this.settings.getConfig(structure.feature);

        if (config != null) {
            StructureStart<?> newStart = ((ICubicConfiguredStructureFeature) structure).generate(registryAccess, ((ChunkGenerator) (Object) this), this.biomeSource, structureManager,
                worldSeed, pos, biome, referenceCount, config, chunk);
            structureFeatureManager.setStartForFeature(pos, structure.feature, newStart, chunk);
        }
    }

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    public void createReferences(WorldGenLevel worldGenLevel, StructureFeatureManager featureManager, ChunkAccess chunkAccess, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }

        if (!(chunkAccess instanceof IBigCube)) {
            return;
        }

        ci.cancel();

        IBigCube cube = (IBigCube) chunkAccess;

        CubeWorldGenRegion world = (CubeWorldGenRegion) worldGenLevel;

        int cubeX = world.getMainCubeX();
        int cubeY = world.getMainCubeY();
        int cubeZ = world.getMainCubeZ();

        int blockX = cubeToMinBlock(cubeX);
        int blockY = cubeToMinBlock(cubeY);
        int blockZ = cubeToMinBlock(cubeZ);

        for (int x = cubeX - 8 / IBigCube.DIAMETER_IN_SECTIONS; x <= cubeX + 8 / IBigCube.DIAMETER_IN_SECTIONS; ++x) {
            for (int y = cubeY - 8 / IBigCube.DIAMETER_IN_SECTIONS; y <= cubeY + 8 / IBigCube.DIAMETER_IN_SECTIONS; ++y) {
                for (int z = cubeZ - 8 / IBigCube.DIAMETER_IN_SECTIONS; z <= cubeZ + 8 / IBigCube.DIAMETER_IN_SECTIONS; ++z) {
                    long cubePosAsLong = CubePos.asLong(x, y, z);

                    for (StructureStart<?> structureStart : world.getCube(CubePos.of(x, y, z)).getAllCubeStructureStarts().values()) {

                        if (!((ICubicStructureStart) structureStart).has3DPlacement() && y != cubeY) {
                            continue;
                        }

                        try {
                            if (structureStart != StructureStart.INVALID_START && structureStart.getBoundingBox().intersects(
                                //We use a new Bounding Box and check if it intersects a given cube.
                                new BoundingBox(blockX, blockY, blockZ,
                                    blockX + IBigCube.DIAMETER_IN_BLOCKS - 1,
                                    blockY + IBigCube.DIAMETER_IN_BLOCKS - 1,
                                    blockZ + IBigCube.DIAMETER_IN_BLOCKS - 1))) {
                                //The First Param is a SectionPos arg that is not used anywhere so we make it null.
                                featureManager.addReferenceForFeature(null, structureStart.getFeature(), cubePosAsLong, cube);
                                DebugPackets.sendStructurePacket(world, structureStart);
                            }
                        } catch (Exception e) {
                            CrashReport crashReport = CrashReport.forThrowable(e, "Generating structure reference");
                            CrashReportCategory crashReportCategory = crashReport.addCategory("Structure");
                            crashReportCategory.setDetail("Id", () -> Registry.STRUCTURE_FEATURE.getKey(structureStart.getFeature()).toString());
                            crashReportCategory.setDetail("Name", () -> structureStart.getFeature().getFeatureName());
                            crashReportCategory.setDetail("Class", () -> structureStart.getFeature().getClass().getCanonicalName());
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    @Inject(at = @At("HEAD"),
        method = "findNearestMapFeature(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/feature/StructureFeature;Lnet/minecraft/core/BlockPos;IZ)"
            + "Lnet/minecraft/core/BlockPos;",
        cancellable = true)
    private void do3DLocateStructure(ServerLevel serverLevel, StructureFeature<?> structureFeature, BlockPos blockPos, int radius, boolean skipExistingChunks,
                                     CallbackInfoReturnable<BlockPos> cir) {
        if (((CubicLevelHeightAccessor) serverLevel).generates2DChunks()) {
            return;
        }

        if (!this.biomeSource.canGenerateStructure(structureFeature)) {
            cir.setReturnValue(null);
        } else if (structureFeature == StructureFeature.STRONGHOLD) {
            this.generateStrongholds();
            BlockPos blockPos2 = null;
            double d = Double.MAX_VALUE;
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (ChunkPos chunkPos : this.strongholdPositions) {
                mutable.set(SectionPos.sectionToBlockCoord(chunkPos.x, 8), 32, SectionPos.sectionToBlockCoord(chunkPos.z, 8));
                double e = mutable.distSqr(blockPos);
                if (blockPos2 == null) {
                    blockPos2 = new BlockPos(mutable);
                    d = e;
                } else if (e < d) {
                    blockPos2 = new BlockPos(mutable);
                    d = e;
                }
            }

            cir.setReturnValue(blockPos2);
        } else {
            StructureFeatureConfiguration structureFeatureConfiguration = this.settings.getConfig(structureFeature);
            cir.setReturnValue(structureFeatureConfiguration == null ? null : structureFeature
                .getNearestGeneratedFeature(serverLevel, serverLevel.structureFeatureManager(), blockPos, radius, skipExistingChunks, serverLevel.getSeed(), structureFeatureConfiguration));
        }
    }

    @Override
    public void decorate(CubeWorldGenRegion region, StructureFeatureManager structureManager, CubePrimer chunk) {
        int mainCubeX = region.getMainCubeX();
        int mainCubeY = region.getMainCubeY();
        int mainCubeZ = region.getMainCubeZ();

        int xStart = cubeToMinBlock(mainCubeX);
        int yStart = cubeToMinBlock(mainCubeY);
        int zStart = cubeToMinBlock(mainCubeZ);

        //Y value stays 32
        CubeWorldGenRandom worldgenRandom = new CubeWorldGenRandom();

        //Get each individual column from a given cube no matter the size. Where y height is the same per column.
        //Feed the given columnMinPos into the feature decorators.
        int cubeY = chunk.getCubePos().getY();
        for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
            for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
                chunk.moveColumns(columnX, columnZ);
                if (CCWorldGenUtils.areSectionsEmpty(cubeY, chunk.getPos(), chunk)) {
                    continue;
                }

                BlockPos columnMinPos = new BlockPos(xStart + (sectionToMinBlock(columnX)), yStart, zStart + (sectionToMinBlock(columnZ)));

                long seed = worldgenRandom.setDecorationSeed(region.getSeed(), columnMinPos.getX(), columnMinPos.getY(), columnMinPos.getZ());

                Biome biome = ((ChunkGenerator) (Object) this).getBiomeSource().getPrimaryBiome(new ChunkPos(cubeToSection(mainCubeX, columnX), cubeToSection(mainCubeZ, columnZ)));
                try {
                    ((BiomeGetter) (Object) biome).generate(structureManager, ((ChunkGenerator) (Object) this), region, seed, worldgenRandom, columnMinPos);
                } catch (Exception e) {
                    CrashReport crashReport = CrashReport.forThrowable(e, "Biome decoration");
                    crashReport.addCategory("Generation").setDetail("CubeX", mainCubeX).setDetail("CubeY", mainCubeY).setDetail("CubeZ", mainCubeZ).setDetail("Seed", seed)
                        .setDetail("Biome", biome);
                    throw new ReportedException(crashReport);
                }
            }
        }
    }

    // replace with non-atomic random for optimized random number generation
    @Redirect(method = "applyCarvers", at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/WorldgenRandom"))
    private WorldgenRandom createCarverRandom() {
        return new NonAtomicWorldgenRandom();
    }

    @Redirect(method = "applyCarvers", at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/carver/CarvingContext"))
    private CarvingContext cubicContext(ChunkGenerator chunkGenerator, long seed, BiomeManager access, ChunkAccess chunk, GenerationStep.Carving carver) {
        return new CubicCarvingContext(chunkGenerator, chunk);
    }
}
