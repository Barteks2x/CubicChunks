package io.github.opencubicchunks.cubicchunks.config;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;

public class HeightSettings {

    public static final HeightSettings DEFAULT = new HeightSettings(HeightType.WORLD, HeightType.WORLD, HeightType.WORLD);

    @SerializedName("min_y") private final HeightType minHeight;
    @SerializedName("max_y") private final HeightType maxHeight;
    @SerializedName("bounds_y") private final HeightType heightBounds;

    public HeightSettings(HeightType minHeight, HeightType maxHeight, HeightType heightBounds) {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.heightBounds = heightBounds;
    }

    public HeightType getMinHeight() {
        return minHeight;
    }

    public HeightType getMaxHeight() {
        return maxHeight;
    }

    public HeightType getHeightBounds() {
        return heightBounds;
    }


    public static class Builder {
        private HeightType minHeight = HeightType.WORLD;
        private HeightType maxHeight = HeightType.WORLD;
        private HeightType heightBounds = HeightType.WORLD;


        public Builder setMinHeight(HeightType minHeight) {
            this.minHeight = minHeight;
            return this;
        }

        public Builder setMaxHeight(HeightType maxHeight) {
            this.maxHeight = maxHeight;
            return this;
        }

        public Builder setHeightBounds(HeightType heightBounds) {
            this.heightBounds = heightBounds;
            return this;
        }

        public HeightSettings build() {
            return new HeightSettings(minHeight, maxHeight, heightBounds);
        }
    }


    public enum HeightType {
        WORLD,
        CUBE,
        REGION;

        public static final Set<String> HEIGHT_TYPES_NAMES = EnumSet.allOf(HeightType.class).stream().map(Enum::name).collect(Collectors.toSet());

        public int getHeight(CubeWorldGenRegion region) {
            if (ordinal() == 0) {
                return region.getLevel().getHeight();
            } else if (ordinal() == 1) {
                return region.getCenterCube().getCubePos().maxCubeY() - region.getCenterCube().getCubePos().minCubeY();
            } else {
                return Coords.cubeToMaxBlock(region.getMaxCubeY()) - Coords.cubeToMinBlock(region.getMinCubeY());
            }
        }

        public int getMinHeight(CubeWorldGenRegion region) {
            if (ordinal() == 0) {
                return region.getLevel().getMinBuildHeight();
            } else if (ordinal() == 1) {
                return region.getCenterCube().getCubePos().minCubeY();
            } else {
                return Coords.cubeToMinBlock(region.getMinCubeY());
            }
        }
    }
}