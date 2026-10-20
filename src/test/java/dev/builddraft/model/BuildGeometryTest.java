package dev.builddraft.model;

import dev.builddraft.model.BuildGeometry.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildGeometryTest {
    private static final BlockSpec STONE = new BlockSpec("minecraft:stone", Map.of());

    @Test void rayFindsUpperBlocksBeforeGroundWithoutActiveLayer() {
        var cells = BuildGeometry.cells(new Ray(new Vec(5.5, 90, 5.5), new Vec(0, -1, 0)), 256);
        assertEquals(new GridPos(5, 89, 5), cells.get(0));
        assertEquals(new GridPos(5, -167, 5), cells.get(cells.size() - 1));
        Set<GridPos> blocks = Set.of(new GridPos(5, 2, 5), new GridPos(5, 40, 5));
        assertEquals(new GridPos(5, 40, 5), cells.stream().filter(blocks::contains).findFirst().orElseThrow());
    }
    @Test void sideRaysRespectTraversalDirectionAndRange() {
        var forward = BuildGeometry.cells(new Ray(new Vec(-10, 2.5, 3.5), new Vec(1, 0, 0)), 12.5);
        assertEquals(new GridPos(-10, 2, 3), forward.get(0));
        assertEquals(new GridPos(2, 2, 3), forward.get(forward.size() - 1));
        assertEquals(13, forward.size());
        var reverse = BuildGeometry.cells(new Ray(new Vec(40, 2.5, 3.5), new Vec(-1, 0, 0)), 256);
        assertEquals(new GridPos(39, 2, 3), reverse.get(0));
        assertEquals(new GridPos(-217, 2, 3), reverse.get(reverse.size() - 1));
    }
    @Test void diagonalTiesDoNotSelectVoxelsOnlyTouchedAtACorner() {
        var cells = BuildGeometry.cells(new Ray(new Vec(-1, -1, -1), new Vec(1, 1, 1)), 256);
        assertEquals(new GridPos(-1, -1, -1), cells.get(0));
        assertEquals(new GridPos(146, 146, 146), cells.get(cells.size() - 1));
        for (var cell : cells) assertTrue(cell.x() == cell.y() && cell.y() == cell.z());
        assertEquals(cells.size(), new HashSet<>(cells).size());
    }
    @Test void raysParallelToTheVolumeAndPointingAwayHaveNoCells() {
        assertTrue(BuildGeometry.cells(new Ray(new Vec(Blueprint.MIN_XZ - 1, 2, 3), new Vec(0, 1, 0)), 256).isEmpty());
        assertTrue(BuildGeometry.cells(new Ray(new Vec(Blueprint.MIN_XZ - 1, 2, 3), new Vec(-1, 0, 0)), 256).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Ray(new Vec(0, 0, 0), new Vec(0, 0, 0)));
    }
    @Test void adjacentFacesSupportEveryBuildDirection() {
        GridPos p = new GridPos(5, 5, 5);
        Set<GridPos> neighbors = new HashSet<>();
        for (Face face : Face.values()) neighbors.add(face.offset(p, 1));
        assertEquals(Set.of(new GridPos(4, 5, 5), new GridPos(6, 5, 5),
            new GridPos(5, 4, 5), new GridPos(5, 6, 5), new GridPos(5, 5, 4), new GridPos(5, 5, 6)), neighbors);
    }
    @Test void negativeHeightRaycastsFindTheNearestDraftSurface() {
        var ray = new Ray(new Vec(4.5, 30, 7.5), new Vec(0, -1, 0));
        Blueprint b = new Blueprint();
        b.put(new GridPos(4, 20, 7), STONE);
        b.put(new GridPos(4, -40, 7), STONE);
        var cells = BuildGeometry.cells(ray, 256);
        assertEquals(new GridPos(4, 20, 7), cells.stream().filter(b.blocks()::containsKey).findFirst().orElseThrow());
        b.put(new GridPos(4, 20, 7), null);
        assertEquals(new GridPos(4, -40, 7), cells.stream().filter(b.blocks()::containsKey).findFirst().orElseThrow());
        b.undo();
        assertEquals(new GridPos(4, 20, 7), cells.stream().filter(b.blocks()::containsKey).findFirst().orElseThrow());
    }
}
