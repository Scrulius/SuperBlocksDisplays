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

    public void saveGroup(ModelGroup group) {
        String path = "groups." + group.getGroupId().toString();
        config.set(path + ".model", group.getModelId());
        config.set(path + ".name", group.getDisplayName());
        config.set(path + ".world", group.getOrigin().getWorld().getName());
        config.set(path + ".x", group.getOrigin().getX());
        config.set(path + ".y", group.getOrigin().getY());
        config.set(path + ".z", group.getOrigin().getZ());
        config.set(path + ".yaw", group.getYawOffset());
        config.set(path + ".animating", group.isAnimating());
        config.set(path + ".loopAnim", group.isLoopAnim());
        config.set(path + ".animSpeed", group.getAnimSpeed());
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
            String displayName = config.getString(path + ".name", "unnamed_" + uuidStr.substring(0, 6));
            String worldName = config.getString(path + ".world");
            World world = plugin.getServer().getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found. Skipping model " + displayName);
                continue;
            }

            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");
            boolean animating = config.getBoolean(path + ".animating", false);
            boolean loopAnim = config.getBoolean(path + ".loopAnim", true);
            float animSpeed = (float) config.getDouble(path + ".animSpeed", 1.0);

            Location loc = new Location(world, x, y, z, yaw, 0);

            plugin.getModelManager().fetchModel(modelId).thenAccept(modelData -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (modelData != null) {
                        ModelGroup group = new ModelGroup(loc, groupId, modelId, displayName);
                        group.reconnectOrSpawn(modelData, plugin);
                        group.setYaw(yaw);

                        group.setLoopAnim(loopAnim);
                        group.setAnimSpeed(animSpeed);
                        // Auto-start animation if it was animating before and has animations
                        if (animating && modelData.hasAnimations()) {
                            group.setAnimating(true);
                        }

                        plugin.getActiveGroups().put(groupId, group);
                        plugin.getLogger().info("Loaded model '" + displayName + "' (" + modelId + ")");
                    } else {
                        plugin.getLogger().warning("Could not reload model '" + displayName + "' (" + modelId + ") - may be expired on API.");
                    }
                });
            });
            loadedCount++;
        }
        plugin.getLogger().info("Loading " + loadedCount + " saved model(s)...");
    }
}
