package dev.builddraft.model;

import java.util.*;

/** Ray traversal and construction planes in blueprint-local block coordinates. */
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
        public double sign() { return x + y + z; }
    }
    public record Ray(Vec origin, Vec direction) {
        public Ray {
            double length = Math.sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z);
            if (!Double.isFinite(length) || length < 1e-10 || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) throw new IllegalArgumentException("无效射线");
            direction = direction.scale(1 / length);
        }
        public Vec at(double distance) { return origin.add(direction.scale(distance)); }
    }
    public record Plane(int axis, int coordinate, double offset) {
        public Plane(int axis, int coordinate) { this(axis, coordinate, .015); }
        public Plane { if (axis < 0 || axis > 2 || !Double.isFinite(offset) || offset < 0 || offset > 1) throw new IllegalArgumentException("无效平面"); }
        public static Plane through(GridPos pos, Face face) { return new Plane(face.axis, value(pos, face.axis), face.sign() > 0 ? .015 : .985); }
        public static Plane onFace(GridPos pos, Face face) { return new Plane(face.axis, value(pos, face.axis), face.sign() > 0 ? .985 : .015); }
        public GridPos intersect(Ray ray, double maxDistance) {
            double velocity = ray.direction.axis(axis);
            if (Math.abs(velocity) < 1e-8) return null;
            double distance = (coordinate + offset - ray.origin.axis(axis)) / velocity;
            if (distance < 0 || distance > maxDistance) return null;
            Vec p = ray.at(distance);
            int x = floor(p.x), y = floor(p.y), z = floor(p.z);
            return new GridPos(axis == 0 ? coordinate : x, axis == 1 ? coordinate : y, axis == 2 ? coordinate : z);
        }
        public String label() { return (axis == 0 ? "X" : axis == 1 ? "Y" : "Z") + "=" + coordinate; }
    }

    /** Traversal is bounded by ray distance, independent of the movable grid window. */
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
    public static int value(GridPos p, int axis) { return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z(); }
    public static boolean inside(GridPos p) {
        return Blueprint.inBounds(p);
    }
    public static Map<GridPos, BlockSpec> rectangle(GridPos a, GridPos b, Plane plane, BlockSpec state) {
        if (value(a, plane.axis) != plane.coordinate || value(b, plane.axis) != plane.coordinate) throw new IllegalArgumentException("矩形必须位于同一平面");
        if (!inside(a) || !inside(b)) throw new IllegalArgumentException("超出草稿范围");
        long sx = Math.abs((long) a.x() - b.x()) + 1, sy = Math.abs((long) a.y() - b.y()) + 1, sz = Math.abs((long) a.z() - b.z()) + 1;
        if (sx > Blueprint.MAX_BLOCKS || sy > Blueprint.MAX_BLOCKS || sz > Blueprint.MAX_BLOCKS || sx * sy * sz > Blueprint.MAX_BLOCKS)
            throw new IllegalArgumentException("矩形超过 " + Blueprint.MAX_BLOCKS + " 格，请分段编辑");
        Map<GridPos, BlockSpec> edits = new LinkedHashMap<>();
        for (int x = Math.min(a.x(), b.x()); x <= Math.max(a.x(), b.x()); x++) for (int y = Math.min(a.y(), b.y()); y <= Math.max(a.y(), b.y()); y++) for (int z = Math.min(a.z(), b.z()); z <= Math.max(a.z(), b.z()); z++) {
            GridPos p = new GridPos(x, y, z);
            if (!inside(p)) throw new IllegalArgumentException("超出草稿范围");
            edits.put(p, state);
        }
        return edits;
    }
    private static int floor(double number) { return (int) Math.floor(number); }
}
