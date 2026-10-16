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
    @Test void horizontalAndVerticalPlanesStayOnTheirLockedCoordinate() {
        assertEquals(new GridPos(4, 10, 7), new Plane(1, 10).intersect(new Ray(new Vec(4.2, 30, 7.8), new Vec(0, -1, 0)), 256));
        assertEquals(new GridPos(8, 20, 7), new Plane(0, 8).intersect(new Ray(new Vec(-10, 20.2, 7.8), new Vec(1, 0, 0)), 256));
        assertEquals(new GridPos(4, 20, 9), new Plane(2, 9).intersect(new Ray(new Vec(4.2, 20.2, -10), new Vec(0, 0, 1)), 256));
        assertNull(new Plane(1, 10).intersect(new Ray(new Vec(4, 30, 7), new Vec(1, 0, 0)), 256));
        assertNull(new Plane(1, 10).intersect(new Ray(new Vec(4, 30, 7), new Vec(0, 1, 0)), 256));
    }
    @Test void verticalRectanglePaintsAWallAndUndoRestoresItAtomically() {
        Blueprint b = new Blueprint();
        var edits = BuildGeometry.rectangle(new GridPos(2, 4, 6), new GridPos(5, 7, 6), new Plane(2, 6), STONE);
        assertEquals(16, edits.size());
        assertTrue(edits.keySet().stream().allMatch(p -> p.z() == 6));
        b.edit(edits); assertEquals(16, b.blocks().size());
        b.undo(); assertTrue(b.blocks().isEmpty());
        b.redo(); assertEquals(16, b.blocks().size());
        assertThrows(IllegalArgumentException.class, () -> BuildGeometry.rectangle(new GridPos(2, 4, 6), new GridPos(5, 7, 7), new Plane(2, 6), STONE));
    }
    @Test void adjacentFacesSupportEveryBuildDirection() {
        GridPos p = new GridPos(5, 5, 5);
        Set<GridPos> neighbors = new HashSet<>();
        for (Face face : Face.values()) {
            GridPos next = face.offset(p, 1); neighbors.add(next);
            assertEquals(BuildGeometry.value(next, face.axis), Plane.through(next, face).coordinate());
        }
        assertEquals(6, neighbors.size()); assertTrue(neighbors.contains(new GridPos(5, 6, 5)));
    }
    @Test void placementAndDeletionPlanesStayNearTheTouchedFace() {
        GridPos block = new GridPos(8, 8, 8);
        for (Face face : Face.values()) {
            Plane placing = Plane.through(face.offset(block, 1), face);
            Plane erasing = Plane.onFace(block, face);
            double touched = BuildGeometry.value(block, face.axis) + (face.sign() > 0 ? 1 : 0);
            assertEquals(.015, Math.abs(placing.coordinate() + placing.offset() - touched), 1e-8);
            assertEquals(.015, Math.abs(erasing.coordinate() + erasing.offset() - touched), 1e-8);
        }
    }
    @Test void negativeHeightPlanesAndRaycastsRemainIndependentOfPlacedBlocks() {
        var ray = new Ray(new Vec(4.5, 30, 7.5), new Vec(0, -1, 0));
        var plane = new Plane(1, -40);
        assertEquals(new GridPos(4, -40, 7), plane.intersect(ray, 256));
        Blueprint b = new Blueprint();
        b.put(new GridPos(4, 20, 7), STONE);
        b.put(new GridPos(4, -40, 7), STONE);
        assertEquals(new GridPos(4, -40, 7), plane.intersect(ray, 256));
        assertTrue(BuildGeometry.cells(ray, 256).contains(new GridPos(4, -40, 7)));
        var edits = BuildGeometry.rectangle(new GridPos(2, -50, 6), new GridPos(5, -47, 6), new Plane(2, 6), STONE);
        b.edit(edits); assertEquals(18, b.blocks().size());
        b.undo(); assertEquals(2, b.blocks().size());
    }
}
