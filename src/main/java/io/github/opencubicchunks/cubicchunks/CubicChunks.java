package io.github.opencubicchunks.cubicchunks;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.opencubicchunks.cubicchunks.chunk.IChunkManager;
import io.github.opencubicchunks.cubicchunks.meta.EarlyConfig;
import io.github.opencubicchunks.cubicchunks.network.PacketDispatcher;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.server.VerticalSettingsReloadListener;
import io.github.opencubicchunks.cubicchunks.world.biome.StripedBiomeSource;
import io.github.opencubicchunks.cubicchunks.world.gen.feature.CCFeatures;
import io.github.opencubicchunks.cubicchunks.world.gen.placement.CCPlacement;
import io.github.opencubicchunks.cubicchunks.world.gen.placement.CubicHeightProvider;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Requires Mixin BootStrap in order to use in forge.
 */
// The value here should match an entry in the META-INF/mods.toml file
public class CubicChunks implements ModInitializer {

    // TODO: debug and fix optimized cubeload
    public static final boolean OPTIMIZED_CUBELOAD = false;

    public static final long SECTIONPOS_SENTINEL = -1;

    public static final int MAX_SUPPORTED_HEIGHT = Integer.MAX_VALUE / 2;
    public static final int MIN_SUPPORTED_HEIGHT = -MAX_SUPPORTED_HEIGHT;
    public static final int SEA_LEVEL = 64;

    public static final String MODID = "cubicchunks";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final String PROTOCOL_VERSION = "0";

    public static final Map<String, CubicLevelHeightAccessor.WorldStyle> DIMENSION_TO_WORLD_STYLE = Util.make(new HashMap<>(), (set) -> {
        set.put("minecraft:overworld", CubicLevelHeightAccessor.WorldStyle.CUBIC);
        set.put("minecraft:the_nether", CubicLevelHeightAccessor.WorldStyle.CHUNK);
        set.put("minecraft:the_end", CubicLevelHeightAccessor.WorldStyle.CHUNK);
    });

    private static final Config CONFIG = new Config();

    public CubicChunks() {
        if (!(IChunkManager.class.isAssignableFrom(ChunkMap.class))) {
            throw new IllegalStateException("Mixin not applied!");
        }
        EarlyConfig.getDiameterInSections();

        if (System.getProperty("cubicchunks.debug", "false").equalsIgnoreCase("true")) {
            try {
                Class.forName("io.github.opencubicchunks.cubicchunks.debug.DebugVisualization").getMethod("enable").invoke(null);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
                LOGGER.catching(e);
            }
        }

        //Custom CC Features
        CCPlacement.init();
        CCFeatures.init();

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            cubicChunksTrapezoidHeightProviderTest();
        }
    }

    private void cubicChunksTrapezoidHeightProviderTest() {
        TrapezoidHeight trapezoidHeight = TrapezoidHeight.of(VerticalAnchor.absolute(0), VerticalAnchor.absolute(40), 10);

        class Context implements WorldGenerationContext {

            @Override public int getMinGenY() {
                throw new Error();
            }

            @Override public int getGenDepth() {
                return 384;
            }
        }

        WorldgenRandom random = new WorldgenRandom(1000);

        int[] counts = new int[41];
        int[] counts2 = new int[41];
        for (int i = 0; i < 100000; i++) {
            int sample = trapezoidHeight.sample(random, new Context());
            OptionalInt cubeTrapezoidHeight = ((CubicHeightProvider) trapezoidHeight).sampleCubic(random, new Context(), 0, 64);

            if (cubeTrapezoidHeight.isPresent()) {
                counts2[cubeTrapezoidHeight.getAsInt()]++;
            }

            counts[sample]++;
        }

        List<String> collect = IntStream.of(counts).mapToObj(Objects::toString).collect(Collectors.toList());
        List<String> collect2 = IntStream.of(counts2).mapToObj(Objects::toString).collect(Collectors.toList());

        try {
            Files.write(Paths.get("trapezoidHeight.txt"), collect, StandardOpenOption.CREATE);
            Files.write(Paths.get("cubeTrapezoidHeight.txt"), collect2, StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Config config() {
        return CONFIG;
    }

    @Override
    public void onInitialize() {
        PacketDispatcher.register();

        Registry.register(Registry.BIOME_SOURCE, new ResourceLocation(MODID, "stripes"), StripedBiomeSource.CODEC);
//        Registry.register(Registry.CHUNK_GENERATOR, new ResourceLocation(MODID, "generator"), CCNoiseBasedChunkGenerator.CODEC);

        VerticalSettingsReloadListener.registerVerticalSettingsReloadListener();
    }

    //TODO: Implement a file for this.
    public static class Config {
        public Client client = new Client();


        public void markDirty() {

        }


        public static class Client {
            public int verticalViewDistance = 8;
        }
    }
}