package dev.builddraft.model;

/** A single captured modifier/use press; held-use repeat cannot submit another fill. */
public final class UseGesture {
    private long until = Long.MIN_VALUE;
    public void capture(long now, boolean modifierDown, boolean usePress) {
        if (modifierDown && usePress) until = now + 250_000_000L;
    }
    public boolean pending(long now) { return now < until; }
    public boolean consume(long now) { boolean pending = pending(now); clear(); return pending; }
    public void clear() { until = Long.MIN_VALUE; }
}
