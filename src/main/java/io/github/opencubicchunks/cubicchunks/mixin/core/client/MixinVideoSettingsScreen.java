package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.client.OptionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Option;
import net.minecraft.client.ProgressOption;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VideoSettingsScreen.class)
public class MixinVideoSettingsScreen {

    private static final Option[] MODIFIED_OPTIONS;

    private static final ProgressOption VERTICAL_RENDER_DISTANCE = new ProgressOption("options.renderDistance", 2.0D,
        (Minecraft.getInstance().is64Bit() && Runtime.getRuntime().maxMemory() >= 1000000000L) ? 32.0 : 16.0D, 1.0F, (gameOptions) -> {
        return (double) CubicChunks.commonConfig().getVerticalViewDistance();
    }, (gameOptions, viewDistance) -> {
        CubicChunks.commonConfig().setVerticalViewDistance(viewDistance.intValue());
        Minecraft.getInstance().levelRenderer.needsUpdate();
    }, (gameOptions, option) -> {
        double value = option.get(gameOptions);
        return ((OptionAccess) option).invokeGenericValueLabel(new TranslatableComponent("options.chunks", (int) value));
    });


    @Redirect(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/VideoSettingsScreen;OPTIONS:[Lnet/minecraft/client/Option;"))
    private Option[] getOptionsMixin() {
        return MODIFIED_OPTIONS;
    }

    static {
        MODIFIED_OPTIONS = new Option[] { Option.GRAPHICS, Option.RENDER_DISTANCE, Option.AMBIENT_OCCLUSION, VERTICAL_RENDER_DISTANCE, Option.FRAMERATE_LIMIT, Option.ENABLE_VSYNC,
            Option.VIEW_BOBBING, Option.GUI_SCALE, Option.ATTACK_INDICATOR, Option.GAMMA, Option.RENDER_CLOUDS, Option.USE_FULLSCREEN, Option.PARTICLES, Option.MIPMAP_LEVELS,
            Option.ENTITY_SHADOWS, Option.SCREEN_EFFECTS_SCALE, Option.ENTITY_DISTANCE_SCALING, Option.FOV_EFFECTS_SCALE
        };
    }
}