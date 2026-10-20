package dev.builddraft.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintTest {
    private static final BlockSpec STONE = new BlockSpec("minecraft:stone", Map.of());
    private static final BlockSpec LOG = new BlockSpec("minecraft:oak_log", Map.of("axis", "x"));
    private static final GridPos POS = new GridPos(0, 0, 0);

    @Test void replacementEraseUndoAndRedoPreserveState() {
        Blueprint b = new Blueprint();
        b.put(POS, STONE); b.put(POS, LOG); b.put(POS, null);
        assertTrue(b.blocks().isEmpty());
        assertTrue(b.undo()); assertEquals(LOG, b.blocks().get(POS));
        assertTrue(b.undo()); assertEquals(STONE, b.blocks().get(POS));
        assertTrue(b.redo()); assertEquals(LOG, b.blocks().get(POS));
        assertTrue(b.redo()); assertTrue(b.blocks().isEmpty());
    }
    @Test void batchEditIsOneAtomicHistoryEntry() {
        Blueprint b = new Blueprint();
        b.put(POS, LOG);
        Map<GridPos, BlockSpec> area = new HashMap<>();
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) area.put(new GridPos(x, 0, z), STONE);
        b.edit(area); assertEquals(100, b.blocks().size());
        b.undo(); assertEquals(Map.of(POS, LOG), b.blocks());
        b.redo(); assertEquals(100, b.blocks().size());
    }
    @Test void invalidBatchNeverPartiallyChangesBlueprint() {
        Blueprint b = new Blueprint();
        b.put(POS, LOG);
        var batch = new LinkedHashMap<GridPos, BlockSpec>();
        batch.put(POS, STONE); batch.put(new GridPos(Blueprint.MAX_XZ, 0, 0), STONE);
        assertThrows(IllegalArgumentException.class, () -> b.edit(batch));
        assertEquals(Map.of(POS, LOG), b.blocks());
    }
    @Test void capacityFailurePreservesHistoryAndContent() {
        Blueprint b = new Blueprint();
        Map<GridPos, BlockSpec> full = new HashMap<>();
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) full.put(new GridPos(x, y, z), STONE);
        b.edit(full);
        assertThrows(IllegalArgumentException.class, () -> b.put(new GridPos(0, 32, 0), STONE));
        assertEquals(Blueprint.MAX_BLOCKS, b.blocks().size());
        b.undo(); assertTrue(b.blocks().isEmpty());
    }
    @Test void editAfterUndoInvalidatesRedo() {
        Blueprint b = new Blueprint();
        b.put(POS, STONE); b.undo(); b.put(POS, LOG);
        assertFalse(b.redo()); assertEquals(LOG, b.blocks().get(POS));
    }
    @Test void noOpDoesNotConsumeUndoOrRevision() {
        Blueprint b = new Blueprint(); b.put(POS, STONE);
        long revision = b.revision(); b.put(POS, STONE);
        assertEquals(revision, b.revision()); b.undo(); assertTrue(b.blocks().isEmpty());
    }
    @Test void validatesEveryBoundaryAndDefendsCollections() {
        Blueprint b = new Blueprint();
        assertTrue(b.contains(new GridPos(31, 63, 31)));
        assertTrue(b.contains(new GridPos(0, -384, 0)));
        assertTrue(b.contains(new GridPos(0, 384, 0)));
        for (GridPos p : List.of(new GridPos(Blueprint.MIN_XZ - 1, 0, 0), new GridPos(0, Blueprint.MIN_Y - 1, 0), new GridPos(0, Blueprint.MAX_Y, 0), new GridPos(0, 0, Blueprint.MAX_XZ))) {
            assertThrows(IllegalArgumentException.class, () -> b.put(p, STONE));
        }
        assertThrows(UnsupportedOperationException.class, () -> b.blocks().put(POS, STONE));
        assertThrows(UnsupportedOperationException.class, () -> LOG.properties().put("axis", "y"));
    }
    @Test void invalidReplacePreservesExistingDraft() {
        Blueprint b = new Blueprint(); b.put(POS, STONE);
        assertThrows(IllegalArgumentException.class, () -> b.replace(Map.of(new GridPos(Blueprint.MAX_XZ, 0, 0), LOG)));
        assertEquals(Map.of(POS, STONE), b.blocks());
    }
    @Test void clearCanBeUndoneAsOneStepWithoutLosingTheEarlierHistory() {
        Blueprint b = new Blueprint(); b.put(POS, STONE); b.put(new GridPos(1, 0, 0), LOG);
        var before = Map.copyOf(b.blocks());
        b.clear(); assertTrue(b.blocks().isEmpty()); assertTrue(b.canUndo());
        assertTrue(b.undo()); assertEquals(before, b.blocks()); assertTrue(b.canRedo());
        assertTrue(b.undo()); assertEquals(Map.of(POS, STONE), b.blocks());
    }
    @Test void clearingAnEmptyDraftDoesNotConsumeHistoryAndClearCanBeRedone() {
        Blueprint b = new Blueprint(); long revision = b.revision(); b.clear();
        assertEquals(revision, b.revision()); assertFalse(b.canUndo());
        b.put(POS, STONE); b.clear(); b.undo(); b.redo(); assertTrue(b.blocks().isEmpty());
    }
}
