package dev.builddraft.model;

import java.util.*;

/** Structural state comparison and item units, independent of Minecraft's registry bootstrap. */
public final class ConstructionRules {
    public enum Kind { OTHER, STAIRS, CONNECTIONS, SLAB, DOOR, TALL_PLANT, BED }
    private static final Set<String> LIVE = Set.of("powered", "lit", "open", "occupied", "triggered", "enabled", "extended", "power", "distance", "persistent");
    private static final Set<String> CONNECTIONS = Set.of("north", "south", "east", "west", "up");
    public static boolean structureMatches(Kind kind, BlockSpec expected, BlockSpec actual) {
        if (!expected.id().equals(actual.id())) return false;
        for (var entry : expected.properties().entrySet()) {
            String name = entry.getKey();
            if (LIVE.contains(name) || name.equals("waterlogged") || kind == Kind.STAIRS && name.equals("shape")
                || kind == Kind.CONNECTIONS && CONNECTIONS.contains(name)) continue;
            if (!entry.getValue().equals(actual.properties().get(name))) return false;
        }
        return true;
    }
    public static boolean complete(Kind kind, BlockSpec expected, BlockSpec actual) {
        return structureMatches(kind, expected, actual) && Objects.equals(expected.properties().get("waterlogged"), actual.properties().get("waterlogged"));
    }
    public static int units(Kind kind, BlockSpec state) {
        var properties = state.properties();
        if ((kind == Kind.DOOR || kind == Kind.TALL_PLANT) && "upper".equals(properties.get("half"))) return 0;
        if (kind == Kind.BED && "head".equals(properties.get("part"))) return 0;
        if (kind == Kind.SLAB && "double".equals(properties.get("type"))) return 2;
        for (String property : List.of("layers", "candles", "pickles")) if (properties.containsKey(property)) return Integer.parseInt(properties.get(property));
        return 1;
    }
    public static int placedUnits(Kind kind, BlockSpec expected, BlockSpec actual) {
        if (structureMatches(kind, expected, actual)) return units(kind, expected);
        if (!expected.id().equals(actual.id())) return 0;
        if (kind == Kind.SLAB && "double".equals(expected.properties().get("type")) && !"double".equals(actual.properties().get("type"))
            && matchesWith(kind, expected, actual, "type")) return 1;
        for (String property : List.of("layers", "candles", "pickles")) {
            if (expected.properties().containsKey(property) && actual.properties().containsKey(property)
                && Integer.parseInt(actual.properties().get(property)) < Integer.parseInt(expected.properties().get(property))
                && matchesWith(kind, expected, actual, property)) return Integer.parseInt(actual.properties().get(property));
        }
        return 0;
    }
    private static boolean matchesWith(Kind kind, BlockSpec expected, BlockSpec actual, String property) {
        Map<String, String> adjusted = new HashMap<>(expected.properties()); adjusted.put(property, actual.properties().get(property));
        return structureMatches(kind, new BlockSpec(expected.id(), adjusted), actual);
    }
    public static boolean placementMatches(Kind kind, BlockSpec expected, BlockSpec placed) {
        int partial = placedUnits(kind, expected, placed);
        return structureMatches(kind, expected, placed) || partial > 0 && partial < units(kind, expected);
    }
}
