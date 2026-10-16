package dev.builddraft.model;

import dev.builddraft.model.BuildGeometry.Face;
import java.util.*;

/** Exposed, coplanar faces with the same state form one selectable extrusion region. */
public final class SurfaceExtrusion {
    public static Set<GridPos> select(Map<GridPos, BlockSpec> blocks, GridPos start, Face normal, boolean single) {
        BlockSpec state = blocks.get(start);
        if (state == null || blocks.containsKey(normal.offset(start, 1))) return Set.of();
        if (single) return Set.of(start);
        Set<GridPos> result = new LinkedHashSet<>();
        Deque<GridPos> queue = new ArrayDeque<>();
        queue.add(start); result.add(start);
        while (!queue.isEmpty()) {
            GridPos pos = queue.removeFirst();
            for (Face direction : Face.values()) {
                if (direction.axis == normal.axis) continue;
                GridPos neighbor = direction.offset(pos, 1);
                if (!result.contains(neighbor) && state.equals(blocks.get(neighbor)) && !blocks.containsKey(normal.offset(neighbor, 1))) {
                    result.add(neighbor); queue.addLast(neighbor);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }
    public static int maxSteps(Set<GridPos> selection, Face normal) {
        int max = Integer.MAX_VALUE;
        int size = normal.axis == 1 ? Blueprint.MAX_Y : Blueprint.MAX_XZ;
        int minimum = normal.axis == 1 ? Blueprint.MIN_Y : Blueprint.MIN_XZ;
        for (GridPos pos : selection) max = Math.min(max, normal.sign() > 0 ? size - 1 - BuildGeometry.value(pos, normal.axis) : BuildGeometry.value(pos, normal.axis) - minimum);
        return selection.isEmpty() ? 0 : max;
    }
    public static Map<GridPos, BlockSpec> plan(Map<GridPos, BlockSpec> blocks, Set<GridPos> selection, Face normal, int steps) {
        if (steps < 0 || steps > maxSteps(selection, normal)) throw new IllegalArgumentException("拉伸超出草稿范围");
        Map<GridPos, BlockSpec> additions = new LinkedHashMap<>();
        for (GridPos pos : selection) {
            BlockSpec state = blocks.get(pos);
            if (state == null) throw new IllegalArgumentException("选中的草稿已改变");
            for (int distance = 1; distance <= steps; distance++) {
                GridPos next = normal.offset(pos, distance);
                if (blocks.containsKey(next)) break; // Never overwrite or jump through existing design.
                additions.put(next, state);
                if (blocks.size() + additions.size() > Blueprint.MAX_BLOCKS) throw new IllegalArgumentException("蓝图方块数量超过限制");
            }
        }
        if (blocks.size() + additions.size() > Blueprint.MAX_BLOCKS) throw new IllegalArgumentException("蓝图方块数量超过限制");
        return Collections.unmodifiableMap(additions);
    }
}
