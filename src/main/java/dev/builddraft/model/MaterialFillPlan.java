package dev.builddraft.model;

import java.util.*;

/** Select whole structures from the entire snapshot using one shared material budget. */
public final class MaterialFillPlan {
    public record Candidate<T>(T target, int cost) {
        public Candidate { if (cost < 0) throw new IllegalArgumentException("Negative material cost"); }
    }
    public record Selection<T>(List<Candidate<T>> selected, int used, int missing) {
        public Selection { selected = List.copyOf(selected); }
    }
    public static <T> Selection<T> select(List<Candidate<T>> candidates, int available, boolean creative) {
        if (available < 0) throw new IllegalArgumentException("Negative inventory");
        List<Candidate<T>> selected = new ArrayList<>();
        int used = 0, missing = 0;
        for (var candidate : candidates) {
            if (creative || candidate.cost() <= available - used) {
                selected.add(candidate);
                if (!creative) used += candidate.cost();
            } else missing += candidate.cost();
        }
        return new Selection<>(selected, used, Math.max(0, missing - (available - used)));
    }
}
