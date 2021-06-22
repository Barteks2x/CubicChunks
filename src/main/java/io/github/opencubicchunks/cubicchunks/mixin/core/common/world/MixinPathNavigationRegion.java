package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import io.github.opencubicchunks.cubicchunks.chunk.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.chunk.cube.EmptyCube;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeCollisionGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigationRegion.class)
public abstract class MixinPathNavigationRegion implements CubeCollisionGetter {

    @Mutable @Shadow @Final protected int centerX;
    @Mutable @Shadow @Final protected int centerZ;
    @Shadow protected boolean allEmpty;
    @Shadow @Final protected Level level;
    private int centerY;
    private int diameter;
    private boolean isCubic;
    private int minY;
    private int height;

    private ChunkAccess[] cubes;

//    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/ChunkAccess"))
//    private ChunkAccess[][] no2DArray(Level level, BlockPos blockPos, BlockPos blockPos2) {
//        if (!((CubicLevelHeightAccessor) level).isCubic()) {
//            return new ChunkAccess[][]
//        }
//    }


    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;blockToSectionCoord(I)I"))
    private int zeroOutExistingIterators(int coord, Level world, BlockPos blockPos, BlockPos blockPos2) {
        if (!((CubicLevelHeightAccessor) world).isCubic()) {
            return SectionPos.blockToSectionCoord(coord);
        }

        return 0;
    }


    @Inject(method = "<init>", at = @At("TAIL"))
    private void useCubes(Level world, BlockPos blockPos, BlockPos blockPos2, CallbackInfo ci) {
        this.isCubic = ((CubicLevelHeightAccessor) world).isCubic();
        this.minY = world.getMinBuildHeight();
        this.height = world.getHeight();

        if (!isCubic) {
            return;
        }
        this.centerX = Coords.blockToCube(blockPos.getX());
        this.centerY = Coords.blockToCube(blockPos.getY());
        this.centerZ = Coords.blockToCube(blockPos.getZ());

        int offsetX = Coords.blockToCube(blockPos2.getX());
        int offsetY = Coords.blockToCube(blockPos2.getY());
        int offsetZ = Coords.blockToCube(blockPos2.getZ());

        this.cubes = new ChunkAccess[(offsetX - centerX + 1) * (offsetY - centerY + 1) * (offsetZ - centerZ + 1)];

        this.diameter = Mth.floor(Math.cbrt(cubes.length));

        ICubeProvider cubeProvider = (ICubeProvider) world.getChunkSource();

        for (int x = this.centerX; x <= offsetX; ++x) {
            for (int y = this.centerY; y <= offsetY; ++y) {
                for (int z = this.centerZ; z <= offsetZ; ++z) {
                    int dx = x - this.centerX;
                    int dy = y - this.centerY;
                    int dz = z - this.centerZ;
                    int index = diameter * (dx * diameter + dy) + dz;
                    this.cubes[index] = cubeProvider.getCubeNow(x, y, z);
                }
            }
        }

        // Who thought this was a good idea? BC THIS FIELD LITERALLY DOES NOTHING >:(
//        for (int x = this.centerX; x < offsetX; x++) {
//            for (int y = this.centerY; y < offsetY; y++) {
//                for (int z = this.centerZ; z < offsetZ; z++) {
//                    int dx = x - this.centerX;
//                    int dy = y - this.centerY;
//                    int dz = z - this.centerZ;
//                    int index = diameter * (dx * diameter + dy) + dz;
//                    ChunkAccess chunkAccess = this.cubes[index];
//                    if (chunkAccess != null && !chunkAccess.isYSpaceEmpty(blockPos.getY(), blockPos2.getY())) {
//                        this.allEmpty = false;
//                        return;
//                    }
//                }
//            }
//        }


    }

    @Inject(method = "getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void getCube(BlockPos pos, CallbackInfoReturnable<ChunkAccess> cir) {
        if (!isCubic) {
            return;
        }

        cir.setReturnValue(getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ())));
    }


    // Do this instead bc we're in ticks and want to squeeze out as much optimization as we can by calling a variable in this class and going into level is slower.
    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void useHeightVariableInstead(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.height);
    }

    @Inject(method = "getMinBuildHeight", at = @At("HEAD"), cancellable = true)
    private void useMinYVariableInstead(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.minY);
    }

    @Nullable @Override public BlockGetter getCubeForCollisions(int cubeX, int cubeY, int cubeZ) {
        return getCube(cubeX, cubeY, cubeZ);
    }

    private ChunkAccess getCube(int cubeX, int cubeY, int cubeZ) {
        int dx = cubeX - this.centerX;
        int dy = cubeY - this.centerY;
        int dz = cubeZ - this.centerZ;
        int index = this.diameter * (dx * this.diameter + dy) + dz;
        if (dx >= 0 && dy >= 0 && dz >= 0 && index < cubes.length) {
            ChunkAccess chunkAccess = this.cubes[index];
            return (chunkAccess != null ? chunkAccess : new EmptyCube(this.level, CubePos.of(cubeX, cubeY, cubeZ)));
        } else {
            return new EmptyCube(this.level, CubePos.of(cubeX, cubeY, cubeZ));
        }
    }
}
