package dev.builddraft;

/** Grabs the editor cursor without closing its Screen or changing player key states. */
public interface DraftMouseControl {
    void builddraft$setCaptured(boolean captured);
}
