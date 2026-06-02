package com.blockdisplay.plugin;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles animation playback for all active model groups.
 * Supports per-group speed control via a float accumulator.
 */
public class AnimationManager extends BukkitRunnable {

    private final BlockDisplayPlugin plugin;
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Float> accumulators = new ConcurrentHashMap<>();

    public AnimationManager(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Map.Entry<UUID, ModelGroup> entry : plugin.getActiveGroups().entrySet()) {
            ModelGroup group = entry.getValue();
            ModelData data = group.getModelData();

            if (!group.isAnimating()) continue;
            if (data == null || !data.hasAnimations()) continue;

            Map<String, List<String>> anim = data.content.datapack.anim_keyframes.get("default");
            if (anim == null) continue;

            int maxTick = 0;
            for (String key : anim.keySet()) {
                try {
                    int t = Integer.parseInt(key);
                    if (t > maxTick) maxTick = t;
                } catch (NumberFormatException ignored) {}
            }
            if (maxTick == 0) continue;

            UUID gid = group.getGroupId();
            float speed = group.getAnimSpeed();

            // Accumulator-based speed control
            // Speed 1.0 = 1 frame per tick (normal)
            // Speed 2.0 = 2 frames per tick (fast)
            // Speed 0.5 = 1 frame every 2 ticks (slow)
            float acc = accumulators.getOrDefault(gid, 0f) + speed;
            int framesToAdvance = (int) acc;
            accumulators.put(gid, acc - framesToAdvance);

            int tick = tickCounters.getOrDefault(gid, 0);

            for (int i = 0; i < framesToAdvance; i++) {
                int currentAnimTick = tick % (maxTick + 1);
                List<String> commands = anim.get(String.valueOf(currentAnimTick));

                if (commands != null) {
                    String dimension = group.getOrigin().getWorld().getKey().toString();
                    double x = group.getOrigin().getX();
                    double y = group.getOrigin().getY();
                    double z = group.getOrigin().getZ();

                    for (String cmd : commands) {
                        String fullCommand = String.format(Locale.US,
                                "execute in %s positioned %f %f %f run %s",
                                dimension, x, y, z, cmd);
                        try {
                            Bukkit.dispatchCommand(SilentCommandSender.getInstance(), fullCommand);
                        } catch (Exception ignored) {}
                    }
                }
                tick++;
            }

            tickCounters.put(gid, tick);
        }
    }

    public void resetTick(UUID groupId) {
        tickCounters.remove(groupId);
        accumulators.remove(groupId);
    }

    public void removeGroup(UUID groupId) {
        tickCounters.remove(groupId);
        accumulators.remove(groupId);
    }
}
