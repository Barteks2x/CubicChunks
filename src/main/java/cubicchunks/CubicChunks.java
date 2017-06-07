/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks;

import static cubicchunks.api.worldgen.biome.CubicBiome.oceanWaterReplacer;
import static cubicchunks.api.worldgen.biome.CubicBiome.surfaceDefaultReplacer;
import static cubicchunks.api.worldgen.biome.CubicBiome.terrainShapeReplacer;

import cubicchunks.debug.DebugTools;
import cubicchunks.debug.DebugWorldType;
import cubicchunks.network.PacketDispatcher;
import cubicchunks.proxy.CommonProxy;
import cubicchunks.server.chunkio.async.forge.AsyncWorldIOExecutor;
import cubicchunks.world.type.CustomCubicWorldType;
import cubicchunks.world.type.FlatCubicWorldType;
import cubicchunks.world.type.VanillaCubicWorldType;
import cubicchunks.worldgen.generator.CubeGeneratorsRegistry;
import cubicchunks.api.worldgen.biome.CubicBiome;
import cubicchunks.worldgen.generator.custom.ConversionUtils;
import cubicchunks.worldgen.generator.custom.biome.replacer.MesaSurfaceReplacer;
import cubicchunks.worldgen.generator.custom.biome.replacer.SwampWaterWithLilypadReplacer;
import cubicchunks.worldgen.generator.custom.populator.DefaultDecorator;
import cubicchunks.worldgen.generator.custom.populator.DesertDecorator;
import cubicchunks.worldgen.generator.custom.populator.ForestDecorator;
import cubicchunks.worldgen.generator.custom.populator.HillsDecorator;
import cubicchunks.worldgen.generator.custom.populator.JungleDecorator;
import cubicchunks.worldgen.generator.custom.populator.MesaDecorator;
import cubicchunks.worldgen.generator.custom.populator.PlainsDecorator;
import cubicchunks.worldgen.generator.custom.populator.SavannaDecorator;
import cubicchunks.worldgen.generator.custom.populator.SnowBiomeDecorator;
import cubicchunks.worldgen.generator.custom.populator.SwampDecorator;
import cubicchunks.worldgen.generator.custom.populator.TaigaDecorator;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeBeach;
import net.minecraft.world.biome.BiomeDesert;
import net.minecraft.world.biome.BiomeForest;
import net.minecraft.world.biome.BiomeForestMutated;
import net.minecraft.world.biome.BiomeHills;
import net.minecraft.world.biome.BiomeJungle;
import net.minecraft.world.biome.BiomeMesa;
import net.minecraft.world.biome.BiomeMushroomIsland;
import net.minecraft.world.biome.BiomeOcean;
import net.minecraft.world.biome.BiomePlains;
import net.minecraft.world.biome.BiomeRiver;
import net.minecraft.world.biome.BiomeSavanna;
import net.minecraft.world.biome.BiomeSavannaMutated;
import net.minecraft.world.biome.BiomeSnow;
import net.minecraft.world.biome.BiomeStoneBeach;
import net.minecraft.world.biome.BiomeSwamp;
import net.minecraft.world.biome.BiomeTaiga;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.IForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mod(modid = CubicChunks.MODID,
        name = "CubicChunks",
        version = "@@VERSION@@",
        guiFactory = "cubicchunks.client.GuiFactory"
        /*@@DEPS_PLACEHOLDER@@*/)// This will be replaced by gradle with actual dependency list, do not alter it
@Mod.EventBusSubscriber
public class CubicChunks {

    public static final int MIN_BLOCK_Y = Integer.MIN_VALUE >> 1;
    public static final int MAX_BLOCK_Y = Integer.MAX_VALUE >> 1;

    public static final boolean DEBUG_ENABLED = System.getProperty("cubicchunks.debug", "false").equalsIgnoreCase("true");
    public static final String MODID = "cubicchunks";
    public static final String MALISIS_VERSION = "@@MALISIS_VERSION@@";

