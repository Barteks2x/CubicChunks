package io.github.opencubicchunks.cubicchunks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;

public class BlockPosLoadFailureScreenTrigger {

    public static void setBlockPosLongScreen(int packedXZ) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getConnection().getConnection().disconnect(new TranslatableComponent("multiplayer.status.quitting"));
        minecraft.getConnection().getConnection().handleDisconnection();
        minecraft.clearLevel();
        minecraft.setScreen(new BlockPosLoadFailureScreen(null, packedXZ));
    }
}
