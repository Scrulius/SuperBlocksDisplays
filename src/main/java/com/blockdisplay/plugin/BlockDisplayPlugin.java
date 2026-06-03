package com.blockdisplay.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockDisplayPlugin extends JavaPlugin {

    private ModelManager modelManager;
    private PersistenceManager persistenceManager;
    private AnimationManager animationManager;
    private SilentCommandSender silentSender;
    private CommandFeedbackFilter logFilter;
    private final Map<UUID, ModelGroup> activeGroups = new HashMap<>();

    // Config values
    private int searchRadius;
    private int cleanupRadius;
    private int purgeMaxRadius;
    private int maxModels;
    private boolean autoPlayAnimations;
    private float defaultAnimSpeed;
    private boolean defaultLoopMode;
    private float minAnimSpeed;
    private float maxAnimSpeed;

    @Override
    public void onEnable() {
        // Save default config if not present and load values
        saveDefaultConfig();
        loadConfigValues();

        // Install Log4j2 filter to suppress "Modified entity data" console spam
        this.logFilter = new CommandFeedbackFilter();
        ((Logger) LogManager.getRootLogger()).addFilter(logFilter);

        this.silentSender = new SilentCommandSender();
        this.modelManager = new ModelManager(this);
        this.persistenceManager = new PersistenceManager(this);

        // Load saved groups
        this.persistenceManager.loadSavedGroups();

        // Start animation task (every tick)
        this.animationManager = new AnimationManager(this);
        this.animationManager.runTaskTimer(this, 1L, 1L);

        // Register command with tab completer
        BdeCommand bdeCommand = new BdeCommand(this);
        getCommand("bde").setExecutor(bdeCommand);
        getCommand("bde").setTabCompleter(bdeCommand);

        getLogger().info("SuperBlocksDisplays enabled. " + activeGroups.size() + " models loaded.");
    }

    private void loadConfigValues() {
        reloadConfig();
        this.searchRadius = getConfig().getInt("search-radius", 15);
        this.cleanupRadius = getConfig().getInt("cleanup-radius", 50);
        this.purgeMaxRadius = getConfig().getInt("purge-max-radius", 10);
        this.maxModels = getConfig().getInt("max-models", -1);
        this.autoPlayAnimations = getConfig().getBoolean("auto-play-animations", true);
        this.defaultAnimSpeed = (float) getConfig().getDouble("default-animation-speed", 1.0);
        this.defaultLoopMode = getConfig().getString("default-animation-mode", "loop").equalsIgnoreCase("loop");
        this.minAnimSpeed = (float) getConfig().getDouble("min-animation-speed", 0.25);
        this.maxAnimSpeed = (float) getConfig().getDouble("max-animation-speed", 4.0);
    }

    @Override
    public void onDisable() {
        if (animationManager != null) {
            animationManager.cancel();
        }
        // Remove all display entities so they don't duplicate on restart
        int removedCount = 0;
        for (ModelGroup group : activeGroups.values()) {
            removedCount += group.getPartCount();
            group.remove(this);
        }
        activeGroups.clear();
        getLogger().info("Cleaned up " + removedCount + " entities from active models.");

        // Disable our log filter cleanly (stop() prevents it from matching)
        if (logFilter != null) {
            logFilter.stop();
        }
        getLogger().info("SuperBlocksDisplays disabled.");
    }

    // Config getters
    public int getSearchRadius() { return searchRadius; }
    public int getCleanupRadius() { return cleanupRadius; }
    public int getPurgeMaxRadius() { return purgeMaxRadius; }
    public int getMaxModels() { return maxModels; }
    public boolean isAutoPlayAnimations() { return autoPlayAnimations; }
    public float getDefaultAnimSpeed() { return defaultAnimSpeed; }
    public boolean isDefaultLoopMode() { return defaultLoopMode; }
    public float getMinAnimSpeed() { return minAnimSpeed; }
    public float getMaxAnimSpeed() { return maxAnimSpeed; }

    // Service getters
    public ModelManager getModelManager() { return modelManager; }
    public PersistenceManager getPersistenceManager() { return persistenceManager; }
    public AnimationManager getAnimationManager() { return animationManager; }
    public SilentCommandSender getSilentSender() { return silentSender; }
    public Map<UUID, ModelGroup> getActiveGroups() { return activeGroups; }

    /**
     * Find a model group by its display name (case-insensitive).
     */
    public ModelGroup findGroupByName(String name) {
        for (ModelGroup group : activeGroups.values()) {
            if (group.getDisplayName().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }
}
