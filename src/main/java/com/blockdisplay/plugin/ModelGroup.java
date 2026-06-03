package com.blockdisplay.plugin;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelGroup {
    private final UUID groupId;
    private final String modelId;
    private final String displayName;
    private final List<Entity> parts;
    private Location origin;
    private float yawOffset = 0;
    private ModelData modelData;
    private boolean animating = false;
    private boolean ready = false;
    private boolean loopAnim = true;
    private float animSpeed = 1.0f; // Animation speed multiplier (0.25x to 4x)

    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<=\\}),(?=\\{id:\"minecraft:)");
    private static final Pattern ID_PATTERN = Pattern.compile("^\\{id:\"minecraft:([^\"]+)\"");

    public ModelGroup(Location origin, String modelId, String displayName) {
        this(origin, UUID.randomUUID(), modelId, displayName);
    }

    public ModelGroup(Location origin, UUID groupId, String modelId, String displayName) {
        this.groupId = groupId;
        this.modelId = modelId;
        this.displayName = displayName;
        this.parts = new ArrayList<>();
        this.origin = origin.clone();
    }

    public void reconnectOrSpawn(ModelData modelData, BlockDisplayPlugin plugin) {
        this.modelData = modelData;
        NamespacedKey groupKey = new NamespacedKey(plugin, "group_id");
        World world = origin.getWorld();
        if (world == null) return;

        // Remove any stale entities left over from a previous session (e.g. after crash)
        for (Entity e : world.getNearbyEntities(origin, 50, 50, 50)) {
            String idStr = e.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
            if (idStr != null && idStr.equals(groupId.toString())) {
                e.remove();
            }
        }

        // Always spawn fresh
        spawn(modelData, plugin);
    }

    public void spawn(ModelData modelData, BlockDisplayPlugin plugin) {
        this.modelData = modelData;
        if (!modelData.hasPassengers()) return;
        World world = origin.getWorld();
        if (world == null) return;

        NamespacedKey groupKey = new NamespacedKey(plugin, "group_id");
        String dimension = world.getKey().toString();
        SilentCommandSender silentSender = plugin.getSilentSender();

        // Suppress feedback to OP players in-game during spawning
        Boolean originalFeedback = world.getGameRuleValue(GameRule.SEND_COMMAND_FEEDBACK);
        Boolean originalLog = world.getGameRuleValue(GameRule.LOG_ADMIN_COMMANDS);
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, false);

        for (String rawSnbt : modelData.content.passengers) {
            String[] individualParts = SPLIT_PATTERN.split(rawSnbt);

            for (String snbt : individualParts) {
                String entityType = "item_display";
                Matcher idMatcher = ID_PATTERN.matcher(snbt);
                if (idMatcher.find()) {
                    entityType = idMatcher.group(1);
                }

                UUID partUuid = UUID.randomUUID();
                long msb = partUuid.getMostSignificantBits();
                long lsb = partUuid.getLeastSignificantBits();
                String uuidSnbt = String.format(Locale.US, "UUID:[I;%d,%d,%d,%d]",
                        (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);

                String nbt = snbt.replaceFirst("\\{id:\"minecraft:[^\"]+\",?", "{");
                nbt = nbt.replaceFirst("\\{", "{" + uuidSnbt + ",");

                String cmd = String.format(Locale.US,
                        "execute in %s positioned %f %f %f run summon minecraft:%s ~ ~ ~ %s",
                        dimension, origin.getX(), origin.getY(), origin.getZ(), entityType, nbt);

                try {
                    Bukkit.dispatchCommand(silentSender, cmd);
                    Entity spawnedPart = plugin.getServer().getEntity(partUuid);
                    if (spawnedPart != null) {
                        spawnedPart.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, groupId.toString());
                        parts.add(spawnedPart);
                    } else {
                        // Entity may not be registered yet; schedule a retry on the next tick
                        final UUID retryUuid = partUuid;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            Entity retryPart = plugin.getServer().getEntity(retryUuid);
                            if (retryPart != null) {
                                retryPart.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, groupId.toString());
                                parts.add(retryPart);
                            } else {
                                plugin.getLogger().warning("Spawned part not found after retry: " + retryUuid);
                            }
                        }, 1L);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to summon part: " + e.getMessage());
                }
            }
        }

        if (modelData.hasHitbox()) {
            for (String hitboxCmd : modelData.content.hitbox) {
                UUID hitboxUuid = UUID.randomUUID();
                long msb = hitboxUuid.getMostSignificantBits();
                long lsb = hitboxUuid.getLeastSignificantBits();
                String uuidSnbt = String.format(Locale.US, "UUID:[I;%d,%d,%d,%d]",
                        (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);

                String modifiedHitboxCmd = hitboxCmd;
                if (!modifiedHitboxCmd.contains("{")) {
                    modifiedHitboxCmd += "{}";
                }
                modifiedHitboxCmd = modifiedHitboxCmd.replaceFirst("\\{", "{" + uuidSnbt + ",");

                String cmd = String.format(Locale.US,
                        "execute in %s positioned %f %f %f run %s",
                        dimension, origin.getX(), origin.getY(), origin.getZ(), modifiedHitboxCmd);
                try {
                    Bukkit.dispatchCommand(silentSender, cmd);
                    Entity spawnedHitbox = plugin.getServer().getEntity(hitboxUuid);
                    if (spawnedHitbox != null) {
                        spawnedHitbox.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, groupId.toString());
                        parts.add(spawnedHitbox);
                    } else {
                        final UUID retryUuid = hitboxUuid;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            Entity retryPart = plugin.getServer().getEntity(retryUuid);
                            if (retryPart != null) {
                                retryPart.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, groupId.toString());
                                parts.add(retryPart);
                            }
                        }, 1L);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to summon hitbox: " + e.getMessage());
                }
            }
        }

        // Restore original gamerule values
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, Boolean.TRUE.equals(originalFeedback));
        world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, Boolean.TRUE.equals(originalLog));

        // Mark as ready after a short delay so all entities are fully registered
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> this.ready = true, 3L);

        plugin.getLogger().info("Model '" + displayName + "' (" + modelId + ") spawned with " + parts.size() + " parts.");
    }

    public void remove(BlockDisplayPlugin plugin) {
        // Remove tracked parts
        for (Entity part : parts) {
            if (part != null && part.isValid()) {
                part.remove();
            }
        }
        parts.clear();

        // Sweep for any untracked entities with our group_id tag
        World world = origin.getWorld();
        if (world != null) {
            NamespacedKey groupKey = new NamespacedKey(plugin, "group_id");
            for (Entity e : world.getNearbyEntities(origin, 50, 50, 50)) {
                String idStr = e.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
                if (idStr != null && idStr.equals(groupId.toString())) {
                    e.remove();
                }
            }
        }
    }

    public void setYaw(float yaw) {
        this.yawOffset = yaw;
        for (Entity part : parts) {
            if (part != null && part.isValid()) {
                Location loc = part.getLocation();
                loc.setYaw(yaw);
                part.teleport(loc);
            }
        }
    }

    // Getters and setters
    public UUID getGroupId() { return groupId; }
    public Location getOrigin() { return origin; }
    public String getModelId() { return modelId; }
    public String getDisplayName() { return displayName; }
    public ModelData getModelData() { return modelData; }
    public List<Entity> getParts() { return parts; }
    public int getPartCount() { return parts.size(); }
    public boolean isReady() { return ready; }
    public boolean isAnimating() { return animating; }
    public void setAnimating(boolean animating) { this.animating = animating; }
    public float getYawOffset() { return yawOffset; }
    public float getAnimSpeed() { return animSpeed; }
    public void setAnimSpeed(float animSpeed) { this.animSpeed = Math.max(0.25f, Math.min(4.0f, animSpeed)); }
    public boolean isLoopAnim() { return loopAnim; }
    public void setLoopAnim(boolean loopAnim) { this.loopAnim = loopAnim; }
}
