package dev.builddraft.model;

import java.util.*;

/** Surface directions and ray traversal in blueprint-local block coordinates. */
public final class BuildGeometry {
    public record Vec(double x, double y, double z) {
        public double axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public Vec scale(double scale) { return new Vec(x * scale, y * scale, z * scale); }
    }
    public enum Face {
        WEST(-1, 0, 0), EAST(1, 0, 0), DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1);
        public final int x, y, z, axis;
        Face(int x, int y, int z) { this.x = x; this.y = y; this.z = z; axis = x != 0 ? 0 : y != 0 ? 1 : 2; }
        public GridPos offset(GridPos pos, int amount) { return new GridPos(pos.x() + x * amount, pos.y() + y * amount, pos.z() + z * amount); }
    }
    public record Ray(Vec origin, Vec direction) {
        public Ray {
            double length = Math.sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z);
            if (!Double.isFinite(length) || length < 1e-10 || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) throw new IllegalArgumentException("无效射线");
            direction = direction.scale(1 / length);
        }
        public Vec at(double distance) { return origin.add(direction.scale(distance)); }
    }
    /** Traversal is bounded by ray distance, independent of the visual reference grid. */
    public static List<GridPos> cells(Ray ray, double maxDistance) {
        double enter = 0, leave = maxDistance;
        int[] minimum = {Blueprint.MIN_XZ, Blueprint.MIN_Y, Blueprint.MIN_XZ}, sizes = {Blueprint.MAX_XZ, Blueprint.MAX_Y, Blueprint.MAX_XZ};
        for (int axis = 0; axis < 3; axis++) {
            double origin = ray.origin.axis(axis), direction = ray.direction.axis(axis);
            if (Math.abs(direction) < 1e-10) {
                if (origin < minimum[axis] || origin >= sizes[axis]) return List.of();
            } else {
                double a = (minimum[axis] - origin) / direction, b = (sizes[axis] - origin) / direction;
                enter = Math.max(enter, Math.min(a, b)); leave = Math.min(leave, Math.max(a, b));
                if (enter >= leave) return List.of();
            }
        }
        Vec first = ray.at(enter + 1e-7);
        int[] cell = {floor(first.x), floor(first.y), floor(first.z)};
        int[] step = new int[3];
        double[] next = new double[3], delta = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double direction = ray.direction.axis(axis);
            step[axis] = direction > 0 ? 1 : direction < 0 ? -1 : 0;
            delta[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction);
            next[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY : (cell[axis] + (step[axis] > 0 ? 1 : 0) - ray.origin.axis(axis)) / direction;
        }
        List<GridPos> result = new ArrayList<>();
        while (cell[0] >= minimum[0] && cell[0] < sizes[0] && cell[1] >= minimum[1] && cell[1] < sizes[1] && cell[2] >= minimum[2] && cell[2] < sizes[2]) {
            result.add(new GridPos(cell[0], cell[1], cell[2]));
            double distance = Math.min(next[0], Math.min(next[1], next[2]));
            if (distance > leave || distance > maxDistance) break;
            for (int axis = 0; axis < 3; axis++) if (next[axis] <= distance + 1e-9) { cell[axis] += step[axis]; next[axis] += delta[axis]; }
        }
        return result;
    }
    private static int floor(double number) { return (int) Math.floor(number); }
}
