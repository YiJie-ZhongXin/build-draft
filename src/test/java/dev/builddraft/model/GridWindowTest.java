package dev.builddraft.model;

import dev.builddraft.model.BuildGeometry.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GridWindowTest {
    private static final BlockSpec STONE = new BlockSpec("minecraft:stone", Map.of());
    @Test void draggedWindowCanCrossTheOldBoundaryWithoutRewritingExistingBlocks() {
        Blueprint draft = new Blueprint();
        GridPos old = new GridPos(4, 0, 4);
        draft.put(old, STONE);
        var moved = GridWindow.drag(new GridPos(0, 0, 0), new GridPos(12, 0, 12), new GridPos(-28, 0, 62));
        assertEquals(new GridPos(-40, 0, 50), moved);
        GridPos next = new GridPos(moved.x() + 16, 0, moved.z() + 16);
        draft.put(next, STONE);
        assertEquals(STONE, draft.blocks().get(old));
        assertEquals(STONE, draft.blocks().get(next));
        draft.undo(); assertEquals(Map.of(old, STONE), draft.blocks());
        draft.redo(); assertEquals(2, draft.blocks().size());
    }
    @Test void snappingKeepsPointerOffsetAndOriginalHeight() {
        assertEquals(new GridPos(15, -20, -15), GridWindow.drag(new GridPos(10, -20, -10), new GridPos(2, 9, 3), new GridPos(7, 100, -2)));
    }
    @Test void worldEdgeClampingKeepsTheWholeGuideWindowInsideTheWorld() {
        var max = GridWindow.atWorld(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(GridWindow.WORLD_LIMIT - Blueprint.WIDTH, max.x());
        assertEquals(GridWindow.WORLD_LIMIT - Blueprint.DEPTH, max.z());
        var min = GridWindow.atWorld(Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(-GridWindow.WORLD_LIMIT, min.x());
        assertEquals(-GridWindow.WORLD_LIMIT, min.z());
    }
    @Test void rayPickingFindsBlueprintsOutsideTheOriginalGrid() {
        GridPos target = new GridPos(-55, 20, 80);
        var cells = BuildGeometry.cells(new Ray(new Vec(-54.5, 60, 80.5), new Vec(0, -1, 0)), 100);
        assertTrue(cells.contains(target));
        assertTrue(cells.size() <= 102);
    }
    @Test void oversizedRectanglesFailBeforeAllocatingTheirCells() {
        assertThrows(IllegalArgumentException.class, () -> BuildGeometry.rectangle(new GridPos(-1000, 0, -1000), new GridPos(1000, 0, 1000), new Plane(1, 0), STONE));
        assertThrows(IllegalArgumentException.class, () -> BuildGeometry.rectangle(new GridPos(Blueprint.MIN_XZ, 0, Blueprint.MIN_XZ), new GridPos(Blueprint.MAX_XZ - 1, 0, Blueprint.MAX_XZ - 1), new Plane(1, 0), STONE));
    }
    @Test void longExtrusionsRespectCapacityBeforeBuildingAnUnboundedPreview() {
        GridPos start = new GridPos(-40, 0, 50);
        assertThrows(IllegalArgumentException.class, () -> SurfaceExtrusion.plan(Map.of(start, STONE), java.util.Set.of(start), Face.EAST, 100000));
    }
}
