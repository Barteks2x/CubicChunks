package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.structure;

import io.github.opencubicchunks.cubicchunks.chunk.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.ICubicStructureStart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(StructureStart.class)
public abstract class MixinStructureStart implements ICubicStructureStart {
    private boolean has3dPlacement;

    @Shadow protected abstract BoundingBox createBoundingBox();

    @Override public void init3dPlacement() {
        this.has3dPlacement = true;
    }

    @Override public boolean has3DPlacement() {
        return this.has3dPlacement;
    }

    @ModifyArg(method = "getLocatePos", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;<init>(III)V"), index = 1)
    private int getStructureY(int arg0) {
        if (this.has3dPlacement) {
            return (this.createBoundingBox().minY() + this.createBoundingBox().maxY()) >> 1;
        }
        return arg0;
    }

    @Inject(method = "createTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;putInt(Ljava/lang/String;I)V", ordinal = 0), cancellable = true, locals =
        LocalCapture.CAPTURE_FAILHARD)
    private void packCubeStructureData(ServerLevel world, ChunkPos chunkPos, CallbackInfoReturnable<CompoundTag> cir, CompoundTag compoundTag) {

        if (!((CubicLevelHeightAccessor) world).isCubic()) {
            return;
        }

        if (chunkPos instanceof ImposterChunkPos) {
            compoundTag.putInt("ChunkY", ((ImposterChunkPos) chunkPos).y);
        }
    }
}
