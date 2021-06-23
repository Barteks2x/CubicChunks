package io.github.opencubicchunks.cubicchunks.config;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.util.StringRepresentable;

public class AbstractCommentedConfigHelper {

    private final CommentedConfig config;
    private final Path filePath;

    public AbstractCommentedConfigHelper(Path filePath) {
        this.filePath = filePath;
        if (!filePath.getParent().toFile().exists()) {
            try {
                Files.createDirectories(filePath.getParent());
            } catch (IOException e) {

            }
        }

        CommentedFileConfig config = CommentedFileConfig.builder(filePath).sync().autosave().writingMode(WritingMode.REPLACE).build();
        config.load();
        this.config = config;
    }


    public <T> List<T> addList(String comment, String key, List<T> defaultValue) {
        if (config.get(key) == null) {
            config.set(key, defaultValue);
        }

        if (config.getComment(key) == null) {
            config.setComment(key, comment);
        }
        return config.get(key);
    }


    public Map<?, ?> addMap(String comment, String key, Map<?, Number> defaultValue) {
        if (config.get(key) == null) {
            CommentedConfig subConfig = config.createSubConfig();
            defaultValue.forEach((a, b) -> {
                String subConfigKey = a.toString();
                if (subConfig.get(a.toString()) == null) {
                    subConfig.set(subConfigKey, b);
                }
            });
            config.set(key, subConfig);
        }

        CommentedConfig subConfig = config.get(key);
        String commentValue = config.getComment(key);
        if (commentValue == null) {
            config.setComment(key, comment);
        }

        return subConfig.valueMap();
    }

    public <T> T add(String comment, String key, T defaultValue) {
        if (config.get(key) == null) {
            config.set(key, defaultValue);
        }

        if (config.getComment(key) == null) {
            config.setComment(key, comment);
        }
        return config.get(key);
    }


    public <T extends Number & Comparable<T>> T addNumber(String comment, String key, T defaultValue, T min, T max) {
        if (config.get(key) == null) {
            config.set(key, defaultValue);
        }

        if (config.getComment(key) == null) {
            config.setComment(key, comment + String.format("\nRange: %s-%s", min, max));
        }
        T value = config.get(key);
        return value.compareTo(max) > 0 ? max : value.compareTo(min) < 0 ? min : value;
    }

    public String addString(String comment, String key, String defaultValue) {
        if (config.get(key) == null) {
            config.set(key, defaultValue);
        }

        if (config.getComment(key) == null) {
            config.setComment(key, comment);
        }
        return config.get(key);
    }

    public <T extends Enum<T>> T addEnum(String comment, String key, T defaultValue) {
        if (config.get(key) == null) {
            config.set(key, defaultValue);
        }

        if (config.getComment(key) == null) {
            StringBuilder builder = new StringBuilder().append("Values: ").append(defaultValue instanceof StringRepresentable ? "\n" : "");
            T[] enumConstants = defaultValue.getDeclaringClass().getEnumConstants();
            for (int idx = 0; idx < enumConstants.length; idx++) {
                T value = enumConstants[idx];
                if (defaultValue instanceof StringRepresentable) {
                    builder.append(((StringRepresentable) value).getSerializedName()).append(idx == enumConstants.length - 1 ? "" : "\n");
                } else {
                    builder.append(value.name()).append(", ");
                }
            }

            config.setComment(key, comment + "\n" + builder.toString());
        }


        String value = config.get(key).toString();
        return T.valueOf(defaultValue.getDeclaringClass(), value);
    }


    public <T> void updateValue(String key, T newValue) {
        this.config.set(key, newValue);
        build();
    }

    public void build() {
        TomlWriter writer = new TomlWriter();
        writer.write(config, filePath.toFile(), WritingMode.REPLACE);
    }
}