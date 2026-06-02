package com.blockdisplay.plugin;

import java.util.List;
import java.util.Map;

public class ModelData {
    public Content content;

    public static class Content {
        public String version;
        public String type;
        public int project_id;
        public List<String> passengers;
        public List<String> hitbox;
        public Datapack datapack;
    }

    public static class Datapack {
        public Map<String, Map<String, List<String>>> anim_keyframes;
    }

    public boolean hasPassengers() {
        return content != null && content.passengers != null && !content.passengers.isEmpty();
    }

    public boolean hasAnimations() {
        return content != null && content.datapack != null
                && content.datapack.anim_keyframes != null
                && !content.datapack.anim_keyframes.isEmpty();
    }

    public boolean hasHitbox() {
        return content != null && content.hitbox != null && !content.hitbox.isEmpty();
    }
}