    @Nonnull
    public static Logger LOGGER = LogManager.getLogger("EarlyCubicChunks");//use some logger even before it's set. useful for unit tests

    @SidedProxy(clientSide = "cubicchunks.proxy.ClientProxy", serverSide = "cubicchunks.proxy.ServerProxy")
    public static CommonProxy proxy;
    @Nullable private static Config config;
    @Nonnull
    private static Set<IConfigUpdateListener> configChangeListeners = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void registerRegistries(RegistryEvent.NewRegistry evt) {
        CubicBiome.init();
    }

    @SubscribeEvent
    public static void registerCubicBiomes(RegistryEvent<CubicBiome> event) {
        // Vanilla biomes are initialized during bootstrap which happens before registration events
        // so it should be safe to use them here

        autoRegister(Biome.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomeBeach.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomeDesert.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators().decorator(new DesertDecorator()));
        autoRegister(BiomeForest.class, b -> b
                .addDefaultBlockReplacers()
                .decorator(new ForestDecorator()).defaultDecorators());
        autoRegister(BiomeForestMutated.class, b -> b
                .addDefaultBlockReplacers()
                .decorator(new ForestDecorator()).defaultDecorators());
        autoRegister(BiomeHills.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators().decorator(new HillsDecorator()));
        autoRegister(BiomeJungle.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators().decorator(new JungleDecorator()));
        autoRegister(BiomeMesa.class, b -> b
                .addBlockReplacer(terrainShapeReplacer()).addBlockReplacer(MesaSurfaceReplacer.provider()).addBlockReplacer(oceanWaterReplacer())
                .decorator(new DefaultDecorator.Ores()).decorator(new MesaDecorator()).decorator(new DefaultDecorator()));
        autoRegister(BiomeMushroomIsland.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomeOcean.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomePlains.class, b -> b
                .addDefaultBlockReplacers()
                .decorator(new PlainsDecorator()).defaultDecorators());
        autoRegister(BiomeRiver.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomeSavanna.class, b -> b
                .addDefaultBlockReplacers()
                .decorator(new SavannaDecorator()).defaultDecorators());
        autoRegister(BiomeSavannaMutated.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomeSnow.class, b -> b
                .addDefaultBlockReplacers()
                .decorator(new SnowBiomeDecorator()).defaultDecorators());
        autoRegister(BiomeStoneBeach.class, b -> b
                .addDefaultBlockReplacers()
                .defaultDecorators());
        autoRegister(BiomeSwamp.class, b -> b
                .addDefaultBlockReplacers().addBlockReplacer(SwampWaterWithLilypadReplacer.provider())
                .defaultDecorators().decorator(new SwampDecorator()));
        autoRegister(BiomeTaiga.class, b -> b
                .addDefaultBlockReplacers()
                .decorator(new TaigaDecorator()).defaultDecorators());

    }

    private static void autoRegister(Class<? extends Biome> cl, Consumer<CubicBiome.Builder> cons) {
        ForgeRegistries.BIOMES.getValues().stream()
                .filter(x -> x.getRegistryName().getResourceDomain().equals("minecraft"))
                .filter(x -> x.getClass() == cl).forEach(b -> {
            CubicBiome.Builder builder = CubicBiome.createForBiome(b);
            cons.accept(builder);
            builder.defaultPostDecorators().setRegistryName(MODID, b.getRegistryName().getResourcePath()).register();
        });
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOGGER = e.getModLog();

        ConversionUtils.initFlowNoiseHack();

        config = new Config(new Configuration(e.getSuggestedConfigurationFile()));
        AsyncWorldIOExecutor.registerListeners();

        if (DEBUG_ENABLED) {
            DebugTools.init();
        }
    }

    @EventHandler
    public void init(FMLInitializationEvent event) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        proxy.registerEvents();

        VanillaCubicWorldType.create();
        FlatCubicWorldType.create();
        CustomCubicWorldType.create();
        DebugWorldType.create();
        LOGGER.debug("Registered world types");

