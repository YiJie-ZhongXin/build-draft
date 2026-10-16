package dev.builddraft.model;

import java.util.Map;
import java.util.TreeMap;

/** Serialized registry identifier and all block-state properties. No world data or NBT. */
public record BlockSpec(String id, Map<String, String> properties) {
    public BlockSpec {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("无效方块标识");
        }
        if (properties == null || properties.size() > 32) throw new IllegalArgumentException("无效方块属性");
        properties = Map.copyOf(new TreeMap<>(properties));
    }
}
