package com.blockdisplay.plugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BdeCommand implements CommandExecutor, TabCompleter {

    private final BlockDisplayPlugin plugin;
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "SBD" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "spawn", "remove", "list", "rotate", "anim", "speed", "info", "clearcache", "help"
    );

    public BdeCommand(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "spawn" -> handleSpawn(player, args);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "rotate" -> handleRotate(player, args);
            case "anim" -> handleAnim(player, args);
            case "speed" -> handleSpeed(player, args);
            case "info" -> handleInfo(player, args);
            case "clearcache" -> handleClearCache(player);
            case "help" -> sendHelp(player);
            default -> player.sendMessage(PREFIX + ChatColor.RED + "Unknown command. Use /bde help");
        }

        return true;
    }

    // ========== SPAWN ==========
    private void handleSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /bde spawn <model_id>");
            return;
        }
        String modelId = args[1];
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Fetching model " + ChatColor.WHITE + modelId + ChatColor.YELLOW + "...");

        plugin.getModelManager().fetchModel(modelId).thenAccept(modelData -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (modelData == null) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Failed to load model. Check the ID is valid and not expired.");
                    return;
                }

                Location loc = player.getLocation();
                ModelGroup group = new ModelGroup(loc, modelId);
                group.spawn(modelData, plugin);
                plugin.getActiveGroups().put(group.getGroupId(), group);
                plugin.getPersistenceManager().saveGroup(group.getGroupId(), modelId, loc, 0f);

                if (modelData.hasAnimations()) {
                    group.setAnimating(true);
                }

                player.sendMessage(PREFIX + ChatColor.GREEN + "Model " + ChatColor.WHITE + modelId
                        + ChatColor.GREEN + " spawned! ID: " + ChatColor.GRAY + group.getGroupId().toString().substring(0, 8));

                if (modelData.hasAnimations()) {
                    player.sendMessage(PREFIX + ChatColor.AQUA + "✦ This model has animations! (auto-playing)");
                }
            });
        });
    }

    // ========== REMOVE ==========
    private void handleRemove(Player player, String[] args) {
        ModelGroup target;

        if (args.length >= 2) {
            target = findGroupByPartialId(args[1]);
            if (target == null && args[1].equalsIgnoreCase("nearest")) {
                target = getNearestGroup(player);
            }
        } else {
            target = getNearestGroup(player);
        }

        if (target != null) {
            target.remove();
            plugin.getAnimationManager().removeGroup(target.getGroupId());
            plugin.getActiveGroups().remove(target.getGroupId());
            plugin.getPersistenceManager().removeGroup(target.getGroupId());
            player.sendMessage(PREFIX + ChatColor.GREEN + "Model removed.");
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "No model found. Use /bde list to see active models.");
        }
    }

    // ========== LIST ==========
    private void handleList(Player player) {
        Map<UUID, ModelGroup> groups = plugin.getActiveGroups();
        if (groups.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "No active models.");
            return;
        }

        player.sendMessage(PREFIX + ChatColor.GOLD + "Active Models (" + groups.size() + "):");
        player.sendMessage(ChatColor.DARK_GRAY + "────────────────────────────────");

        for (Map.Entry<UUID, ModelGroup> entry : groups.entrySet()) {
            ModelGroup g = entry.getValue();
            String shortId = g.getGroupId().toString().substring(0, 8);
            Location loc = g.getOrigin();
            String coords = String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());

            String animStatus;
            if (g.getModelData() != null && g.getModelData().hasAnimations()) {
                if (g.isAnimating()) {
                    animStatus = ChatColor.GREEN + "▶ " + g.getAnimSpeed() + "x";
                } else {
                    animStatus = ChatColor.YELLOW + "⏸ Paused";
                }
            } else {
                animStatus = ChatColor.GRAY + "Static";
            }

            TextComponent line = new TextComponent(
                    ChatColor.GRAY + " " + shortId + " " +
                    ChatColor.WHITE + "Model #" + g.getModelId() + " " +
                    ChatColor.DARK_GRAY + "@ " + ChatColor.GRAY + coords + " " +
                    animStatus + " " +
                    ChatColor.DARK_GRAY + "[" + g.getPartCount() + " parts]"
            );
            line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/bde info " + shortId));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click for details")));
            player.spigot().sendMessage(line);
        }
    }

    // ========== ROTATE ==========
    private void handleRotate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /bde rotate <yaw> [group_id]");
            return;
        }

        float yaw;
        try {
            yaw = Float.parseFloat(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Invalid yaw. Use a number (0-360).");
            return;
        }

        ModelGroup target = (args.length >= 3) ? findGroupByPartialId(args[2]) : getNearestGroup(player);

        if (target != null) {
            target.setYaw(yaw);
            plugin.getPersistenceManager().saveGroup(target.getGroupId(), target.getModelId(), target.getOrigin(), yaw);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Model rotated to " + ChatColor.WHITE + yaw + "°");
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "No model found. Specify a group ID or stand near a model.");
        }
    }

    // ========== ANIM ==========
    private void handleAnim(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /bde anim <play|stop> [group_id]");
            return;
        }

        String subAction = args[1].toLowerCase();
        ModelGroup target = (args.length >= 3) ? findGroupByPartialId(args[2]) : getNearestGroup(player);

        if (target == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "No model found.");
            return;
        }

        if (target.getModelData() == null || !target.getModelData().hasAnimations()) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "This model has no animations.");
            return;
        }

        switch (subAction) {
            case "play" -> {
                target.setAnimating(true);
                plugin.getAnimationManager().resetTick(target.getGroupId());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Animation started ▶ (" + target.getAnimSpeed() + "x)");
            }
            case "stop" -> {
                target.setAnimating(false);
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Animation stopped ⏸");
            }
            default -> player.sendMessage(PREFIX + ChatColor.RED + "Usage: /bde anim <play|stop> [group_id]");
        }
    }

    // ========== SPEED ==========
    private void handleSpeed(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /bde speed <0.25-4.0> [group_id]");
            return;
        }

        float speed;
        try {
            speed = Float.parseFloat(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Invalid speed. Use a number between 0.25 and 4.0");
            return;
        }

        if (speed < 0.25f || speed > 4.0f) {
            player.sendMessage(PREFIX + ChatColor.RED + "Speed must be between 0.25 and 4.0");
            return;
        }

        ModelGroup target = (args.length >= 3) ? findGroupByPartialId(args[2]) : getNearestGroup(player);

        if (target == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "No model found.");
            return;
        }

        if (target.getModelData() == null || !target.getModelData().hasAnimations()) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "This model has no animations to adjust speed for.");
            return;
        }

        target.setAnimSpeed(speed);
        plugin.getAnimationManager().resetTick(target.getGroupId());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Animation speed set to " + ChatColor.WHITE + speed + "x");
    }

    // ========== INFO ==========
    private void handleInfo(Player player, String[] args) {
        ModelGroup target = (args.length >= 2) ? findGroupByPartialId(args[1]) : getNearestGroup(player);

        if (target == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "No model found.");
            return;
        }

        Location loc = target.getOrigin();
        ModelData data = target.getModelData();

        player.sendMessage(ChatColor.DARK_GRAY + "────────────────────────────────");
        player.sendMessage(PREFIX + ChatColor.GOLD + "Model Info");
        player.sendMessage(ChatColor.GRAY + " Group ID: " + ChatColor.WHITE + target.getGroupId());
        player.sendMessage(ChatColor.GRAY + " Model ID: " + ChatColor.WHITE + target.getModelId());
        player.sendMessage(ChatColor.GRAY + " Location: " + ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
        player.sendMessage(ChatColor.GRAY + " World: " + ChatColor.WHITE + loc.getWorld().getName());
        player.sendMessage(ChatColor.GRAY + " Parts: " + ChatColor.WHITE + target.getPartCount());
        player.sendMessage(ChatColor.GRAY + " Yaw: " + ChatColor.WHITE + target.getYawOffset() + "°");

        if (data != null && data.hasAnimations()) {
            String animLine = target.isAnimating()
                    ? ChatColor.GREEN + "Playing ▶ " + ChatColor.WHITE + target.getAnimSpeed() + "x"
                    : ChatColor.YELLOW + "Stopped ⏸";
            player.sendMessage(ChatColor.GRAY + " Animation: " + animLine);
        } else {
            player.sendMessage(ChatColor.GRAY + " Animation: " + ChatColor.DARK_GRAY + "None");
        }

        if (data != null && data.content != null && data.content.version != null) {
            player.sendMessage(ChatColor.GRAY + " MC Version: " + ChatColor.WHITE + data.content.version);
        }
        player.sendMessage(ChatColor.DARK_GRAY + "────────────────────────────────");
    }

    // ========== CLEARCACHE ==========
    private void handleClearCache(Player player) {
        plugin.getModelManager().clearCache();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Model cache cleared.");
    }

    // ========== HELP ==========
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.DARK_GRAY + "────────────────────────────────");
        player.sendMessage(PREFIX + ChatColor.GOLD + "SuperBlocksDisplays " + ChatColor.GRAY + "by Melonzio");
        player.sendMessage(ChatColor.DARK_GRAY + "────────────────────────────────");
        player.sendMessage(ChatColor.YELLOW + " /bde spawn <id>" + ChatColor.GRAY + " - Spawn a model");
        player.sendMessage(ChatColor.YELLOW + " /bde remove [group]" + ChatColor.GRAY + " - Remove model");
        player.sendMessage(ChatColor.YELLOW + " /bde list" + ChatColor.GRAY + " - List all active models");
        player.sendMessage(ChatColor.YELLOW + " /bde rotate <yaw> [group]" + ChatColor.GRAY + " - Rotate a model");
        player.sendMessage(ChatColor.YELLOW + " /bde anim <play|stop> [group]" + ChatColor.GRAY + " - Toggle animation");
        player.sendMessage(ChatColor.YELLOW + " /bde speed <0.25-4.0> [group]" + ChatColor.GRAY + " - Set anim speed");
        player.sendMessage(ChatColor.YELLOW + " /bde info [group]" + ChatColor.GRAY + " - Show model details");
        player.sendMessage(ChatColor.YELLOW + " /bde clearcache" + ChatColor.GRAY + " - Clear model cache");
        player.sendMessage(ChatColor.DARK_GRAY + "────────────────────────────────");
    }

    // ========== TAB COMPLETION ==========
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(SUBCOMMANDS, args[0]);
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "spawn" -> {
                if (args.length == 2) return Collections.singletonList("<model_id>");
            }
            case "remove", "info" -> {
                if (args.length == 2) {
                    List<String> options = new ArrayList<>();
                    options.add("nearest");
                    options.addAll(getGroupIdSuggestions());
                    return filterStartsWith(options, args[1]);
                }
            }
            case "rotate" -> {
                if (args.length == 2) return Arrays.asList("0", "45", "90", "135", "180", "225", "270", "315");
                if (args.length == 3) {
                    List<String> options = new ArrayList<>();
                    options.add("nearest");
                    options.addAll(getGroupIdSuggestions());
                    return filterStartsWith(options, args[2]);
                }
            }
            case "anim" -> {
                if (args.length == 2) return filterStartsWith(Arrays.asList("play", "stop"), args[1]);
                if (args.length == 3) {
                    List<String> options = new ArrayList<>();
                    options.add("nearest");
                    options.addAll(getGroupIdSuggestions());
                    return filterStartsWith(options, args[2]);
                }
            }
            case "speed" -> {
                if (args.length == 2) return Arrays.asList("0.25", "0.5", "1", "1.5", "2", "3", "4");
                if (args.length == 3) {
                    List<String> options = new ArrayList<>();
                    options.add("nearest");
                    options.addAll(getGroupIdSuggestions());
                    return filterStartsWith(options, args[2]);
                }
            }
        }

        return Collections.emptyList();
    }

    // ========== HELPERS ==========

    private ModelGroup getNearestGroup(Player player) {
        NamespacedKey groupKey = new NamespacedKey(plugin, "group_id");
        Entity nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity entity : player.getNearbyEntities(15, 15, 15)) {
            if (entity instanceof Display && entity.getPersistentDataContainer().has(groupKey, PersistentDataType.STRING)) {
                double dist = entity.getLocation().distanceSquared(player.getLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = entity;
                }
            }
        }

        if (nearest != null) {
            String uuidStr = nearest.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
            if (uuidStr != null) {
                return plugin.getActiveGroups().get(UUID.fromString(uuidStr));
            }
        }
        return null;
    }

    private ModelGroup findGroupByPartialId(String partial) {
        for (Map.Entry<UUID, ModelGroup> entry : plugin.getActiveGroups().entrySet()) {
            if (entry.getKey().toString().startsWith(partial)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<String> getGroupIdSuggestions() {
        return plugin.getActiveGroups().entrySet().stream()
                .map(e -> e.getKey().toString().substring(0, 8))
                .collect(Collectors.toList());
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
