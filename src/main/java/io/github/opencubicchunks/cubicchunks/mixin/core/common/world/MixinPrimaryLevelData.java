package io.github.opencubicchunks.cubicchunks.mixin.core.common.world;


import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BlockPosAccess;
import io.github.opencubicchunks.cubicchunks.server.CCServerSavedData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelVersion;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrimaryLevelData.class)
public class MixinPrimaryLevelData implements CCServerSavedData {

    private int packedXZ;

    @Override public void setPackedXZ(int packedXZ) {
        this.packedXZ = packedXZ;
    }

    @Override public boolean blockPosLongNoMatch() {
        return this.packedXZ != BlockPosAccess.getPackedXLength();
    }

    @Override public int getServerPackedXZ() {
        return packedXZ;
    }

    @Inject(method = "setTagData", at = @At("HEAD"))
    private void getPackedXZ(RegistryAccess registryManager, CompoundTag levelTag, CompoundTag playerTag, CallbackInfo ci) {
        levelTag.putInt("packedXZ", this.packedXZ);
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static void setPackedXZ(Dynamic<Tag> dynamic, DataFixer dataFixer, int dataVersion, CompoundTag playerData, LevelSettings levelInfo, LevelVersion saveVersionInfo,
                                    WorldGenSettings generatorOptions, Lifecycle lifecycle, CallbackInfoReturnable<PrimaryLevelData> cir) {
        ((CCServerSavedData) cir.getReturnValue()).setPackedXZ(dynamic.get("packedXZ").asInt(BlockPosAccess.getPackedXLength()));
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/levelgen/WorldGenSettings;Lcom/mojang/serialization/Lifecycle;)V", at = @At("RETURN"))
    private void setPackedXZ(LevelSettings levelSettings, WorldGenSettings worldGenSettings, Lifecycle lifecycle, CallbackInfo ci) {
        this.packedXZ = BlockPosAccess.getPackedXLength();
    }
}
