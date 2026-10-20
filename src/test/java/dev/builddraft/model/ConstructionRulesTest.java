package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static dev.builddraft.model.ConstructionRules.*;

class ConstructionRulesTest {
    private BlockSpec state(String block, String... properties) {
        var map = new java.util.HashMap<String, String>();
        for (int i = 0; i < properties.length; i += 2) map.put(properties[i], properties[i + 1]);
        return new BlockSpec("minecraft:" + block, map);
    }
    @Test void orientationMustMatchButNormalStairShapeUpdatesAreAllowed() {
        var stairs = state("oak_stairs", "facing", "east", "shape", "straight");
        assertFalse(complete(Kind.STAIRS, stairs, state("oak_stairs", "facing", "west", "shape", "straight")));
        assertTrue(complete(Kind.STAIRS, stairs, state("oak_stairs", "facing", "east", "shape", "inner_left")));
    }
    @Test void doubleSlabRequiresTwoItemsAndPartialProgressIsOneItem() {
        var single = state("oak_slab", "type", "bottom"); var doubled = state("oak_slab", "type", "double");
        assertEquals(2, units(Kind.SLAB, doubled)); assertEquals(1, placedUnits(Kind.SLAB, doubled, single));
        assertTrue(placementMatches(Kind.SLAB, doubled, single)); assertFalse(complete(Kind.SLAB, doubled, single));
        assertTrue(complete(Kind.SLAB, doubled, doubled));
        assertFalse(placementMatches(Kind.SLAB, state("oak_slab", "type", "top"), single));
    }
    @Test void doorsAndBedsCostOnlyOneItemPerStructure() {
        assertEquals(1, units(Kind.DOOR, state("oak_door", "half", "lower")));
        assertEquals(0, units(Kind.DOOR, state("oak_door", "half", "upper")));
        assertEquals(1, units(Kind.BED, state("red_bed", "part", "foot")));
        assertEquals(0, units(Kind.BED, state("red_bed", "part", "head")));
    }
    @Test void waterloggedNeedsWaterEvenWhenBlockItemIsAlreadyPlaced() {
        var dry = state("oak_slab", "waterlogged", "false", "type", "bottom");
        var wet = state("oak_slab", "waterlogged", "true", "type", "bottom");
        assertFalse(complete(Kind.SLAB, wet, dry)); assertEquals(1, placedUnits(Kind.SLAB, wet, dry));
        assertTrue(complete(Kind.SLAB, wet, wet));
    }
    @Test void connectionsAndPowerDoNotInvalidateAnOtherwiseCorrectBlock() {
        assertTrue(complete(Kind.CONNECTIONS, state("oak_fence", "north", "false"), state("oak_fence", "north", "true")));
        assertTrue(complete(Kind.OTHER, state("redstone_lamp", "lit", "false"), state("redstone_lamp", "lit", "true")));
        assertFalse(complete(Kind.OTHER, state("stone"), state("glass")));
        assertFalse(complete(Kind.OTHER, state("oak_log", "axis", "x"), state("oak_log", "axis", "y")));
    }
    @Test void stackedMaterialsCountRemainingIndividualItems() {
        assertEquals(8, units(Kind.OTHER, state("snow", "layers", "8")));
        assertEquals(3, placedUnits(Kind.OTHER, state("snow", "layers", "8"), state("snow", "layers", "3")));
        assertEquals(4, units(Kind.OTHER, state("candle", "candles", "4")));
    }
}
