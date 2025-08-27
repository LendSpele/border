package com.govno.border;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BorderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("border-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/border.json");

    // список точек
    private List<int[]> polygon = new ArrayList<>();

    private static BorderConfig INSTANCE;

    public static BorderConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static BorderConfig load() {
        try {
            if (!CONFIG_FILE.exists()) {
                LOGGER.warn("Config not found, creating default...");
                BorderConfig config = new BorderConfig();
                config.polygon.add(new int[]{1900, -1900});
                config.polygon.add(new int[]{-2000, 2000});
                config.polygon.add(new int[]{-2130, 2130});
                config.polygon.add(new int[]{-2200, -2200});
                config.save();
                INSTANCE = config;
                return config;
            }

            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                Type type = new TypeToken<BorderConfig>() {}.getType();
                INSTANCE = GSON.fromJson(reader, type);
                LOGGER.info("Border config loaded with {} points", INSTANCE.polygon.size());
                return INSTANCE;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load border config", e);
            BorderConfig fallback = new BorderConfig();
            fallback.polygon.add(new int[]{0, 0});
            return fallback;
        }
    }

    public static void reload() {
        LOGGER.info("Reloading border config...");
        load();
    }

    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
            LOGGER.info("Border config saved!");
        } catch (Exception e) {
            LOGGER.error("Failed to save border config", e);
        }
    }

    public List<int[]> getPolygon() {
        return polygon;
    }
    public void setPolygon(List<int[]> polygon) {
        this.polygon = polygon;
    }
}
