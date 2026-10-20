package dev.builddraft.model;

import java.util.*;

/** Small per-world-session MRU list; selecting an existing material moves it to the front. */
public final class RecentMaterials {
    private static final int LIMIT = 24;
    private final List<String> ids = new ArrayList<>();
    public void use(String id) {
        ids.remove(id);
        ids.add(0, id);
        if (ids.size() > LIMIT) ids.remove(ids.size() - 1);
    }
    public List<String> ids() { return List.copyOf(ids); }
}
