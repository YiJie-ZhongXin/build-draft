package dev.builddraft.model;

import java.util.*;

/** Classification policy independent of Minecraft, with overlap and explicit fallback. */
public final class BlockCategoryRules {
    public enum Function {
        ALL("全部"), BUILDING("建筑"), COLORED("彩色"), NATURAL("自然"), FUNCTIONAL("功能"), REDSTONE("红石"), OTHER("其他");
        public final String label;
        Function(String label) { this.label = label; }
    }
    public enum Type {
        ALL("全部"), STAIRS("楼梯"), SLABS("台阶"), FENCES("围挡"), DOORS_GLASS("门窗"), LIGHTS("光源"), CONTAINERS("容器"), PLANTS("植物"), FLUIDS("流体"), OTHER("其他");
        public final String label;
        Type(String label) { this.label = label; }
    }
    public enum Source { ALL, VANILLA, MODDED }

    public record Membership(Set<Function> functions, Set<Type> types) {
        public Membership { functions = Set.copyOf(functions); types = Set.copyOf(types); }
    }

    public static Membership classify(Set<Function> creativeGroups, Set<Type> detectedTypes, boolean natural, boolean redstone) {
        var functions = EnumSet.noneOf(Function.class);
        functions.addAll(creativeGroups);
        functions.remove(Function.ALL); functions.remove(Function.OTHER);
        var types = EnumSet.noneOf(Type.class);
        types.addAll(detectedTypes);
        types.remove(Type.ALL); types.remove(Type.OTHER);
        // Custom mod tabs do not map to vanilla tabs; infer broad uses from block traits.
        if (functions.isEmpty()) {
            if (types.contains(Type.STAIRS) || types.contains(Type.SLABS) || types.contains(Type.FENCES) || types.contains(Type.DOORS_GLASS)) functions.add(Function.BUILDING);
            if (natural || types.contains(Type.PLANTS) || types.contains(Type.FLUIDS)) functions.add(Function.NATURAL);
            if (types.contains(Type.LIGHTS) || types.contains(Type.CONTAINERS)) functions.add(Function.FUNCTIONAL);
        }
        if (redstone) functions.add(Function.REDSTONE);
        if (functions.isEmpty()) functions.add(Function.OTHER);
        if (types.isEmpty()) types.add(Type.OTHER);
        functions.add(Function.ALL); types.add(Type.ALL);
        return new Membership(functions, types);
    }

    public static boolean matches(String id, String name, String query, Source source, Membership membership, Function function, Type type) {
        boolean vanilla = id.startsWith("minecraft:");
        if (source == Source.VANILLA && !vanilla || source == Source.MODDED && vanilla) return false;
        if (!membership.functions().contains(function) || !membership.types().contains(type)) return false;
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || id.toLowerCase(Locale.ROOT).contains(needle) || name.toLowerCase(Locale.ROOT).contains(needle);
    }
}
