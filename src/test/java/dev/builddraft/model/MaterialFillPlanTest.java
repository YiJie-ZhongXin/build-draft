package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.builddraft.model.ConstructionRules.Kind.*;

class MaterialFillPlanTest {
    private MaterialFillPlan.Candidate<String> cost(String target, int cost) {
        return new MaterialFillPlan.Candidate<>(target, cost);
    }
    @Test void wholeDraftIsSelectedInOnePlanIncludingDistantPositions() {
        List<MaterialFillPlan.Candidate<GridPos>> targets = new ArrayList<>();
        for (int i = 0; i < 32768; i++) targets.add(new MaterialFillPlan.Candidate<>(new GridPos(i * 100, 80, -i * 100), 1));
        var result = MaterialFillPlan.select(targets, 32768, false);
        assertEquals(targets, result.selected());
        assertEquals(32768, result.used());
        assertEquals(0, result.missing());
    }
    @Test void notEnoughMaterialFillsAsManyWholeStructuresAsPossible() {
        var result = MaterialFillPlan.select(List.of(cost("double slab", 2), cost("single slab", 1), cost("another single", 1)), 3, false);
        assertEquals(List.of(cost("double slab", 2), cost("single slab", 1)), result.selected());
        assertEquals(3, result.used());
        assertEquals(1, result.missing());
    }
    @Test void expensiveStructureIsNotPartiallyPlacedAndDoesNotBlockCheaperOne() {
        var result = MaterialFillPlan.select(List.of(cost("three candles", 3), cost("one candle", 1)), 2, false);
        assertEquals(List.of(cost("one candle", 1)), result.selected());
        assertEquals(1, result.used());
        assertEquals(2, result.missing()); // One remains in inventory; only two more are needed.
    }
    @Test void creativeFillsEverythingWithoutInventoryOrConsumption() {
        var result = MaterialFillPlan.select(List.of(cost("bed", 1), cost("double slab", 2)), 0, true);
        assertEquals(2, result.selected().size());
        assertEquals(0, result.used());
        assertEquals(0, result.missing());
    }
    @Test void partialSlabOnlyChargesTheMissingItem() {
        var expected = new BlockSpec("minecraft:stone_slab", Map.of("type", "double"));
        var actual = new BlockSpec("minecraft:stone_slab", Map.of("type", "top"));
        int required = ConstructionRules.units(SLAB, expected) - ConstructionRules.placedUnits(SLAB, expected, actual);
        var result = MaterialFillPlan.select(List.of(cost("remaining slab", required)), 1, false);
        assertEquals(1, result.used());
        assertEquals(1, result.selected().size());
    }
    @Test void doorAndBedEachCostOneItemForTheirWholeStructure() {
        var lower = new BlockSpec("minecraft:oak_door", Map.of("half", "lower"));
        var upper = new BlockSpec("minecraft:oak_door", Map.of("half", "upper"));
        var foot = new BlockSpec("minecraft:red_bed", Map.of("part", "foot"));
        var head = new BlockSpec("minecraft:red_bed", Map.of("part", "head"));
        assertEquals(1, ConstructionRules.units(DOOR, lower) + ConstructionRules.units(DOOR, upper));
        assertEquals(1, ConstructionRules.units(BED, foot) + ConstructionRules.units(BED, head));
        assertEquals(1, MaterialFillPlan.select(List.of(cost("both door halves", ConstructionRules.units(DOOR, lower))), 1, false).used());
        assertEquals(1, MaterialFillPlan.select(List.of(cost("both bed halves", ConstructionRules.units(BED, foot))), 1, false).used());
    }
    @Test void repairedSecondaryHalfCanUseAnAlreadyPaidPrimaryItem() {
        var result = MaterialFillPlan.select(List.of(cost("missing upper half", 0)), 0, false);
        assertEquals(1, result.selected().size());
        assertEquals(0, result.used());
    }
    @Test void differentOrientationsUseOneSharedBudget() {
        var north = new BlockSpec("minecraft:oak_stairs", Map.of("facing", "north"));
        var west = new BlockSpec("minecraft:oak_stairs", Map.of("facing", "west"));
        var result = MaterialFillPlan.select(List.of(cost("north", ConstructionRules.units(STAIRS, north)),
            cost("west", ConstructionRules.units(STAIRS, west))), 2, false);
        assertEquals(2, result.selected().size());
        assertEquals(2, result.used());
    }
    @Test void repeatedPlanAfterCompletionConsumesNothing() {
        var first = MaterialFillPlan.select(List.of(cost("unfinished", 1)), 1, false);
        assertEquals(1, first.used());
        var repeated = MaterialFillPlan.select(List.of(), 0, false);
        assertTrue(repeated.selected().isEmpty());
        assertEquals(0, repeated.used());
    }
}
