package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.chunk.CubicAquifer;
import io.github.opencubicchunks.cubicchunks.chunk.NonAtomicWorldgenRandom;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
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

    @Shadow @Final private int cellHeight;

    @Mutable @Shadow @Final private int cellCountY;

    @Shadow @Final protected BlockState defaultFluid;

    @Shadow @Final private NormalNoise barrierNoise;

    @Shadow @Final private NormalNoise waterLevelNoise;

    @Shadow @Final private NormalNoise lavaNoise;

    @Inject(method = "<init>(Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/BiomeSource;JLjava/util/function/Supplier;)V", at = @At("RETURN"))
    private void init(BiomeSource biomeSource, BiomeSource biomeSource2, long l, Supplier<NoiseGeneratorSettings> supplier, CallbackInfo ci) {
        // access to through the registry is slow: vanilla accesses settings directly from the supplier in the constructor anyway
        NoiseGeneratorSettings suppliedSettings = this.settings.get();
        this.settings = () -> suppliedSettings;
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

    @Inject(method = "getAquifer", at = @At("HEAD"), cancellable = true)
    private void createNoiseAquifer(int minY, int sizeY, ChunkPos chunkPos, CallbackInfoReturnable<Aquifer> cir) {
        if (!this.settings.get().noiseSettings().islandNoiseOverride()) {
            cir.setReturnValue(
                new CubicAquifer(chunkPos, this.barrierNoise, this.waterLevelNoise, this.lavaNoise, this.settings.get(), minY * cellHeight, sizeY * cellHeight, this.defaultFluid));
        }
    }
}
