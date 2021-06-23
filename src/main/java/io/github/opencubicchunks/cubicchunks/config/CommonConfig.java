package io.github.opencubicchunks.cubicchunks.config;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.utils.ExceptionHandler;

public class CommonConfig {

    public static final int MIN_RENDER_DISTANCE = 2;//TODO: Optifine check
    public static final int MAX_RENDER_DISTANCE = 32;

    private final AbstractCommentedConfigHelper configHelper;

    private int verticalViewDistance;
    private ExceptionHandler worldExceptionHandler;
    private boolean asyncChunkLoad;

    public CommonConfig() {
        this.configHelper = new AbstractCommentedConfigHelper(CubicChunks.CONFIG_PATH.resolve(CubicChunks.MODID + "-common.toml"));
        verticalViewDistance = this.configHelper.addNumber("Cubic Chunks vertical render Distance.", "verticalRenderDistance", 8, MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
        worldExceptionHandler = this.configHelper.addEnum("How will Cubic Chunks handle errors during world gen? It is recommended to leave this as:\""
                + ExceptionHandler.EXCEPTION_THROWN.toString() + "\"", "worldExceptionHandler", ExceptionHandler.EXCEPTION_THROWN);
        asyncChunkLoad = this.configHelper.add("Use async cube loading?", "asyncCubeLoad", true);
        this.configHelper.build();
    }

    public void setVerticalViewDistance(int verticalViewDistance) {
        this.verticalViewDistance = verticalViewDistance;
        this.configHelper.updateValue("verticalRenderDistance", verticalViewDistance);
    }

    public int getVerticalViewDistance() {
        return verticalViewDistance;
    }

    public ExceptionHandler getWorldExceptionHandler() {
        return worldExceptionHandler;
    }

    public boolean isAsyncChunkLoad() {
        return asyncChunkLoad;
    }
}
