package dev.builddraft.model;

import dev.builddraft.model.BuildGeometry.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GridWindowTest {
    private static final BlockSpec STONE = new BlockSpec("minecraft:stone", Map.of());
    @Test void buildingOutsideTheReferenceGridPreservesExistingBlocksAndHistory() {
        Blueprint draft = new Blueprint();
        GridPos old = new GridPos(4, 0, 4);
        draft.put(old, STONE);
        GridPos next = new GridPos(-24, 0, 66);
        draft.put(next, STONE);
        assertEquals(STONE, draft.blocks().get(old));
        assertEquals(STONE, draft.blocks().get(next));
        draft.undo(); assertEquals(Map.of(old, STONE), draft.blocks());
        draft.redo(); assertEquals(2, draft.blocks().size());
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
}
