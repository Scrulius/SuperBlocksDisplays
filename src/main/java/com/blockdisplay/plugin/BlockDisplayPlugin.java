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

    @Override
    public void onEnable() {
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

    public ModelManager getModelManager() {
        return modelManager;
    }

    public PersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public SilentCommandSender getSilentSender() {
        return silentSender;
    }

    public Map<UUID, ModelGroup> getActiveGroups() {
        return activeGroups;
    }

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
