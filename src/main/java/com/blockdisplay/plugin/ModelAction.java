package com.blockdisplay.plugin;

import java.util.Locale;

/**
 * One click-action of an interactive model ("what happens when a player right-clicks it").
 * Stored in spawned.yml as plain strings ("console: say hola {player}"), same format the
 * furniture module uses for its commands. Pure (no Bukkit) — parsing and placeholder expansion
 * are unit-tested; execution lives in {@link ModelInteractListener}.
 *
 * <p>Types:
 * <ul>
 *   <li>{@code console: <cmd>} — dispatched as the console.</li>
 *   <li>{@code player: <cmd>} — performed by the clicking player.</li>
 *   <li>{@code anim: <once|loop|stop>} — drives the model's own animation.</li>
 * </ul>
 */
public record ModelAction(Type type, String payload) {

    public enum Type { CONSOLE, PLAYER, ANIM }

    /** Parse a serialized action, or return null if malformed. */
    public static ModelAction parse(String raw) {
        if (raw == null) return null;
        int colon = raw.indexOf(':');
        if (colon <= 0) return null;
        String head = raw.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String payload = raw.substring(colon + 1).trim();
        if (payload.isEmpty()) return null;

        Type type = switch (head) {
            case "console" -> Type.CONSOLE;
            case "player" -> Type.PLAYER;
            case "anim" -> Type.ANIM;
            default -> null;
        };
        if (type == null) return null;

        if (type == Type.ANIM) {
            payload = payload.toLowerCase(Locale.ROOT);
            if (!payload.equals("once") && !payload.equals("loop") && !payload.equals("stop")) return null;
        } else if (payload.startsWith("/")) {
            // Commands are stored without the slash (dispatchCommand doesn't want it).
            payload = payload.substring(1);
            if (payload.isEmpty()) return null;
        }
        return new ModelAction(type, payload);
    }

    /** The spawned.yml representation; {@code parse(serialize())} round-trips. */
    public String serialize() {
        return type.name().toLowerCase(Locale.ROOT) + ": " + payload;
    }

    /**
     * Expand the placeholders of a command payload: {player} = clicking player's name,
     * {model} = model display name, {x}/{y}/{z} = the player's block position.
     */
    public String expand(String playerName, String modelName, int x, int y, int z) {
        return payload
                .replace("{player}", playerName)
                .replace("{model}", modelName)
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z));
    }
}
