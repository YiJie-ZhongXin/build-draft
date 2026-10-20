package dev.builddraft.model;

import java.util.*;

/** Counts item units, after combining all states that use the same inventory item. */
public final class MaterialCounts {
    public record Requirement(String item, int total, int completed) {
        public Requirement {
            if (item == null || total < 0 || completed < 0 || completed > total) throw new IllegalArgumentException("无效材料数量");
        }
    }
    public record Row(String item, int total, int completed, int remaining, int available, int missing) { }
    public static List<Row> tally(List<Requirement> requirements, Map<String, Integer> inventory) {
        Map<String, int[]> sums = new TreeMap<>();
        requirements.forEach(r -> {
            int[] sum = sums.computeIfAbsent(r.item(), id -> new int[2]);
            sum[0] += r.total(); sum[1] += r.completed();
        });
        return sums.entrySet().stream().map(e -> {
            int total = e.getValue()[0], done = e.getValue()[1], remaining = total - done;
            int available = Math.max(0, inventory.getOrDefault(e.getKey(), 0));
            return new Row(e.getKey(), total, done, remaining, available, Math.max(0, remaining - available));
        }).toList();
    }
}
