package com.blockdisplay.plugin;

import org.bukkit.Color;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Named glow colors for {@code /bde glow}, plus free-form hex ({@code #RRGGBB}). Names match the
 * Minecraft chat palette so admins don't have to guess RGB values.
 */
public final class GlowColors {

    private GlowColors() {}

    private static final Map<String, Color> NAMED = Map.ofEntries(
            Map.entry("white", Color.fromRGB(0xFFFFFF)),
            Map.entry("gray", Color.fromRGB(0xAAAAAA)),
            Map.entry("dark_gray", Color.fromRGB(0x555555)),
            Map.entry("black", Color.fromRGB(0x000000)),
            Map.entry("red", Color.fromRGB(0xFF5555)),
            Map.entry("dark_red", Color.fromRGB(0xAA0000)),
            Map.entry("gold", Color.fromRGB(0xFFAA00)),
            Map.entry("yellow", Color.fromRGB(0xFFFF55)),
            Map.entry("green", Color.fromRGB(0x55FF55)),
            Map.entry("dark_green", Color.fromRGB(0x00AA00)),
            Map.entry("aqua", Color.fromRGB(0x55FFFF)),
            Map.entry("dark_aqua", Color.fromRGB(0x00AAAA)),
            Map.entry("blue", Color.fromRGB(0x5555FF)),
            Map.entry("dark_blue", Color.fromRGB(0x0000AA)),
            Map.entry("purple", Color.fromRGB(0xAA00AA)),
            Map.entry("pink", Color.fromRGB(0xFF55FF))
    );

    /** Tab-completion options (sorted names; hex is typed by hand). */
    public static List<String> names() {
        return NAMED.keySet().stream().sorted().toList();
    }

    /** Resolve a color by name or {@code #RRGGBB} hex; null if unknown/malformed. */
    public static Color resolve(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String key = raw.toLowerCase(Locale.ROOT);
        Color named = NAMED.get(key);
        if (named != null) return named;
        if (key.startsWith("#") && key.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(key.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
