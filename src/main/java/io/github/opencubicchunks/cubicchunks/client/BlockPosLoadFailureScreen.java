package io.github.opencubicchunks.cubicchunks.client;

import java.nio.file.Path;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BlockPosAccess;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.dimension.DimensionType;

public class BlockPosLoadFailureScreen extends Screen {
    @Nullable
    private final String currentWorldName;
    private final int currentServerXZPacked;
    private MultiLineLabel message;

    public BlockPosLoadFailureScreen(@Nullable String currentWorldName, int currentServerXZPacked) {
        super(new TranslatableComponent("blockposlongfailure.title", (MathUtil.unpackXZSize(currentServerXZPacked) * 2), MathUtil.unpackYSize(currentServerXZPacked),
            (MathUtil.unpackXZSize(BlockPosAccess.getPackedXLength()) * 2), DimensionType.Y_SIZE));
        this.currentWorldName = currentWorldName;
        this.currentServerXZPacked = currentServerXZPacked;
        this.message = MultiLineLabel.EMPTY;
    }

    protected void init() {
        super.init();
        this.message = MultiLineLabel.create(this.font, this.getTitle(), this.width - 50);

        this.addRenderableWidget(new Button(this.width / 3, this.height / 2 + ((this.height / 8)), this.width / 3, 20,
            new TranslatableComponent("gui.blockposlongfailure.title.menu.openfile"), (button) -> {
            Path blockPosPath = CubicChunks.CONFIG_PATH.resolve("blockpos.properties");
            CubicChunks.createBlockPosPropertiesFile(blockPosPath,
                "#File generated with the ±xzsize from world: \"" + (this.currentWorldName == null ? "" : this.currentWorldName) + "\".\nxzsize=" + (
                    MathUtil.unpackXZSize(this.currentServerXZPacked) * 2),
                false);

            Util.getPlatform().openUri(blockPosPath.toUri());
        }));
        this.addRenderableWidget(
            new Button(this.width / 3, this.height / 2 + ((this.height / 8) + (this.height / 8)), this.width / 3, 20, new TranslatableComponent("gui.toTitle"), (button) -> {
                this.minecraft.setScreen(null);
            }));
        this.addRenderableWidget(new Button(this.width / 3, this.height / 2 + ((this.height / 8) + (this.height / 8) + (this.height / 8)), this.width / 3, 20,
            new TranslatableComponent("menu.quit"), (button) -> {
            Minecraft.getInstance().stop();
        }));
    }

    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.message.renderCentered(matrices, this.width / 2, this.height / 12, 18, 16777215);
        super.render(matrices, mouseX, mouseY, delta);
    }

    public boolean shouldCloseOnEsc() {
        return false;
    }
}