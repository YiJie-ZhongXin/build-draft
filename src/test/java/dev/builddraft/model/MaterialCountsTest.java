package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialCountsTest {
    @Test void mergesDifferentStatesBeforeSubtractingTheSharedInventory() {
        var rows = MaterialCounts.tally(List.of(new MaterialCounts.Requirement("stairs", 10, 3), new MaterialCounts.Requirement("stairs", 12, 4)), Map.of("stairs", 9));
        assertEquals(List.of(new MaterialCounts.Row("stairs", 22, 7, 15, 9, 6)), rows);
    }
    @Test void completedAndSpareInventoryNeverProduceNegativeShortages() {
        assertEquals(new MaterialCounts.Row("stone", 64, 20, 44, 30, 14), MaterialCounts.tally(List.of(new MaterialCounts.Requirement("stone", 64, 20)), Map.of("stone", 30)).get(0));
        assertEquals(0, MaterialCounts.tally(List.of(new MaterialCounts.Requirement("stone", 64, 64)), Map.of("stone", 500)).get(0).missing());
    }
    @Test void noInventoryAndPartialDoubleSlabCountInItemUnits() {
        var row = MaterialCounts.tally(List.of(new MaterialCounts.Requirement("slab", 2, 1)), Map.of()).get(0);
        assertEquals(new MaterialCounts.Row("slab", 2, 1, 1, 0, 1), row);
    }
    @Test void emptyInputAndInvalidCountsAreHandled() {
        assertTrue(MaterialCounts.tally(List.of(), Map.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MaterialCounts.Requirement("stone", 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new MaterialCounts.Requirement("stone", -1, 0));
    }
}
