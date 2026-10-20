package dev.builddraft.model;

/** Bounds for the fixed visual reference grid at the blueprint origin. */
public record GridWindow(int x, int z) {
    public static final int WORLD_LIMIT = 29999984;
    public static GridWindow atWorld(int x, int z) {
        return new GridWindow(Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT - Blueprint.WIDTH, x)),
            Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT - Blueprint.DEPTH, z)));
    }
    public int centerX() { return x + Blueprint.WIDTH / 2; }
    public int centerZ() { return z + Blueprint.DEPTH / 2; }
}
