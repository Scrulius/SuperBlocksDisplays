package com.blockdisplay.plugin;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles animation playback for all active model groups.
 * Supports per-group speed control via a float accumulator.
 *
 * <p>Hot path (runs every tick), so per-group keyframe data is compiled once into ready-to-dispatch
 * commands and cached: the model's origin, dimension and per-group scoping tag are baked in, so no
 * string formatting happens per tick. Commands are batched per world and the command-feedback
 * gamerules are toggled at most once per world per tick instead of once per model per frame.
 */
public class AnimationManager extends BukkitRunnable {

    private final BlockDisplayPlugin plugin;
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Float> accumulators = new ConcurrentHashMap<>();
    private final Map<UUID, CompiledAnim> compiledCache = new ConcurrentHashMap<>();

    public AnimationManager(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    /** A model's "default" animation, pre-baked into dispatch-ready commands keyed by tick. */
    private static final class CompiledAnim {
        final int maxTick;
        final Map<Integer, List<String>> framesByTick;

        CompiledAnim(int maxTick, Map<Integer, List<String>> framesByTick) {
            this.maxTick = maxTick;
            this.framesByTick = framesByTick;
        }
    }

    @Override
    public void run() {
        // Collect every command to run this tick, grouped by world, so we toggle gamerules once per world.
        Map<World, List<String>> batched = null;

        for (Map.Entry<UUID, ModelGroup> entry : plugin.getActiveGroups().entrySet()) {
            ModelGroup group = entry.getValue();

            if (!group.isAnimating()) continue;
            if (!group.isReady()) continue;
            ModelData data = group.getModelData();
            if (data == null || !data.hasAnimations()) continue;

            Map<String, List<String>> anim = data.content.datapack.anim_keyframes.get("default");
            if (anim == null) continue;

            World world = group.getOrigin().getWorld();
            if (world == null) continue;

            UUID gid = group.getGroupId();
            CompiledAnim compiled = compiledCache.computeIfAbsent(gid, k -> compile(group, anim));
            int maxTick = compiled.maxTick;
            if (maxTick == 0) continue;

            float speed = group.getAnimSpeed();
            int tick = tickCounters.getOrDefault(gid, 0);
            float accum = accumulators.getOrDefault(gid, 0.0f) + speed;
            int framesToAdvance = (int) accum;
            accumulators.put(gid, accum - framesToAdvance);

            boolean stopped = false;
            for (int i = 0; i < framesToAdvance; i++) {
                int currentAnimTick = tick % (maxTick + 1);

                List<String> frame = compiled.framesByTick.get(currentAnimTick);
                if (frame != null && !frame.isEmpty()) {
                    if (batched == null) batched = new HashMap<>();
                    batched.computeIfAbsent(world, w -> new ArrayList<>()).addAll(frame);
                }
                tick++;

                // In "once" mode, stop after rendering the final frame (not before it).
                if (currentAnimTick == maxTick && !group.isLoopAnim()) {
                    group.setAnimating(false);
                    plugin.getPersistenceManager().saveGroup(group);
                    tickCounters.remove(gid);
                    accumulators.remove(gid);
                    stopped = true;
                    break;
                }
            }

            if (!stopped) {
                tickCounters.put(gid, tick);
            }
        }

        if (batched != null) {
            for (Map.Entry<World, List<String>> e : batched.entrySet()) {
                dispatchBatch(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Bake a model's keyframes into dispatch-ready commands. Each command gets this group's unique
     * scoping tag injected into every entity selector and is wrapped in the {@code execute in ...
     * positioned ...} prefix using the (immutable) model origin. Done once per group, then cached.
     */
    private CompiledAnim compile(ModelGroup group, Map<String, List<String>> anim) {
        String groupTag = group.getAnimTag();
        World world = group.getOrigin().getWorld();
        String dimension = world.getKey().toString();
        double x = group.getOrigin().getX();
        double y = group.getOrigin().getY();
        double z = group.getOrigin().getZ();

        int maxTick = 0;
        Map<Integer, List<String>> framesByTick = new HashMap<>();

        for (Map.Entry<String, List<String>> e : anim.entrySet()) {
            int t;
            try {
                t = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (t > maxTick) maxTick = t;

            List<String> baked = new ArrayList<>(e.getValue().size());
            for (String cmd : e.getValue()) {
                // Scope every entity selector to this group so it only touches its own parts.
                String scoped = cmd.replace("@e[", "@e[tag=" + groupTag + ",");
                baked.add(String.format(Locale.US,
                        "execute in %s positioned %f %f %f run %s",
                        dimension, x, y, z, scoped));
            }
            framesByTick.put(t, baked);
        }

        return new CompiledAnim(maxTick, framesByTick);
    }

    private void dispatchBatch(World world, List<String> commands) {
        SilentCommandSender silentSender = plugin.getSilentSender();

        // Suppress command feedback to in-game OPs for the duration of this batch only.
        Boolean originalFeedback = world.getGameRuleValue(GameRule.SEND_COMMAND_FEEDBACK);
        Boolean originalLog = world.getGameRuleValue(GameRule.LOG_ADMIN_COMMANDS);
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, false);
        try {
            for (String cmd : commands) {
                try {
                    Bukkit.dispatchCommand(silentSender, cmd);
                } catch (Exception ignored) {
                }
            }
        } finally {
            world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, Boolean.TRUE.equals(originalFeedback));
            world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, Boolean.TRUE.equals(originalLog));
        }
    }

    public void resetTick(UUID groupId) {
        tickCounters.remove(groupId);
        accumulators.remove(groupId);
    }

    public void removeGroup(UUID groupId) {
        tickCounters.remove(groupId);
        accumulators.remove(groupId);
        compiledCache.remove(groupId);
    }
}
