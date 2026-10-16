package dev.builddraft.model;

/** Minecraft camera convention: yaw 0 faces +Z, positive pitch looks down. */
public final class OrbitMath {
    public record Point(double x, double y, double z) { }
    public static Point eye(double x, double y, double z, double distance, double yaw, double pitch) {
        double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Point(x + Math.sin(a) * Math.cos(p) * distance, y + Math.sin(p) * distance,
            z - Math.cos(a) * Math.cos(p) * distance);
    }
    public static Point pan(double right, double forward, double yaw) {
        double a = Math.toRadians(yaw);
        return new Point(-Math.cos(a) * right - Math.sin(a) * forward, 0,
            -Math.sin(a) * right + Math.cos(a) * forward);
    }
    public static float wrapYaw(double yaw) { return (float) ((yaw % 360 + 360) % 360); }
    public static float clampPitch(double pitch) { return (float) Math.max(20, Math.min(85, pitch)); }
}
