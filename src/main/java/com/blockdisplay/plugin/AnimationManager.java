package com.blockdisplay.plugin;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles animation playback for all active model groups.
 *
 * <p>Keyframes from block-display.com are {@code data merge entity @e[tag=bde_N,...]} commands.
 * Instead of dispatching them (which needs gamerule juggling, a silent sender and a Log4j filter
 * to stay quiet), each model's keyframes are compiled ONCE into native Paper Display API calls:
 * the {@code bde_N} selector tag resolves to concrete part UUIDs at compile time, and per tick we
 * just call {@link Display#setTransformationMatrix}, {@link Display#setInterpolationDuration},
 * {@link Display#setInterpolationDelay} and {@link BlockDisplay#setBlock} on them. This is exactly
 * what a datapack's function context achieves (functions run with feedback suppressed natively) —
 * zero commands, zero gamerule toggles, zero log spam on the hot path.
 *
 * <p>Commands the API cannot express without NMS (e.g. a keyframe swapping an item_display's
 * {@code item:}, or non-merge commands like playsound) fall back to batched dispatch through the
 * old silent-sender path. In practice block-display.com models animate only transformation,
 * interpolation and block_state, so the fallback list is empty.
 */
public class AnimationManager extends BukkitRunnable {

    private final BlockDisplayPlugin plugin;
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Float> accumulators = new ConcurrentHashMap<>();
    private final Map<UUID, CompiledAnim> compiledCache = new ConcurrentHashMap<>();

    public AnimationManager(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    /** One keyframe command compiled to direct API calls on its target parts. Null fields = field not present in the merge. */
    private record FrameAction(List<UUID> targets, Matrix4f matrix, Integer interpDuration, Integer interpDelay, BlockData block) {}

    /** All work for one animation tick: native actions plus any commands we could not translate. */
    private record Frame(List<FrameAction> actions, List<String> fallbackCommands) {}

    private record CompiledAnim(String animName, int maxTick, Map<Integer, Frame> frames) {}

    @Override
    public void run() {
        // Untranslatable commands collected for this tick, grouped by world (gamerules toggled once per world).
        Map<World, List<String>> fallbackBatch = null;
        fallbackBatch = tickAll(plugin.getActiveGroups().values(), fallbackBatch);
        fallbackBatch = tickAll(plugin.getFurnitureAnimGroups().values(), fallbackBatch);

        if (fallbackBatch != null) {
            for (Map.Entry<World, List<String>> e : fallbackBatch.entrySet()) {
                dispatchBatch(e.getKey(), e.getValue());
            }
        }
    }

    private Map<World, List<String>> tickAll(Iterable<ModelGroup> groups, Map<World, List<String>> fallbackBatch) {
        for (ModelGroup group : groups) {

            if (!group.isAnimating()) continue;
            if (!group.isReady()) continue;
            ModelData data = group.getModelData();
            if (data == null || !data.hasAnimations()) continue;

            String animName = group.getCurrentAnim();
            if (animName == null) animName = data.defaultAnimName();
            Map<String, List<String>> anim = data.getAnimation(animName);
            if (anim == null) continue;

            World world = group.getOrigin().getWorld();
            if (world == null) continue;

            UUID gid = group.getGroupId();
            CompiledAnim compiled = compiledCache.get(gid);
            if (compiled == null || !compiled.animName().equals(animName)) {
                // First play, or the group switched to another named animation -> (re)compile.
                compiled = compile(group, animName, anim);
                compiledCache.put(gid, compiled);
            }
            int maxTick = compiled.maxTick();
            if (maxTick == 0) continue;

            float speed = group.getAnimSpeed();
            int tick = tickCounters.getOrDefault(gid, 0);
            float accum = accumulators.getOrDefault(gid, 0.0f) + speed;
            int framesToAdvance = (int) accum;
            accumulators.put(gid, accum - framesToAdvance);

            boolean stopped = false;
            for (int i = 0; i < framesToAdvance; i++) {
                int currentAnimTick = tick % (maxTick + 1);

                Frame frame = compiled.frames().get(currentAnimTick);
                if (frame != null) {
                    applyFrame(frame);
                    if (!frame.fallbackCommands().isEmpty()) {
                        if (fallbackBatch == null) fallbackBatch = new HashMap<>();
                        fallbackBatch.computeIfAbsent(world, w -> new ArrayList<>()).addAll(frame.fallbackCommands());
                    }
                }
                tick++;

                // In "once" mode, stop after rendering the final frame (not before it).
                if (currentAnimTick == maxTick && !group.isLoopAnim()) {
                    group.setAnimating(false);
                    if (group.isAdminManaged()) {
                        plugin.getPersistenceManager().saveGroup(group);
                    }
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
        return fallbackBatch;
    }

    /** Apply one frame's native actions to whatever parts currently resolve to live Display entities. */
    private void applyFrame(Frame frame) {
        for (FrameAction a : frame.actions()) {
            for (UUID id : a.targets()) {
                Entity e = Bukkit.getEntity(id);
                if (!(e instanceof Display d) || !d.isValid()) continue;
                if (a.interpDelay() != null) d.setInterpolationDelay(a.interpDelay());
                if (a.interpDuration() != null) d.setInterpolationDuration(a.interpDuration());
                if (a.matrix() != null) d.setTransformationMatrix(a.matrix());
                if (a.block() != null && d instanceof BlockDisplay bd) bd.setBlock(a.block());
            }
        }
    }

    /**
     * Bake a model's keyframes. Each {@code data merge} we fully understand becomes a
     * {@link FrameAction} with its targets pre-resolved from the group's tag->UUID map;
     * anything else is wrapped for command dispatch (scoped to this group, origin baked in).
     */
    private CompiledAnim compile(ModelGroup group, String animName, Map<String, List<String>> anim) {
        int maxTick = 0;
        Map<Integer, Frame> frames = new HashMap<>();

        for (Map.Entry<String, List<String>> e : anim.entrySet()) {
            int t;
            try {
                t = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (t > maxTick) maxTick = t;

            List<FrameAction> actions = new ArrayList<>();
            List<String> fallback = new ArrayList<>();
            for (String cmd : e.getValue()) {
                FrameAction action = tryParse(cmd, group.getPartsByTag(), group.getScale());
                if (action != null) {
                    if (!action.targets().isEmpty()) {
                        actions.add(action);
                    }
                    // Selector tag matches no known part: vanilla would no-op too, drop it.
                } else {
                    fallback.add(bakeFallback(group, cmd));
                }
            }
            frames.put(t, new Frame(List.copyOf(actions), List.copyOf(fallback)));
        }

        if (group.getScale() != 1.0f && frames.values().stream().anyMatch(f -> !f.fallbackCommands().isEmpty())) {
            // Fallback commands carry the model's original (unscaled) data; a scaled group playing
            // them would flicker back to 1x. No known model hits this (0 fallbacks in practice).
            plugin.getLogger().warning("Model '" + group.getDisplayName() + "' is scaled x" + group.getScale()
                    + " but its animation has untranslatable keyframes - those frames will render unscaled.");
        }

        return new CompiledAnim(animName, maxTick, frames);
    }

    /**
     * Translate one {@code data merge entity} command into API calls, or return null to use the
     * command fallback (parsing strictness lives in {@link KeyframeParser}). The group's scale is
     * baked into the compiled matrix so animating a scaled model keeps its size.
     */
    private FrameAction tryParse(String cmd, Map<String, List<UUID>> partsByTag, float scale) {
        KeyframeParser.ParsedMerge parsed = KeyframeParser.parse(cmd);
        if (parsed == null) return null;

        Matrix4f matrix = (parsed.matrixRowMajor() != null)
                ? ScaleMath.toMatrix(parsed.matrixRowMajor(), scale) : null;

        BlockData block = null;
        if (parsed.blockDataString() != null) {
            try {
                block = Bukkit.createBlockData(parsed.blockDataString());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        List<UUID> targets = partsByTag.get(parsed.tag());
        return new FrameAction(targets == null ? List.of() : List.copyOf(targets),
                matrix, parsed.interpDuration(), parsed.interpDelay(), block);
    }

    /** Wrap an untranslatable command the old way: group-scoped selector + origin/dimension prefix. */
    private String bakeFallback(ModelGroup group, String cmd) {
        World world = group.getOrigin().getWorld();
        String scoped = cmd.replace("@e[", "@e[tag=" + group.getAnimTag() + ",");
        return String.format(Locale.US,
                "execute in %s positioned %f %f %f run %s",
                world.getKey().toString(),
                group.getOrigin().getX(), group.getOrigin().getY(), group.getOrigin().getZ(),
                scoped);
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

    /** Drop the compiled keyframes for a group (e.g. after /bde move changes the baked fallback origin). */
    public void invalidateCompiled(UUID groupId) {
        compiledCache.remove(groupId);
    }

    public void removeGroup(UUID groupId) {
        tickCounters.remove(groupId);
        accumulators.remove(groupId);
        compiledCache.remove(groupId);
    }
}
