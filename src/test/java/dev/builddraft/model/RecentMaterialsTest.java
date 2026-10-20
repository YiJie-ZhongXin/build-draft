package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecentMaterialsTest {
    @Test void reusingAMaterialMovesItToTheFrontWithoutDuplicatingIt() {
        var recent = new RecentMaterials();
        recent.use("minecraft:stone"); recent.use("minecraft:glass"); recent.use("minecraft:stone");
        assertEquals(List.of("minecraft:stone", "minecraft:glass"), recent.ids());
    }
    @Test void onlyTheTwentyFourMostRecentMaterialsAreRetained() {
        var recent = new RecentMaterials();
        for (int i = 0; i < 30; i++) recent.use("example:block_" + i);
        assertEquals(24, recent.ids().size());
        assertEquals("example:block_29", recent.ids().get(0));
        assertEquals("example:block_6", recent.ids().get(23));
    }
    @Test void snapshotsAreImmutableAndDoNotChangeWhenAnotherMaterialIsUsed() {
        var recent = new RecentMaterials(); recent.use("minecraft:stone");
        var snapshot = recent.ids(); recent.use("minecraft:glass");
        assertEquals(List.of("minecraft:stone"), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("minecraft:dirt"));
    }
}
