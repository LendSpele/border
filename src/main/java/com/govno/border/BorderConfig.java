package com.govno.border;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BorderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/border_config.json");

    private int posX = 1900;
    private int negX = -2000;
    private int posZ = 2130;
    private int negZ = -2200;

    public int getPosX() {
        return posX;
    }

    public int getNegX() {
        return negX;
    }

    public int getPosZ() {
        return posZ;
    }

    public int getNegZ() {
        return negZ;
    }

    public void setPosX(int posX) {
        this.posX = posX;
        save();
    }

    public void setNegX(int negX) {
        this.negX = negX;
        save();
    }

    public void setPosZ(int posZ) {
        this.posZ = posZ;
        save();
    }

    public void setNegZ(int negZ) {
        this.negZ = negZ;
        save();
    }

    public void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static BorderConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, BorderConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BorderConfig config = new BorderConfig();
        config.save();
        return config;
    }
}
