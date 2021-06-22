package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;

import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeCollisionGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.CollisionSpliterator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CollisionSpliterator.class)
public abstract class MixinCollisionSpliterator {

    @Shadow @Final private CollisionGetter collisionGetter;
    @Shadow @Final private Cursor3D cursor;
    private boolean isCubic;

    @Shadow @Nullable protected abstract BlockGetter getChunk(int x, int z);

    @Inject(method = "<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/BiPredicate;)V",
        at = @At("RETURN"))
    private void initIsCubicCheck(CollisionGetter collisionGetter1, Entity entity, AABB aABB, BiPredicate<BlockState, BlockPos> biPredicate, CallbackInfo ci) {
        this.isCubic = ((CubicLevelHeightAccessor) collisionGetter1).isCubic();
    }

    @Nullable
    @Redirect(method = "collisionCheck", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CollisionSpliterator;getChunk(II)Lnet/minecraft/world/level/BlockGetter;"))
    private BlockGetter getCube(CollisionSpliterator collisionSpliterator, int x, int z) {
        if (!isCubic) {
            return this.getChunk(x, z);
        }

        return ((CubeCollisionGetter) this.collisionGetter)
            .getCubeForCollisions(Coords.blockToCube(x), Coords.blockToCube(this.cursor.nextY()), Coords.blockToCube(z));
    }
}
