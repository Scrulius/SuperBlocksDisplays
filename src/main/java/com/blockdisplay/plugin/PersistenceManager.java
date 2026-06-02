package com.blockdisplay.plugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PersistenceManager {
    private final BlockDisplayPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public PersistenceManager(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawned.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create spawned.yml");
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveGroup(UUID groupId, String modelId, Location loc, float yaw) {
        String path = "groups." + groupId.toString();
        config.set(path + ".model", modelId);
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", yaw);
        save();
    }

    public void removeGroup(UUID groupId) {
        config.set("groups." + groupId.toString(), null);
        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawned.yml");
        }
    }

    public void loadSavedGroups() {
        ConfigurationSection section = config.getConfigurationSection("groups");
        if (section == null) return;

        int loadedCount = 0;
        for (String uuidStr : section.getKeys(false)) {
            UUID groupId = UUID.fromString(uuidStr);
            String path = "groups." + uuidStr;
            String modelId = config.getString(path + ".model");
            String worldName = config.getString(path + ".world");
            World world = plugin.getServer().getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found. Skipping model " + modelId);
                continue;
            }

            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");

            Location loc = new Location(world, x, y, z, yaw, 0);

            plugin.getModelManager().fetchModel(modelId).thenAccept(modelData -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (modelData != null) {
                        ModelGroup group = new ModelGroup(loc, groupId, modelId);
                        group.reconnectOrSpawn(modelData, plugin);
                        group.setYaw(yaw);

                        // Auto-start animation if model has animations
                        if (modelData.hasAnimations()) {
                            group.setAnimating(true);
                        }

                        plugin.getActiveGroups().put(groupId, group);
                        plugin.getLogger().info("Loaded model " + modelId + " (group " + groupId.toString().substring(0, 8) + ")");
                    } else {
                        plugin.getLogger().warning("Could not reload model " + modelId + " - may be expired on API.");
                    }
                });
            });
            loadedCount++;
        }
        plugin.getLogger().info("Loading " + loadedCount + " saved model(s)...");
    }
}
