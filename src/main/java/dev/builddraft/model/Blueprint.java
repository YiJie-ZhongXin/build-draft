package dev.builddraft.model;

import java.util.*;

/** World-independent sparse blueprint with atomic edits and bounded undo history. */
public final class Blueprint {
    public static final int WIDTH = 32, MIN_Y = -4096, MAX_Y = 4096, HEIGHT = MAX_Y - MIN_Y, DEPTH = 32, MAX_BLOCKS = 32768;
    public static final int MIN_XZ = -60000000, MAX_XZ = 60000000;
    private static final int HISTORY_LIMIT = 128;
    private final Map<GridPos, BlockSpec> blocks = new LinkedHashMap<>();
    private final Deque<Change> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private long revision;

    private record Change(Map<GridPos, BlockSpec> before, Map<GridPos, BlockSpec> after) { }

    public Map<GridPos, BlockSpec> blocks() { return Collections.unmodifiableMap(blocks); }
    public long revision() { return revision; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void clear() {
        Map<GridPos, BlockSpec> erase = new LinkedHashMap<>();
        blocks.keySet().forEach(pos -> erase.put(pos, null));
        edit(erase);
    }
    public boolean contains(GridPos p) {
        return inBounds(p);
    }
    public static boolean inBounds(GridPos p) {
        return p != null && p.x() >= MIN_XZ && p.x() < MAX_XZ && p.y() >= MIN_Y && p.y() < MAX_Y && p.z() >= MIN_XZ && p.z() < MAX_XZ;
    }

    public void edit(Map<GridPos, BlockSpec> requested) {
        Map<GridPos, BlockSpec> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        int size = blocks.size();
        for (var e : requested.entrySet()) {
            if (!contains(e.getKey())) throw new IllegalArgumentException("超出支持的草稿坐标范围");
            BlockSpec old = blocks.get(e.getKey()), next = e.getValue();
            if (Objects.equals(old, next)) continue;
            if (old == null && next != null) size++;
            if (old != null && next == null) size--;
            before.put(e.getKey(), old);
            after.put(e.getKey(), next);
        }
        if (size > MAX_BLOCKS) throw new IllegalArgumentException("蓝图最多保存 " + MAX_BLOCKS + " 个方块");
        if (after.isEmpty()) return;
        apply(after);
        undo.addLast(new Change(before, after));
        if (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear();
    }

    public void put(GridPos p, BlockSpec spec) {
        Map<GridPos, BlockSpec> edit = new HashMap<>();
        edit.put(p, spec);
        edit(edit);
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        Change c = undo.removeLast();
        apply(c.before());
        redo.addLast(c);
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        Change c = redo.removeLast();
        apply(c.after());
        undo.addLast(c);
        return true;
    }

    public void replace(Map<GridPos, BlockSpec> loaded) {
        if (loaded.size() > MAX_BLOCKS || loaded.keySet().stream().anyMatch(p -> !contains(p)) || loaded.containsValue(null)) {
            throw new IllegalArgumentException("蓝图数据超出限制");
        }
        blocks.clear();
        blocks.putAll(loaded);
        undo.clear();
        redo.clear();
        revision++;
    }

    private void apply(Map<GridPos, BlockSpec> values) {
        values.forEach((p, b) -> { if (b == null) blocks.remove(p); else blocks.put(p, b); });
        revision++;
    }
}
