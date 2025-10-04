package net.jackson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RecipeViewerConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lessthanenoughitems.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean showOnlyCraftable = true; // Set to true as default
    public boolean useNewUI = true; // Enable the new UI by default
    public int itemsPerRow = 15;
    public int rowsPerPage = 20;

    private static RecipeViewerConfig instance;

    public static RecipeViewerConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static RecipeViewerConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, RecipeViewerConfig.class);
            } catch (Exception e) {
                System.err.println("Failed to load config, using defaults: " + e.getMessage());
            }
        }
        return new RecipeViewerConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public void toggleShowOnlyCraftable() {
        showOnlyCraftable = !showOnlyCraftable;
        save();
    }

    public void toggleNewUI() {
        useNewUI = !useNewUI;
        save();
    }
}
