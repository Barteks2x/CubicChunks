package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;
import io.github.opencubicchunks.cubicchunks.chunk.aquifer.AquiferSourceSampler;
import io.github.opencubicchunks.cubicchunks.chunk.aquifer.CubicAquifer;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.BaseStoneSource;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseModifier;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class MixinNoiseBasedChunkGenerator {
    @Mutable @Shadow @Final protected Supplier<NoiseGeneratorSettings> settings;

    @Shadow @Final protected BlockState defaultFluid;

    @Mutable @Shadow @Final int cellCountY;

    @Shadow @Final private int cellHeight;

    @Shadow @Final private NormalNoise barrierNoise;

    @Shadow @Final private NormalNoise waterLevelNoise;

    @Shadow @Final private NormalNoise lavaNoise;

    @Shadow public abstract int getSeaLevel();

    private AquiferSourceSampler aquiferSourceSampler;

    @Inject(method = "<init>(Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/BiomeSource;JLjava/util/function/Supplier;)V", at = @At("RETURN"))
    private void init(BiomeSource biomeSource, BiomeSource biomeSource2, long l, Supplier<NoiseGeneratorSettings> supplier, CallbackInfo ci) {
        // access to through the registry is slow: vanilla accesses settings directly from the supplier in the constructor anyway
        NoiseGeneratorSettings suppliedSettings = this.settings.get();
        this.settings = () -> suppliedSettings;

        AquiferSourceSampler aquiferSampler;
        if (suppliedSettings.getDefaultFluid().getBlock() != Blocks.LAVA) {
            aquiferSampler = new AquiferSourceSampler.Overworld(this.waterLevelNoise, this.lavaNoise, suppliedSettings);
        } else {
            aquiferSampler = new AquiferSourceSampler.Nether(this.waterLevelNoise);
        }

        this.aquiferSourceSampler = aquiferSampler;
    }

    @Redirect(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private int alwaysUseChunkMinBuildHeight(int a, int b, Executor executor, StructureFeatureManager accessor, ChunkAccess chunk) {
        if (((CubicLevelHeightAccessor) chunk).generates2DChunks()) {
            return Math.max(a, b);
        }
        return b;
    }

    @Redirect(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseSettings;minY()I"))
    private int modifyMinY(NoiseSettings noiseSettings, Executor executor, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return noiseSettings.minY();
        }

        return chunkAccess.getMinBuildHeight();
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"))
    private void changeCellSize(Executor executor, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        this.cellCountY = chunkAccess.getHeight() / this.cellHeight;
    }

    @Inject(method = "doFill", at = @At("HEAD"))
    private void changeCellSize2(StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, int i, int j, CallbackInfoReturnable<ChunkAccess> cir) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        this.cellCountY = chunkAccess.getHeight() / this.cellHeight;
    }

    @Redirect(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;intFloorDiv(II)I", ordinal = 1))
    private int alwaysUseChunkMaxHeight(int i, int j, Executor executor, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return Mth.intFloorDiv(i, j);
        }

        return Mth.intFloorDiv(chunkAccess.getMaxBuildHeight() - chunkAccess.getMinBuildHeight(), cellHeight);
    }

    @Inject(method = "setBedrock", at = @At(value = "HEAD"), cancellable = true)
    private void cancelBedrockPlacement(ChunkAccess chunk, Random random, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunk).generates2DChunks()) {
            return;
        }
        ci.cancel();
    }

    // replace with non-atomic random for optimized random number generation
    @Redirect(method = "buildSurfaceAndBedrock", at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/WorldgenRandom"))
    private WorldgenRandom createCarverRandom() {
        return new NonAtomicWorldgenRandom();
    }

    @Redirect(method = "buildSurfaceAndBedrock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;getMinSurfaceLevel()I"))
    private int useWorldMinY(NoiseGeneratorSettings noiseGeneratorSettings, WorldGenRegion region, ChunkAccess chunk) {
        if (!((CubicLevelHeightAccessor) region).isCubic()) {
            return noiseGeneratorSettings.getMinSurfaceLevel();
        }
        if (region.getLevel().dimension() == Level.NETHER) {
            return chunk.getMinBuildHeight(); // Allow surface builders to generate infinitely in the nether.
        }
        return noiseGeneratorSettings.getMinSurfaceLevel();
    }

    @Redirect(method = "buildSurfaceAndBedrock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;getSeaLevel()I"))
    private int noSeaLevelNether(NoiseBasedChunkGenerator noiseBasedChunkGenerator, WorldGenRegion region, ChunkAccess chunk) {
        if (!((CubicLevelHeightAccessor) region).isCubic()) {
            return this.getSeaLevel();
        }
        if (region.getLevel().dimension() == Level.NETHER) {
            return chunk.getMinBuildHeight(); // The nether has no sea level for cubic chunks so, so no sea level :P
        }
        return this.getSeaLevel();
    }

    @Redirect(method = "updateNoiseAndGenerateBaseState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseModifier;modifyNoise(DIII)D"))
    private double dontModifyNoiseIfAboveCurrentCube(NoiseModifier noiseModifier, double weight, int x, int y, int z, Beardifier structures, Aquifer aquifer,
                                                     BaseStoneSource blockInterpolator, NoiseModifier noiseModifier2, int i, int j, int k, double d) {
        if (aquifer instanceof CubicAquifer cubicAquifer && y >= (cubicAquifer.getMinY() + cubicAquifer.getSizeY())) {
            return weight;
        }
        return noiseModifier.modifyNoise(weight, x, y, z);
    }

    @Inject(method = "getAquifer", at = @At("HEAD"), cancellable = true)
    private void createNoiseAquifer(int minY, int sizeY, ChunkPos chunkPos, CallbackInfoReturnable<Aquifer> cir) {
        if (!this.settings.get().noiseSettings().islandNoiseOverride()) {
            cir.setReturnValue(new CubicAquifer(chunkPos, this.barrierNoise, this.aquiferSourceSampler, minY * cellHeight, this.defaultFluid));
        }
    }
}
