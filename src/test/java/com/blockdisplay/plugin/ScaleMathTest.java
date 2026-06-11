package com.blockdisplay.plugin;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ScaleMathTest {

    /** Row-major: identity rotation, scale 0.5 per axis, translation (2, 3, -4). */
    private static final float[] SAMPLE = {
            0.5f, 0f, 0f, 2f,
            0f, 0.5f, 0f, 3f,
            0f, 0f, 0.5f, -4f,
            0f, 0f, 0f, 1f
    };

    @Test
    void scaleMatrixScalesBasisAndTranslationButNotBottomRow() {
        float[] out = ScaleMath.scaleMatrixRowMajor(SAMPLE, 2f);
        assertArrayEquals(new float[]{
                1f, 0f, 0f, 4f,
                0f, 1f, 0f, 6f,
                0f, 0f, 1f, -8f,
                0f, 0f, 0f, 1f
        }, out);
        // Input untouched (defensive copy).
        assertEquals(0.5f, SAMPLE[0]);
    }

    @Test
    void toMatrixPutsRowMajorTranslationWhereJomlExpectsIt() {
        // MC stores row-major (translation in indices 3/7/11); JOML stores column-major
        // (translation in m30/m31/m32). This is the transpose the animation engine relies on.
        Matrix4f m = ScaleMath.toMatrix(SAMPLE, 1f);
        assertEquals(2f, m.m30());
        assertEquals(3f, m.m31());
        assertEquals(-4f, m.m32());

        // The unit-cube origin corner maps to the translation; (1,1,1) adds the scaled basis.
        Vector3f corner = m.transformPosition(new Vector3f(1f, 1f, 1f));
        assertEquals(2.5f, corner.x, 1e-6);
        assertEquals(3.5f, corner.y, 1e-6);
        assertEquals(-3.5f, corner.z, 1e-6);
    }

    @Test
    void toMatrixWithScaleGrowsTheWholeTransformAboutTheOrigin() {
        Matrix4f m = ScaleMath.toMatrix(SAMPLE, 2f);
        Vector3f corner = m.transformPosition(new Vector3f(1f, 1f, 1f));
        // Exactly 2x the unscaled result: offset AND size both scale.
        assertEquals(5f, corner.x, 1e-6);
        assertEquals(7f, corner.y, 1e-6);
        assertEquals(-7f, corner.z, 1e-6);
    }

    @Test
    void scalePartSnbtScalesTheMatrixAndPreservesEverythingElse() {
        String snbt = "{id:\"minecraft:block_display\",transformation:"
                + "[0.5f,0f,0f,0.125f,0f,0.5f,0f,0.25f,0f,0f,0.5f,-0.0625f,0f,0f,0f,1f],"
                + "Tags:[\"bde_0\"]}";
        String out = ScaleMath.scalePartSnbt(snbt, 2f);
        assertEquals("{id:\"minecraft:block_display\",transformation:"
                + "[1f,0f,0f,0.25f,0f,1f,0f,0.5f,0f,0f,1f,-0.125f,0f,0f,0f,1f],"
                + "Tags:[\"bde_0\"]}", out);
    }

    @Test
    void scalePartSnbtAtFactorOneIsANoOp() {
        String snbt = "{transformation:[1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f]}";
        assertSame(snbt, ScaleMath.scalePartSnbt(snbt, 1f));
    }

    @Test
    void scalePartSnbtLeavesMalformedMatricesUntouched() {
        // 3 floats is not a transformation matrix - pass through rather than corrupt.
        String snbt = "{transformation:[1f,2f,3f],Tags:[\"bde_0\"]}";
        assertEquals(snbt, ScaleMath.scalePartSnbt(snbt, 2f));
    }

    @Test
    void scaleHitboxCommandScalesOffsetsAndDimensions() {
        String cmd = "summon minecraft:interaction ~0.5 ~ ~-1.25 {width:1.0f,height:2f,response:1b}";
        String out = ScaleMath.scaleHitboxCommand(cmd, 2f);
        assertEquals("summon minecraft:interaction ~1 ~ ~-2.5 {width:2f,height:4f,response:1b}", out);
    }

    @Test
    void scaleHitboxCommandShrinksToo() {
        String cmd = "summon minecraft:interaction ~2 ~1 ~ {width:4f,height:3f}";
        String out = ScaleMath.scaleHitboxCommand(cmd, 0.5f);
        assertEquals("summon minecraft:interaction ~1 ~0.5 ~ {width:2f,height:1.5f}", out);
    }

    @Test
    void scaleHitboxCommandAtFactorOneIsANoOp() {
        String cmd = "summon minecraft:interaction ~1 ~ ~1 {width:1f,height:1f}";
        assertSame(cmd, ScaleMath.scaleHitboxCommand(cmd, 1f));
    }
}
