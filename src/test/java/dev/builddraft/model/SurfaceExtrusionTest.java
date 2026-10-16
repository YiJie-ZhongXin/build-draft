package dev.builddraft.model;

import dev.builddraft.model.BuildGeometry.Face;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceExtrusionTest {
    private static final BlockSpec STONE = new BlockSpec("minecraft:stone", Map.of());
    private static final BlockSpec WOOD = new BlockSpec("minecraft:oak_planks", Map.of());
    private static final GridPos START = new GridPos(5, 0, 5);

    @Test void connectedCoplanarSameMaterialFacesBecomeOneWall() {
        Blueprint b = new Blueprint();
        for (int x = 5; x <= 8; x++) b.put(new GridPos(x, 0, 5), STONE);
        b.put(new GridPos(9, 0, 5), WOOD); b.put(new GridPos(5, 0, 7), STONE);
        var selected = SurfaceExtrusion.select(b.blocks(), START, Face.UP, false);
        assertEquals(4, selected.size());
        var plan = SurfaceExtrusion.plan(b.blocks(), selected, Face.UP, 5);
        assertEquals(20, plan.size()); assertEquals(6, b.blocks().size()); // Preview has no side effects.
        b.edit(plan); assertEquals(26, b.blocks().size());
        b.undo(); assertEquals(6, b.blocks().size());
        b.redo(); assertEquals(26, b.blocks().size());
    }
    @Test void singleFaceModifierProducesOneColumn() {
        var blocks = Map.of(START, STONE, new GridPos(6, 0, 5), STONE);
        var selected = SurfaceExtrusion.select(blocks, START, Face.UP, true);
        assertEquals(Set.of(START), selected);
        assertEquals(3, SurfaceExtrusion.plan(blocks, selected, Face.UP, 3).size());
    }
    @Test void coveredFacesAreNotSelectedAndDoNotConnectAcrossAMaterialBoundary() {
        var blocks = Map.of(START, STONE, new GridPos(6, 0, 5), STONE, new GridPos(6, 1, 5), WOOD, new GridPos(7, 0, 5), STONE);
        assertEquals(Set.of(START), SurfaceExtrusion.select(blocks, START, Face.UP, false));
        assertTrue(SurfaceExtrusion.select(blocks, new GridPos(6, 0, 5), Face.UP, false).isEmpty());
    }
    @Test void horizontalAndDownwardExtrusionsCopyOriginalStates() {
        GridPos start = new GridPos(10, 10, 10);
        var state = new BlockSpec("minecraft:oak_log", Map.of("axis", "z"));
        var blocks = Map.of(start, state);
        for (Face face : Face.values()) {
            var plan = SurfaceExtrusion.plan(blocks, Set.of(start), face, 2);
            assertEquals(Map.of(face.offset(start, 1), state, face.offset(start, 2), state), plan);
        }
    }
    @Test void stopsAtExistingDraftAndDoesNotOverwriteOrJumpThroughIt() {
        var obstacle = new GridPos(5, 2, 5);
        var blocks = Map.of(START, STONE, obstacle, WOOD);
        var plan = SurfaceExtrusion.plan(blocks, Set.of(START), Face.UP, 6);
        assertEquals(Map.of(new GridPos(5, 1, 5), STONE), plan);
        assertEquals(WOOD, blocks.get(obstacle));
    }
    @Test void boundariesAreEnforcedBeforeAnyChange() {
        GridPos top = new GridPos(0, Blueprint.MAX_Y - 1, 0);
        var blocks = Map.of(top, STONE);
        assertEquals(0, SurfaceExtrusion.maxSteps(Set.of(top), Face.UP));
        assertEquals(Blueprint.HEIGHT - 1, SurfaceExtrusion.maxSteps(Set.of(top), Face.DOWN));
        assertThrows(IllegalArgumentException.class, () -> SurfaceExtrusion.plan(blocks, Set.of(top), Face.UP, 1));
        assertTrue(SurfaceExtrusion.plan(blocks, Set.of(top), Face.UP, 0).isEmpty());
    }
    @Test void oversizedPreviewFailsWithoutChangingTheBlueprint() {
        Map<GridPos, BlockSpec> blocks = new HashMap<>();
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) blocks.put(new GridPos(x, y, z), STONE);
        GridPos start = new GridPos(0, 31, 0);
        assertThrows(IllegalArgumentException.class, () -> SurfaceExtrusion.plan(blocks, Set.of(start), Face.UP, 1));
        assertEquals(Blueprint.MAX_BLOCKS, blocks.size());
    }
}
