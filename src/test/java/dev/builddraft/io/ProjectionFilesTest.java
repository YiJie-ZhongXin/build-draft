package dev.builddraft.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.builddraft.io.ProjectionFiles.Mode;

class ProjectionFilesTest {
    @TempDir Path directory;
    @Test void defaultIsOffAndAllThreeModesCycle() throws Exception {
        assertEquals(new ProjectionFiles.Selection("", Mode.OFF), new ProjectionFiles(directory).load());
        assertEquals(Mode.GHOST, Mode.OFF.next()); assertEquals(Mode.SOLID, Mode.GHOST.next()); assertEquals(Mode.OFF, Mode.SOLID.next());
    }
    @Test void savedSelectionSurvivesRestartAndCanBeDisabled() throws Exception {
        new ProjectionFiles(directory).save("小屋", Mode.GHOST);
        assertEquals(new ProjectionFiles.Selection("小屋", Mode.GHOST), new ProjectionFiles(directory).load());
        new ProjectionFiles(directory).save("小屋", Mode.OFF);
        assertEquals(Mode.OFF, new ProjectionFiles(directory).load().mode());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void corruptAndOversizedSettingsArePreservedAndReported() throws Exception {
        Path file = directory.resolve("projection.json"); Files.writeString(file, "bad");
        assertThrows(IOException.class, () -> new ProjectionFiles(directory).load()); assertEquals("bad", Files.readString(file));
        Files.writeString(file, "x".repeat(8193)); assertThrows(IOException.class, () -> new ProjectionFiles(directory).load());
    }
}
