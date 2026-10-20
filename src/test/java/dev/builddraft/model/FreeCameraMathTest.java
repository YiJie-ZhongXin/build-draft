package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeCameraMathTest {
    private static double length(FreeCameraMath.Point point) {
        return Math.sqrt(point.x() * point.x() + point.y() * point.y() + point.z() * point.z());
    }
    @Test void forwardFollowsYawAndPitchRatherThanAHorizontalOrbitPlane() {
        var south = FreeCameraMath.step(0, 1, 0, 0, 0, 8, .05);
        assertEquals(0, south.x(), 1e-8); assertEquals(.4, south.z(), 1e-8);
        var west = FreeCameraMath.step(0, 1, 0, 90, 0, 8, .05);
        assertEquals(-.4, west.x(), 1e-8); assertEquals(0, west.z(), 1e-8);
        var down = FreeCameraMath.step(0, 1, 0, 123, 90, 8, .05);
        assertEquals(-.4, down.y(), 1e-8); assertEquals(.4, length(down), 1e-8);
        assertEquals(.4, FreeCameraMath.step(0, 1, 0, 0, -90, 8, .05).y(), 1e-8);
    }
    @Test void strafeAndVerticalMovementStayIndependentOfLookPitch() {
        var right = FreeCameraMath.step(1, 0, 0, 0, 80, 8, .05);
        assertEquals(-.4, right.x(), 1e-8); assertEquals(0, right.y(), 1e-8);
        assertEquals(.4, FreeCameraMath.step(0, 0, 1, 270, 80, 8, .05).y(), 1e-8);
        assertEquals(-.4, FreeCameraMath.step(0, 0, -1, 270, -80, 8, .05).y(), 1e-8);
    }
    @Test void diagonalFlightCannotExceedTheConfiguredSpeed() {
        for (int pitch = -90; pitch <= 90; pitch += 10) {
            assertTrue(length(FreeCameraMath.step(1, 1, 1, 40, pitch, 8, .05)) <= .40000001);
        }
        assertEquals(.4, length(FreeCameraMath.step(1, 1, 0, 0, 0, 8, .05)), 1e-8);
    }
    @Test void flightDistanceDependsOnElapsedTimeRatherThanKeyRepeatOrFrames() {
        var tick = FreeCameraMath.step(0, 1, 0, 45, -30, 8, .05);
        var second = FreeCameraMath.step(0, 1, 0, 45, -30, 8, 1);
        assertEquals(second.x(), tick.x() * 20, 1e-8);
        assertEquals(second.y(), tick.y() * 20, 1e-8);
        assertEquals(second.z(), tick.z() * 20, 1e-8);
        assertEquals(3 * length(tick), length(FreeCameraMath.step(0, 1, 0, 45, -30, 24, .05)), 1e-8);
    }
    @Test void releasedOrOpposingMovementHasNoResidualDrift() {
        assertEquals(0, length(FreeCameraMath.step(0, 0, 0, 45, -30, 8, .05)));
        assertEquals(0, length(FreeCameraMath.step(0, 1, 1, 0, 90, 8, .05)));
    }
    @Test void pitchCanLookStraightUpAndDownAndYawWraps() {
        assertEquals(-90, FreeCameraMath.clampPitch(-100)); assertEquals(90, FreeCameraMath.clampPitch(100));
        assertEquals(0, FreeCameraMath.clampPitch(0)); assertEquals(359, FreeCameraMath.wrapYaw(-1));
        assertEquals(1, FreeCameraMath.wrapYaw(721));
    }
}
