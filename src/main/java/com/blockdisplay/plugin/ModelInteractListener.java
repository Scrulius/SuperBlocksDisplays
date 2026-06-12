package com.blockdisplay.plugin;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a model's click-actions when a player right-clicks any of its Interaction entities —
 * either an authored hitbox shipped with the model or the {@code /bde clickbox} target.
 *
 * <p>Only admin-managed groups react (the group_id PDC resolves through activeGroups); furniture
 * anchors carry different keys and are handled by FurnitureListener.
 */
public class ModelInteractListener implements Listener {

    private static final long COOLDOWN_MS = 600;

    private final BlockDisplayPlugin plugin;
    private final NamespacedKey groupKey;
    // player UUID -> (group UUID -> last click millis); pruned implicitly (tiny, admin models only).
    private final Map<UUID, Map<UUID, Long>> lastClick = new HashMap<>();

    public ModelInteractListener(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
        this.groupKey = new NamespacedKey(plugin, "group_id");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // The event fires once per hand; act on the main hand only.
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction)) return;

        String idStr = clicked.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
        if (idStr == null) return;
        ModelGroup group;
        try {
            group = plugin.getActiveGroups().get(UUID.fromString(idStr));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (group == null || group.getActions().isEmpty()) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Map<UUID, Long> perGroup = lastClick.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Long last = perGroup.get(group.getGroupId());
        if (last != null && now - last < COOLDOWN_MS) return;
        perGroup.put(group.getGroupId(), now);

        for (ModelAction action : group.getActions()) {
            execute(group, action, player);
        }
    }

    private void execute(ModelGroup group, ModelAction action, Player player) {
        switch (action.type()) {
            case ANIM -> {
                ModelData data = group.getModelData();
                if (data == null || !data.hasAnimations()) return;
                switch (action.payload()) {
                    case "stop" -> group.setAnimating(false);
                    case "loop", "once" -> {
                        group.setLoopAnim(action.payload().equals("loop"));
                        group.setAnimating(true);
                        plugin.getAnimationManager().resetTick(group.getGroupId());
                    }
                }
                // Click-driven animation state is transient by design: not persisted, so a
                // restart brings the model back in its configured idle state.
            }
            case CONSOLE, PLAYER -> {
                String cmd = action.expand(player.getName(), group.getDisplayName(),
                        player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                        player.getLocation().getBlockZ());
                try {
                    if (action.type() == ModelAction.Type.CONSOLE) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    } else {
                        player.performCommand(cmd);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Click-action of model '" + group.getDisplayName()
                            + "' failed (" + cmd + "): " + e.getMessage());
                }
            }
        }
    }
}
