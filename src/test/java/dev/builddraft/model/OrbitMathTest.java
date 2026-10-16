package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbitMathTest {
    @Test void orbitAlwaysKeepsDistanceFromTarget() {
        for (int yaw = -720; yaw <= 720; yaw += 45) for (int pitch = 20; pitch <= 85; pitch += 5) {
            var eye = OrbitMath.eye(13, 64, -7, 32, yaw, pitch);
            double distance = Math.sqrt(Math.pow(eye.x() - 13, 2) + Math.pow(eye.y() - 64, 2) + Math.pow(eye.z() + 7, 2));
            assertEquals(32, distance, 1e-8);
            assertTrue(eye.y() > 64);
        }
    }
    @Test void fullRotationReturnsToSameEyePosition() {
        var a = OrbitMath.eye(0, 0, 0, 32, 17, 55);
        var b = OrbitMath.eye(0, 0, 0, 32, 377, 55);
        assertEquals(a.x(), b.x(), 1e-8); assertEquals(a.y(), b.y(), 1e-8); assertEquals(a.z(), b.z(), 1e-8);
    }
    @Test void panFollowsCameraOrientation() {
        var south = OrbitMath.pan(0, 2, 0); assertEquals(2, south.z(), 1e-8);
        var west = OrbitMath.pan(0, 2, 90); assertEquals(-2, west.x(), 1e-8);
        var right = OrbitMath.pan(2, 0, 90); assertEquals(-2, right.z(), 1e-8);
    }
    @Test void pitchAvoidsHorizonAndSingularVerticalView() {
        assertEquals(20, OrbitMath.clampPitch(-90)); assertEquals(85, OrbitMath.clampPitch(100));
        assertEquals(359, OrbitMath.wrapYaw(-1)); assertEquals(1, OrbitMath.wrapYaw(721));
    }
}
