package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.structure;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.server.commands.LocateCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LocateCommand.class)
public class MixinLocateCommand {

    @ModifyConstant(method = "showLocateResult", constant = @Constant(stringValue = "~"))
    private static String show3DLocateResultInBrackets(String arg0, CommandSourceStack source, String structure, BlockPos sourcePos, BlockPos structurePos, String successMessage) {
        return String.valueOf(structurePos.getY());
    }

//    @SuppressWarnings("UnresolvedMixinReference")
//    @ModifyConstant(method = "lambda$showLocateResult$2(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/chat/Style;)Lnet/minecraft/network/chat/Style;",
//        constant = @Constant(stringValue = " ~ "))
//    private static String show3DLocateResultForTPSuggestion(String arg0, BlockPos structurePos, Style style) {
//        return " " + structurePos.getY() + " ";
//    }
}
