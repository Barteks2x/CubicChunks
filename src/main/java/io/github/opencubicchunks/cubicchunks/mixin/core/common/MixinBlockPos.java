package io.github.opencubicchunks.cubicchunks.mixin.core.common;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BlockPos.class)
public class MixinBlockPos {

    //This is a workaround to allow usages of Blockpos.tolong to work in taller worlds at the cost of XZ size.
    @SuppressWarnings("UnresolvedMixinReference")
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 30000000))
    private static int getMaxWorldSizeXZ(int size) {
        int defaultValue = 1000000;
        Path blockPosPath = CubicChunks.CONFIG_PATH.resolve("blockpos.properties");
        try (FileReader reader = new FileReader(blockPosPath.toFile())) {
            Properties p = new Properties();
            p.load(reader);

            try {
                String xzsize = p.getProperty("xzsize");
                int parsed = Integer.parseInt(xzsize);
                int sizeAbsolute = parsed / 2;
                int xzPacked = 1 + Mth.log2(Mth.smallestEncompassingPowerOfTwo(sizeAbsolute));
                int xzUnpackedSize = MathUtil.unpackXZSize(xzPacked);
                if (sizeAbsolute != xzUnpackedSize) {
                    CubicChunks.createBlockPosPropertiesFile(blockPosPath,
                        "#File generated from the xzsize of: \"" + parsed + "\" where this new value is the nearest encompassing power of 2.\nxzsize=" + (xzUnpackedSize * 2), true);
                }
                CubicChunks.LOGGER.info("BlockPos XZ size: " + xzUnpackedSize);

                return xzUnpackedSize;
            } catch (NumberFormatException e) {
            }
        } catch (IOException e) {
        }

        CubicChunks.LOGGER.info("BlockPos XZ size: " + defaultValue);
        return defaultValue;
    }
}