        PacketDispatcher.registerPackets();
        CubeGeneratorsRegistry.computeSortedGeneratorList();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        CubicBiome.postInit();
    }

    @EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        proxy.setBuildLimit(event.getServer());
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent eventArgs) {
        if (eventArgs.getModID().equals(CubicChunks.MODID)) {
            config.syncConfig();
            for (IConfigUpdateListener l : configChangeListeners) {
                l.onConfigUpdate(config);
            }
        }
    }

    public static ResourceLocation location(String location) {
        return new ResourceLocation(MODID, location);
    }

    public static void addConfigChangeListener(IConfigUpdateListener listener) {
        configChangeListeners.add(listener);
        //notify if the config is already there
        if (config != null) {
            listener.onConfigUpdate(config);
        }
    }

    public static class Config {

        public static enum Options {
            MAX_GENERATED_CUBES_PER_TICK(1, Integer.MAX_VALUE, 49 * 16, "The number of cubic chunks to generate per tick."),
            VERTICAL_CUBE_LOAD_DISTANCE(2, 32, 8, "Similar to Minecraft's view distance, only for vertical chunks."),
            CHUNK_G_C_INTERVAL(1, Integer.MAX_VALUE, 20 * 10,
                    "Chunk garbage collector update interval. A more lower it is - a more CPU load it will generate. "
                            + "A more high it is - a more memory will be used to store cubes between launches.");

            private final int minValue;
            private final int maxValue;
            private final int defaultValue;
            private final String description;
            private int value;

            private Options(int minValue1, int maxValue1, int defaultValue1, String description1) {
                minValue = minValue1;
                maxValue = maxValue1;
                defaultValue = defaultValue1;
                description = description1;
                value = defaultValue;
            }

            public float getNormalValue() {
                return (float) (value - minValue) / (maxValue - minValue);
            }

            public void setValueFromNormal(float sliderValue) {
                value = minValue + (int) ((maxValue - minValue) * sliderValue);
                config.configuration.get(Configuration.CATEGORY_GENERAL, this.getNicelyFormattedName(), value).set(value);
                config.configuration.save();
                for (IConfigUpdateListener l : configChangeListeners) {
                    l.onConfigUpdate(config);
                }
            }

            public int getValue() {
                return value;
            }

            public String getNicelyFormattedName() {
                StringBuffer out = new StringBuffer();
                char char_ = '_';
                char prevchar = 0;
                for (char c : this.name().toCharArray()) {
                    if (c != char_ && prevchar != char_) {
                        out.append(String.valueOf(c).toLowerCase());
                    } else if (c != char_) {
                        out.append(String.valueOf(c));
                    }
                    prevchar = c;
                }
                return out.toString();
            }
        }

        private Configuration configuration;

        private Config(Configuration configuration) {
            loadConfig(configuration);
            syncConfig();
        }

        void loadConfig(Configuration configuration) {
            this.configuration = configuration;
        }

        void syncConfig() {
            for (Options configOption : Options.values()) {
                configOption.value = configuration.getInt(configOption.getNicelyFormattedName(), Configuration.CATEGORY_GENERAL,
                        configOption.defaultValue, configOption.minValue, configOption.maxValue, configOption.description);
            }
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }

        public int getMaxGeneratedCubesPerTick() {
            return Options.MAX_GENERATED_CUBES_PER_TICK.value;
        }

        public int getVerticalCubeLoadDistance() {
            return Options.VERTICAL_CUBE_LOAD_DISTANCE.value;
        }

        public int getChunkGCInterval() {
            return Options.CHUNK_G_C_INTERVAL.value;
        }

        public static class GUI extends GuiConfig {

            public GUI(GuiScreen parent) {
                super(parent, new ConfigElement(config.configuration.getCategory(Configuration.CATEGORY_GENERAL)).getChildElements(), MODID, false,
                        false, GuiConfig.getAbridgedConfigPath(config.configuration.toString()));
            }
        }
    }
}
