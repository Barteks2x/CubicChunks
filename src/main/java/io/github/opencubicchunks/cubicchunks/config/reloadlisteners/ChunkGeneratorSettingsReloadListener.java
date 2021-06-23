package io.github.opencubicchunks.cubicchunks.config.reloadlisteners;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.ChunkGeneratorSettings;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class ChunkGeneratorSettingsReloadListener<T extends Codec<? extends ChunkGenerator>> implements SimpleSynchronousResourceReloadListener {

    public static final IdentityHashMap<Codec<? extends ChunkGenerator>, ChunkGeneratorSettings> CHUNK_GENERATOR_SETTINGS = new IdentityHashMap<>();

    private static final IdentityHashMap<String, ChunkGeneratorSettings> CONFIG_DEFAULT = Util.make(new IdentityHashMap<>(), (map) -> {
        map.put("modid:chunk_generator", ChunkGeneratorSettings.DEFAULT);
    });

    private final Registry<T> registry;
    private final String jsonFileTarget;
    private final IdentityHashMap<T, ChunkGeneratorSettings> trackedEntriesMap;
    private final Path configPath;

    public ChunkGeneratorSettingsReloadListener(Registry<T> registry, String jsonFileTarget, IdentityHashMap<T, ChunkGeneratorSettings> trackedEntriesMap) {
        this.registry = registry;
        this.jsonFileTarget = jsonFileTarget;
        this.trackedEntriesMap = trackedEntriesMap;
        this.configPath = CubicChunks.WORLD_CONFIG_PATH.resolve(jsonFileTarget);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        IdentityHashMap<T, ChunkGeneratorSettings> newMap = new IdentityHashMap<>();
        ResourceLocation location = new ResourceLocation(CubicChunks.MODID, this.jsonFileTarget);
        try {
            Collection<Resource> heightSettings = manager.getResources(location);
            for (Resource settings : heightSettings) {
                readAndProcess(newMap, settings.toString(), new BufferedReader(new InputStreamReader(settings.getInputStream())));
            }
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not get resources for: " + location);
        }

        handleConfig(newMap);

        this.trackedEntriesMap.clear();
        this.trackedEntriesMap.putAll(newMap);
    }

    private void handleConfig(IdentityHashMap<T, ChunkGeneratorSettings> newMap) {
        if (!configPath.toFile().exists()) {
            createDefault();
        }

        try {
            readAndProcess(newMap, configPath.getFileName().toString(), new FileReader(configPath.toString()));
        } catch (FileNotFoundException e) {
            CubicChunks.LOGGER.error("Could not parse chunk generator settings for: \"" + configPath.getFileName() + "\". Entries in this file will not be added...");
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

    private void readAndProcess(IdentityHashMap<T, ChunkGeneratorSettings> newMap, String fileName, Reader bufferedReader) {
        try (Reader reader = bufferedReader) {
            JsonObject jsonObject = new JsonParser().parse(reader).getAsJsonObject();

            process(newMap, jsonObject);

        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not parse chunk generator settings for: \"" + fileName + "\". Entries in this file will not be added...");
        }
    }

    private void process(IdentityHashMap<T, ChunkGeneratorSettings> newMap, JsonObject jsonObject) {
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            ResourceLocation entryID = new ResourceLocation(entry.getKey());
            ChunkGeneratorSettings.Builder chunkGeneratorSettingsEntry = new ChunkGeneratorSettings.Builder();
            if (registry.keySet().contains(entryID)) {
                processEntry(jsonObject, chunkGeneratorSettingsEntry);
                newMap.put(registry.get(entryID), chunkGeneratorSettingsEntry.build());
            } else {
                CubicChunks.LOGGER.error("\"" + entryID.toString() + "\" was not found in the \"" + registry.key().location() + "\" registry, skipping entry...");
            }
        }
    }

    private void processEntry(JsonObject jsonObject, ChunkGeneratorSettings.Builder heightEntry) {
        if (jsonObject.has("controlled_statuses")) {
            String controlledStatuses = jsonObject.get("controlled_statuses").getAsString();
            heightEntry.addControlledStatuses(controlledStatuses);
        }
    }

    @Override public ResourceLocation getFabricId() {
        return new ResourceLocation(CubicChunks.MODID, this.jsonFileTarget.replace(".json", "_listener"));
    }

    public static void registerChunkGeneratorSettingsReloadListeners() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
            .registerReloadListener(new ChunkGeneratorSettingsReloadListener<>(Registry.CHUNK_GENERATOR, "chunk_generator_settings.json", CHUNK_GENERATOR_SETTINGS));
    }
}
