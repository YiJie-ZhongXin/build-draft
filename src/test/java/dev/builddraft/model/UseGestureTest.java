package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UseGestureTest {
    @Test void shortModifierUsePressSurvivesReleaseUntilVanillaProcessesIt() {
        var gesture = new UseGesture(); gesture.capture(0, true, true);
        assertTrue(gesture.pending(100_000_000)); assertTrue(gesture.consume(100_000_000)); assertFalse(gesture.pending(100_000_001));
    }
    @Test void heldUseRepeatCannotFillAgainWithoutANewPress() {
        var gesture = new UseGesture(); gesture.capture(0, true, true);
        assertTrue(gesture.consume(1));
        assertFalse(gesture.consume(2)); assertFalse(gesture.consume(3));
        gesture.capture(4, true, true); assertTrue(gesture.consume(5));
    }
    @Test void ordinaryUseAndExpiredInputDoNotStartWork() {
        var gesture = new UseGesture(); gesture.capture(0, false, true); assertFalse(gesture.consume(1));
        gesture.capture(2, true, false); assertFalse(gesture.consume(3));
        gesture.capture(0, true, true); assertFalse(gesture.consume(250_000_000));
    }
    @Test void closingAnInterfaceOrChangingProjectionCanClearQueuedInput() {
        var gesture = new UseGesture(); gesture.capture(0, true, true); gesture.clear(); assertFalse(gesture.consume(1));
    }
}
