package com.govno.border;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BorderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "border.json";

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME).toFile();

    // ====== поля, которые сериализуются ======
    private int distance = 5000; // дефолтная граница

    // ====== геттеры/сеттеры ======
    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    // ====== загрузка ======
    public static BorderConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                BorderConfig loaded = GSON.fromJson(reader, BorderConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                System.err.println("[BorderConfig] Ошибка чтения: " + e.getMessage());
            }
        }
        // если ошибка → создаём новый конфиг по дефолту
        BorderConfig def = new BorderConfig();
        def.save();
        return def;
    }

    // ====== сохранение ======
    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("[BorderConfig] Ошибка записи: " + e.getMessage());
        }
    }
}
