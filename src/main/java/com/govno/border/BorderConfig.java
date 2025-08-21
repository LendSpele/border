package com.govno.border;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BorderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/border.json");

    private int distance = 5000; // дефолтное значение

    public BorderConfig() {
        load();
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
        save();
    }

    private void load() {
        if (!CONFIG_FILE.exists()) {
            save(); // если файла нет → создать дефолт
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            BorderConfig loaded = GSON.fromJson(reader, BorderConfig.class);
            if (loaded != null) {
                this.distance = loaded.distance;
            }
        } catch (Exception e) {
            System.err.println("[BorderConfig] Ошибка загрузки конфига, использую дефолт: " + e.getMessage());
            this.distance = 5000; // fallback
            save(); // пересоздать файл
        }
    }

    private void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("[BorderConfig] Ошибка сохранения: " + e.getMessage());
        }
    }
}
