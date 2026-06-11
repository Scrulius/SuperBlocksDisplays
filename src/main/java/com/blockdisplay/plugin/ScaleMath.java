package com.blockdisplay.plugin;

import org.joml.Matrix4f;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure math for scaling a block-display.com model by a uniform factor.
 *
 * <p>Every part of a model is summoned exactly at the group origin: the whole shape (offset,
 * rotation and size of each piece) lives in its row-major {@code transformation:[16 floats]}.
 * Scaling the model about the origin is therefore premultiplying each matrix by
 * {@code S = diag(s,s,s,1)}, which for a row-major layout means multiplying the first 12 entries
 * (the 3x4 block: basis vectors AND translation column) by {@code s} and leaving row 3 untouched.
 *
 * <p>No Bukkit types beyond JOML (shipped with Paper) — fully unit-testable.
 */
public final class ScaleMath {

    private ScaleMath() {}

    /** transformation:[f,f,...,f] inside a part's SNBT or a keyframe payload. */
    private static final Pattern TRANSFORMATION = Pattern.compile("transformation:\\[([^\\]]*)\\]");
    /** A relative coordinate in a summon command: ~ or ~-1.25 etc. */
    private static final Pattern RELATIVE_COORD = Pattern.compile("~(-?\\d*\\.?\\d+)?");
    /** width:1.0f / height:2f inside an interaction's NBT. */
    private static final Pattern WIDTH_HEIGHT = Pattern.compile("(width|height):(-?\\d*\\.?\\d+)([fF]?)");

    /**
     * Scale a row-major 4x4 transformation about the origin: entries 0..11 multiplied by
     * {@code s}, the bottom row (12..15) untouched. Returns a new array.
     */
    public static float[] scaleMatrixRowMajor(float[] rowMajor, float s) {
        float[] out = rowMajor.clone();
        for (int i = 0; i < 12; i++) {
            out[i] *= s;
        }
        return out;
    }

    /**
     * Build the JOML matrix a Display expects from MC's row-major floats, scaled by {@code s}.
     * MC stores the matrix row-major; JOML's {@code set(float[])} reads column-major, so the
     * result must be transposed. (If models ever render warped in-game, this transpose is the
     * knob to revisit — see ANIMATION_API_REWRITE_PLAN.md.)
     */
    public static Matrix4f toMatrix(float[] rowMajor, float s) {
        float[] vals = (s == 1.0f) ? rowMajor : scaleMatrixRowMajor(rowMajor, s);
        return new Matrix4f().set(vals).transpose();
    }

    /**
     * Scale every {@code transformation:[...]} found in a part's SNBT (or any payload string).
     * Anything that is not a well-formed 16-float list is left untouched. A factor of 1 returns
     * the input unchanged.
     */
    public static String scalePartSnbt(String snbt, float s) {
        if (s == 1.0f) return snbt;
        Matcher m = TRANSFORMATION.matcher(snbt);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            float[] vals = parseFloats(m.group(1));
            if (vals == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            vals = scaleMatrixRowMajor(vals, s);
            m.appendReplacement(sb, Matcher.quoteReplacement("transformation:[" + formatFloats(vals) + "]"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Scale an authored hitbox summon command: relative coordinates ({@code ~dx ~dy ~dz}) and the
     * interaction's {@code width}/{@code height} are multiplied by {@code s}. Everything else
     * (entity type, other NBT) passes through unchanged.
     */
    public static String scaleHitboxCommand(String cmd, float s) {
        if (s == 1.0f) return cmd;

        Matcher rel = RELATIVE_COORD.matcher(cmd);
        StringBuilder sb = new StringBuilder();
        while (rel.find()) {
            String num = rel.group(1);
            if (num == null) {
                // A bare "~" is offset 0 - scaling changes nothing.
                rel.appendReplacement(sb, Matcher.quoteReplacement(rel.group(0)));
            } else {
                double scaled = Double.parseDouble(num) * s;
                rel.appendReplacement(sb, Matcher.quoteReplacement("~" + trimNumber(scaled)));
            }
        }
        rel.appendTail(sb);

        Matcher wh = WIDTH_HEIGHT.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (wh.find()) {
            double scaled = Double.parseDouble(wh.group(2)) * s;
            wh.appendReplacement(out, Matcher.quoteReplacement(
                    wh.group(1) + ":" + trimNumber(scaled) + wh.group(3)));
        }
        wh.appendTail(out);
        return out.toString();
    }

    /** Parse a comma-separated list of 16 floats with optional f/F suffix; null if malformed. */
    private static float[] parseFloats(String list) {
        String[] raw = list.split(",");
        if (raw.length != 16) return null;
        float[] vals = new float[16];
        try {
            for (int i = 0; i < 16; i++) {
                String t = raw[i].trim();
                if (t.endsWith("f") || t.endsWith("F")) t = t.substring(0, t.length() - 1);
                vals[i] = Float.parseFloat(t);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return vals;
    }

    private static String formatFloats(float[] vals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(trimNumber(vals[i])).append('f');
        }
        return sb.toString();
    }

    /** Plain decimal without scientific notation or a trailing ".0" — what SNBT expects. */
    private static String trimNumber(double v) {
        String s = String.format(Locale.US, "%.7f", v);
        // Strip trailing zeros, then a trailing dot ("1.2500000" -> "1.25", "3.0000000" -> "3").
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s.isEmpty() || s.equals("-") ? "0" : s;
    }
}
