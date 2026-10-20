package dev.builddraft.model;

/** Collision-free camera motion, independent of player input, world blocks and frame rate. */
public final class FreeCameraMath {
    public record Point(double x, double y, double z) { }
    public static Point step(double right, double forward, double up, double yaw, double pitch, double speed, double seconds) {
        double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double x = -Math.cos(a) * right - Math.sin(a) * Math.cos(p) * forward;
        double y = up - Math.sin(p) * forward;
        double z = -Math.sin(a) * right + Math.cos(a) * Math.cos(p) * forward;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-8) return new Point(0, 0, 0);
        double scale = speed * seconds / Math.max(1, length);
        return new Point(x * scale, y * scale, z * scale);
    }
    public static float wrapYaw(double yaw) { return (float) ((yaw % 360 + 360) % 360); }
    public static float clampPitch(double pitch) { return (float) Math.max(-90, Math.min(90, pitch)); }
}
