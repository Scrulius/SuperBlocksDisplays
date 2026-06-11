package com.blockdisplay.plugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure parsing of one block-display.com keyframe command ({@code data merge entity @e[...] {...}})
 * into its mappable fields. No Bukkit types — the caller turns {@link ParsedMerge#blockDataString}
 * into a real BlockData and the tag into part UUIDs.
 *
 * <p>Strict by design: if the payload contains anything beyond the four known fields (unknown
 * keys, {@code item:} swaps), {@link #parse} returns null so the caller falls back to dispatching
 * the real command — a keyframe is never silently half-applied.
 */
public final class KeyframeParser {

    private KeyframeParser() {}

    // "data merge entity @e[type=block_display,tag=bde_0,distance=..1,limit=1,sort=nearest] {...}"
    private static final Pattern DATA_MERGE = Pattern.compile("^data merge entity @e\\[([^\\]]*)\\]\\s*(\\{.*)$");
    private static final Pattern TAG_IN_SELECTOR = Pattern.compile("tag=([^,\\]]+)");
    private static final Pattern TRANSFORMATION = Pattern.compile("transformation:\\[([^\\]]*)\\]");
    private static final Pattern INTERP_DURATION = Pattern.compile("interpolation_duration:(-?\\d+)");
    private static final Pattern START_INTERP = Pattern.compile("start_interpolation:(-?\\d+)");
    private static final Pattern BLOCK_STATE = Pattern.compile("block_state:\\{Name:\"([^\"]+)\"(?:,Properties:\\{([^}]*)\\})?\\}");
    private static final Pattern PROPERTY = Pattern.compile("([A-Za-z0-9_]+):\"([^\"]*)\"");

    /**
     * One fully understood {@code data merge}. Null fields = field not present in the merge.
     *
     * @param tag             the author tag (bde_N) the selector targets
     * @param matrixRowMajor  the 16 floats exactly as MC stores them (row-major), unscaled
     * @param blockDataString ready for {@code Bukkit.createBlockData} (e.g. "minecraft:oak_log[axis=x]")
     */
    public record ParsedMerge(String tag, float[] matrixRowMajor,
                              Integer interpDuration, Integer interpDelay, String blockDataString) {}

    /** Parse one keyframe command, or return null if it must go through the command fallback. */
    public static ParsedMerge parse(String cmd) {
        Matcher m = DATA_MERGE.matcher(cmd);
        if (!m.matches()) return null;
        String selector = m.group(1);
        String payload = m.group(2);

        Matcher tagM = TAG_IN_SELECTOR.matcher(selector);
        if (!tagM.find()) return null;
        String tag = tagM.group(1);

        String residue = payload;

        float[] matrix = null;
        Matcher tm = TRANSFORMATION.matcher(payload);
        if (tm.find()) {
            String[] raw = tm.group(1).split(",");
            if (raw.length != 16) return null;
            float[] vals = new float[16];
            try {
                for (int i = 0; i < 16; i++) {
                    String s = raw[i].trim();
                    if (s.endsWith("f") || s.endsWith("F")) s = s.substring(0, s.length() - 1);
                    vals[i] = Float.parseFloat(s);
                }
            } catch (NumberFormatException ex) {
                return null;
            }
            matrix = vals;
            residue = residue.replace(tm.group(0), "");
        }

        Integer duration = null;
        Matcher dm = INTERP_DURATION.matcher(payload);
        if (dm.find()) {
            duration = Integer.parseInt(dm.group(1));
            residue = residue.replace(dm.group(0), "");
        }

        Integer delay = null;
        Matcher sm = START_INTERP.matcher(payload);
        if (sm.find()) {
            delay = Integer.parseInt(sm.group(1));
            residue = residue.replace(sm.group(0), "");
        }

        String blockData = null;
        Matcher bm = BLOCK_STATE.matcher(payload);
        if (bm.find()) {
            StringBuilder bd = new StringBuilder(bm.group(1));
            String props = bm.group(2);
            if (props != null && !props.isEmpty()) {
                bd.append('[');
                Matcher pm = PROPERTY.matcher(props);
                boolean first = true;
                while (pm.find()) {
                    if (!first) bd.append(',');
                    bd.append(pm.group(1)).append('=').append(pm.group(2));
                    first = false;
                }
                bd.append(']');
            }
            blockData = bd.toString().toLowerCase(Locale.ROOT);
            residue = residue.replace(bm.group(0), "");
        }

        if (matrix == null && duration == null && delay == null && blockData == null) return null;

        // Anything left beyond braces/commas means the merge carries data we can't map -> fallback.
        if (!residue.replaceAll("[{}\\s,]", "").isEmpty()) return null;

        return new ParsedMerge(tag, matrix, duration, delay, blockData);
    }
}
