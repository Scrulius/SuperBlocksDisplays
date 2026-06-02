package com.blockdisplay.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockDisplayPlugin extends JavaPlugin {

    private ModelManager modelManager;
    private PersistenceManager persistenceManager;
    private AnimationManager animationManager;
    private final Map<UUID, ModelGroup> activeGroups = new HashMap<>();

    @Override
    public void onEnable() {
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

        getLogger().info("BlockDisplayPlugin enabled. " + activeGroups.size() + " models loaded.");
    }

    @Override
    public void onDisable() {
        if (animationManager != null) {
            animationManager.cancel();
        }
        getLogger().info("BlockDisplayPlugin disabled.");
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

    public Map<UUID, ModelGroup> getActiveGroups() {
        return activeGroups;
    }
}
