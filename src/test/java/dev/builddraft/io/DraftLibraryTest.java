package dev.builddraft.io;

import dev.builddraft.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DraftLibraryTest {
    @TempDir Path directory;
    private final GridPos origin = new GridPos(100, -60, 200);
    private Blueprint draft(String block) {
        Blueprint result = new Blueprint();
        result.put(new GridPos(1, 2, 3), new BlockSpec("minecraft:" + block, Map.of()));
        return result;
    }
    @Test void multipleNamedDraftsSurviveReopeningWithIndependentOrigins() throws Exception {
        var library = new DraftLibrary(directory);
        library.create("小屋", draft("stone"), origin);
        var otherOrigin = new GridPos(-800, 64, 400);
        library.create("桥", draft("oak_planks"), otherOrigin);
        var reopened = new DraftLibrary(directory);
        assertEquals(2, reopened.list().size());
        assertEquals(origin, reopened.load("小屋").origin());
        assertEquals(otherOrigin, reopened.load("桥").origin());
        assertEquals(draft("stone").blocks(), reopened.load("小屋").blueprint().blocks());
    }
    @Test void duplicateCreateNeverOverwritesButExplicitUpdateDoes() throws Exception {
        var library = new DraftLibrary(directory);
        library.create("小屋", draft("stone"), origin);
        assertThrows(IOException.class, () -> library.create("小屋", draft("glass"), origin));
        assertEquals(draft("stone").blocks(), library.load("小屋").blueprint().blocks());
        library.update("小屋", draft("glass"), origin);
        assertEquals(draft("glass").blocks(), library.load("小屋").blueprint().blocks());
    }
    @Test void deletingOneEntryPreservesOtherDraftsAndRecoveryFile() throws Exception {
        Path drafts = directory.resolve("drafts");
        new BlueprintFiles(directory).save("session", draft("stone"), origin);
        var library = new DraftLibrary(drafts);
        library.create("A", draft("stone"), origin); library.create("B", draft("glass"), origin);
        library.delete("A");
        assertEquals(List.of("B"), library.list().stream().map(DraftLibrary.Entry::name).toList());
        assertEquals(draft("stone").blocks(), new BlueprintFiles(directory).load("session").blocks());
        assertThrows(IOException.class, () -> library.delete("A"));
        assertThrows(IllegalArgumentException.class, () -> library.delete("../session"));
    }
    @Test void corruptEntryDoesNotHideHealthyDraftsOrGetOverwrittenDuringBackup() throws Exception {
        var library = new DraftLibrary(directory);
        library.create("正常", draft("stone"), origin);
        Files.writeString(directory.resolve("损坏.json"), "not json");
        assertEquals(2, library.list().size());
        assertFalse(library.list().stream().filter(e -> e.name().equals("损坏")).findFirst().orElseThrow().error().isEmpty());
        library.preserve(draft("glass"), origin);
        assertEquals("not json", Files.readString(directory.resolve("损坏.json")));
        assertEquals(3, library.list().size());
    }
    @Test void switchBackupDeduplicatesOnlyWhenBothContentAndOriginMatch() throws Exception {
        var library = new DraftLibrary(directory);
        var blueprint = draft("stone");
        String first = library.preserve(blueprint, origin);
        assertEquals(first, library.preserve(blueprint, origin));
        String moved = library.preserve(blueprint, new GridPos(101, -60, 200));
        assertNotEquals(first, moved);
        String changed = library.preserve(draft("glass"), origin);
        assertNotEquals(first, changed); assertNotEquals(moved, changed);
        assertEquals(3, library.list().size());
    }
    @Test void intentionallySavedEmptyDraftLoadsAndAutomaticBackupSkipsEmptyDrafts() throws Exception {
        var library = new DraftLibrary(directory);
        assertEquals("", library.preserve(new Blueprint(), origin));
        assertTrue(library.list().isEmpty());
        library.create("空白", new Blueprint(), origin);
        assertTrue(library.load("空白").blueprint().blocks().isEmpty());
        assertEquals(origin, library.load("空白").origin());
    }
    @Test void legacyDraftWithoutOriginRemainsVisibleButCannotBeLoadedOrCreated() throws Exception {
        new BlueprintFiles(directory).save("旧稿", draft("stone"));
        var library = new DraftLibrary(directory);
        assertEquals(1, library.list().size()); assertNull(library.list().get(0).origin());
        assertThrows(IOException.class, () -> library.load("旧稿"));
        assertThrows(IOException.class, () -> library.create("无原点", draft("stone"), null));
        assertFalse(Files.exists(directory.resolve("无原点.json")));
    }
    @Test void backupFailureIsReportedInsteadOfPretendingTheDraftWasSaved() throws Exception {
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "occupied");
        var library = new DraftLibrary(blocked);
        assertThrows(IOException.class, () -> library.preserve(draft("stone"), origin));
        assertEquals("occupied", Files.readString(blocked));
    }
}
