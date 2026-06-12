package com.blockdisplay.plugin;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlowColorsTest {

    @Test
    void resolvesNamedColorsCaseInsensitively() {
        assertEquals(Color.fromRGB(0xFF5555), GlowColors.resolve("red"));
        assertEquals(Color.fromRGB(0xFF5555), GlowColors.resolve("RED"));
        assertEquals(Color.fromRGB(0xFFAA00), GlowColors.resolve("gold"));
    }

    @Test
    void resolvesHexColors() {
        assertEquals(Color.fromRGB(0x12AB34), GlowColors.resolve("#12AB34"));
        assertEquals(Color.fromRGB(0x12AB34), GlowColors.resolve("#12ab34"));
    }

    @Test
    void rejectsUnknownAndMalformedInput() {
        assertNull(GlowColors.resolve(null));
        assertNull(GlowColors.resolve(""));
        assertNull(GlowColors.resolve("fucsia_neon"));
        assertNull(GlowColors.resolve("#12AB"));     // too short
        assertNull(GlowColors.resolve("#GGGGGG"));   // not hex
        assertNull(GlowColors.resolve("12AB34"));    // missing #
    }

    @Test
    void namesAreSortedAndCompleteForTabCompletion() {
        var names = GlowColors.names();
        assertTrue(names.contains("red"));
        assertTrue(names.contains("dark_aqua"));
        assertEquals(names.stream().sorted().toList(), names);
        // Every advertised name must resolve.
        for (String name : names) {
            assertEquals(true, GlowColors.resolve(name) != null, name + " must resolve");
        }
    }
}
