package com.blockdisplay.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelActionTest {

    @Test
    void parsesConsoleAndPlayerActions() {
        ModelAction console = ModelAction.parse("console: say hola {player}");
        assertNotNull(console);
        assertEquals(ModelAction.Type.CONSOLE, console.type());
        assertEquals("say hola {player}", console.payload());

        ModelAction player = ModelAction.parse("player: warp tienda");
        assertNotNull(player);
        assertEquals(ModelAction.Type.PLAYER, player.type());
        assertEquals("warp tienda", player.payload());
    }

    @Test
    void stripsLeadingSlashFromCommands() {
        ModelAction action = ModelAction.parse("player: /spawn");
        assertNotNull(action);
        assertEquals("spawn", action.payload());
    }

    @Test
    void parsesAnimActionsAndRejectsUnknownModes() {
        assertEquals("once", ModelAction.parse("anim: once").payload());
        assertEquals("loop", ModelAction.parse("anim: LOOP").payload());
        assertEquals("stop", ModelAction.parse("anim: stop").payload());
        assertNull(ModelAction.parse("anim: backwards"));
    }

    @Test
    void rejectsMalformedActions() {
        assertNull(ModelAction.parse(null));
        assertNull(ModelAction.parse("say hola"));            // no type prefix
        assertNull(ModelAction.parse("console:"));            // empty payload
        assertNull(ModelAction.parse("console: /"));          // slash-only command
        assertNull(ModelAction.parse("op: give diamonds"));   // unknown type
        assertNull(ModelAction.parse(": say hola"));          // empty type
    }

    @Test
    void serializeRoundTrips() {
        ModelAction action = ModelAction.parse("CONSOLE:   give {player} bread 1  ");
        assertNotNull(action);
        assertEquals("console: give {player} bread 1", action.serialize());
        assertEquals(action, ModelAction.parse(action.serialize()));
    }

    @Test
    void expandReplacesAllPlaceholders() {
        ModelAction action = ModelAction.parse("console: tellraw {player} en {model} @ {x} {y} {z}");
        assertNotNull(action);
        assertEquals("tellraw Scrulius en fuente @ 10 64 -5",
                action.expand("Scrulius", "fuente", 10, 64, -5));
    }
}
