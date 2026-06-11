package com.blockdisplay.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyframeParserTest {

    /** A real keyframe from a block-display.com export (api_sample2.json). */
    private static final String REAL_KEYFRAME =
            "data merge entity @e[type=item_display,tag=bde_0,distance=..1,limit=1,sort=nearest] "
            + "{transformation:[0.4998730866f,-0.0008724433f,0.0112564226f,0.125f,"
            + "0.0112531848f,0.0841799858f,-0.4980981814f,0.125f,"
            + "-0.0005130031f,0.9964501838f,0.0420891271f,-0.0625f,"
            + "0f,0f,0f,1f],interpolation_duration:0}";

    @Test
    void parsesARealKeyframe() {
        KeyframeParser.ParsedMerge p = KeyframeParser.parse(REAL_KEYFRAME);
        assertNotNull(p);
        assertEquals("bde_0", p.tag());
        assertNotNull(p.matrixRowMajor());
        assertEquals(16, p.matrixRowMajor().length);
        assertEquals(0.4998730866f, p.matrixRowMajor()[0]);
        assertEquals(0.125f, p.matrixRowMajor()[3]);
        assertEquals(1f, p.matrixRowMajor()[15]);
        assertEquals(0, p.interpDuration());
        assertNull(p.interpDelay());
        assertNull(p.blockDataString());
    }

    @Test
    void parsesBlockStateWithProperties() {
        KeyframeParser.ParsedMerge p = KeyframeParser.parse(
                "data merge entity @e[type=block_display,tag=bde_3,distance=..1,limit=1,sort=nearest] "
                + "{block_state:{Name:\"minecraft:oak_log\",Properties:{axis:\"x\",waterlogged:\"false\"}}}");
        assertNotNull(p);
        assertEquals("bde_3", p.tag());
        assertEquals("minecraft:oak_log[axis=x,waterlogged=false]", p.blockDataString());
        assertNull(p.matrixRowMajor());
    }

    @Test
    void parsesStartInterpolationAsDelay() {
        KeyframeParser.ParsedMerge p = KeyframeParser.parse(
                "data merge entity @e[tag=bde_1] {interpolation_duration:5,start_interpolation:0}");
        assertNotNull(p);
        assertEquals(5, p.interpDuration());
        assertEquals(0, p.interpDelay());
    }

    @Test
    void refusesPayloadWithUnknownData() {
        // item: swaps can't be expressed through the Display API - must go to the command fallback.
        assertNull(KeyframeParser.parse(
                "data merge entity @e[tag=bde_0] {interpolation_duration:0,item:{id:\"minecraft:stone\"}}"));
    }

    @Test
    void refusesNonMergeCommands() {
        assertNull(KeyframeParser.parse("playsound minecraft:block.note_block.bell master @a"));
        assertNull(KeyframeParser.parse("data modify entity @e[tag=bde_0] Glowing set value 1b"));
    }

    @Test
    void refusesSelectorWithoutTag() {
        assertNull(KeyframeParser.parse(
                "data merge entity @e[type=block_display,limit=1] {interpolation_duration:0}"));
    }

    @Test
    void refusesMalformedMatrix() {
        // 15 floats instead of 16.
        assertNull(KeyframeParser.parse(
                "data merge entity @e[tag=bde_0] {transformation:[1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,1f]}"));
    }

    @Test
    void refusesEmptyPayload() {
        assertNull(KeyframeParser.parse("data merge entity @e[tag=bde_0] {}"));
    }
}
