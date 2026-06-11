package com.blockdisplay.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelGroupTest {

    private static ModelData model(List<String> passengers, List<String> hitbox) {
        ModelData d = new ModelData();
        d.content = new ModelData.Content();
        d.content.passengers = passengers;
        d.content.hitbox = hitbox;
        return d;
    }

    @Test
    void countPartsSplitsConcatenatedPassengersAndAddsHitboxes() {
        // The API ships several parts concatenated in one passengers string.
        String twoParts = "{id:\"minecraft:block_display\",Tags:[\"bde_0\"]},"
                + "{id:\"minecraft:item_display\",Tags:[\"bde_1\"]}";
        ModelData data = model(List.of(twoParts),
                List.of("summon minecraft:interaction ~ ~ ~ {width:1f,height:2f}"));
        assertEquals(3, ModelGroup.countParts(data));
    }

    @Test
    void countPartsDoesNotSplitOnCommasInsideNbt() {
        // Commas inside the NBT (e.g. matrix floats) must not be counted as part boundaries.
        String onePart = "{id:\"minecraft:block_display\","
                + "transformation:[1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f]}";
        assertEquals(1, ModelGroup.countParts(model(List.of(onePart), null)));
    }

    @Test
    void countPartsHandlesModelWithoutHitbox() {
        ModelData data = model(List.of("{id:\"minecraft:block_display\"}"), null);
        assertEquals(1, ModelGroup.countParts(data));
    }
}
