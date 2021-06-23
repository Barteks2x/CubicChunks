package io.github.opencubicchunks.cubicchunks.config.reloadlisteners;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;

public class WorldStyleReloadListener implements SimpleSynchronousResourceReloadListener {

    public static final HashMap<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> WORLD_WORLD_STYLE = new HashMap<>();

    private static final IdentityHashMap<String, CubicLevelHeightAccessor.WorldStyle> CONFIG_DEFAULT = Util.make(new IdentityHashMap<>(), (map) -> {
        map.put(Level.OVERWORLD.location().toString(), CubicLevelHeightAccessor.WorldStyle.CUBIC);
        map.put(Level.NETHER.location().toString(), CubicLevelHeightAccessor.WorldStyle.CHUNK);
        map.put(Level.END.location().toString(), CubicLevelHeightAccessor.WorldStyle.CHUNK);
    });

    private final String jsonFileTarget;
    private final Map<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> trackedEntriesMap;
    private final Path configPath;

    public WorldStyleReloadListener(String jsonFileTarget, Map<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> trackedEntriesMap) {
        this.jsonFileTarget = jsonFileTarget;
        this.trackedEntriesMap = trackedEntriesMap;
        this.configPath = CubicChunks.WORLD_CONFIG_PATH.resolve(jsonFileTarget);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        IdentityHashMap<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> newMap = new IdentityHashMap<>();
        ResourceLocation location = new ResourceLocation(CubicChunks.MODID, this.jsonFileTarget);
        try {
            for (Resource settings : manager.getResources(location)) {
                readAndProcess(newMap, settings.toString(), new BufferedReader(new InputStreamReader(settings.getInputStream())));
            }
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not get resources for: " + location);
        }

        handleConfig(newMap);

        this.trackedEntriesMap.clear();
        this.trackedEntriesMap.putAll(newMap);
    }

    private void handleConfig(IdentityHashMap<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> newMap) {
        if (!configPath.toFile().exists()) {
            createDefault();
        }

        try {
            readAndProcess(newMap, configPath.getFileName().toString(), new FileReader(configPath.toString()));
        } catch (FileNotFoundException e) {
            CubicChunks.LOGGER.error("Could not parse height settings for: \"" + configPath.getFileName() + "\". Entries in this file will not be added...");
        }
    }

    private void createDefault() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(CONFIG_DEFAULT).getBytes());
        } catch (IOException e) {
            CubicChunks.LOGGER.error(this.configPath.toString() + " could not be created.");
        }
    }

    private void readAndProcess(IdentityHashMap<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> newMap, String fileName, Reader bufferedReader) {
        try (Reader reader = bufferedReader) {
            JsonObject jsonObject = new JsonParser().parse(reader).getAsJsonObject();

            process(newMap, jsonObject);

        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not parse height settings for: \"" + fileName + "\". Entries in this file will not be added...");
        }
    }

    private void process(IdentityHashMap<ResourceLocation, CubicLevelHeightAccessor.WorldStyle> newMap, JsonObject jsonObject) {
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            ResourceLocation entryID = new ResourceLocation(entry.getKey());
            String entryVal = entry.getValue().getAsString().toUpperCase();
            if (CubicLevelHeightAccessor.WorldStyle.WORLD_STYLE_NAMES.contains(entryVal)) {
                newMap.put(entryID, CubicLevelHeightAccessor.WorldStyle.valueOf(entryVal));
            }
        }
    }

    @Override public ResourceLocation getFabricId() {
        return new ResourceLocation(CubicChunks.MODID, this.jsonFileTarget.replace(".json", "_listener"));
    }

    public static void registerWorldStyleReloadListeners() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new WorldStyleReloadListener("world_style.json", WORLD_WORLD_STYLE));
    }
}
