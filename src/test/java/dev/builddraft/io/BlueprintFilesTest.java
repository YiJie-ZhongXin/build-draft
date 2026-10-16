package dev.builddraft.io;

import dev.builddraft.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintFilesTest {
    @TempDir Path directory;
    @Test void roundTripRetainsAllCoordinatesAndProperties() throws Exception {
        Blueprint original = new Blueprint();
        original.put(new GridPos(31, 63, 0), new BlockSpec("minecraft:oak_stairs", Map.of("facing", "east", "half", "top", "shape", "straight", "waterlogged", "false")));
        original.put(new GridPos(-200, -180, 300), new BlockSpec("minecraft:stone", Map.of()));
        original.put(new GridPos(2, 300, 3), new BlockSpec("minecraft:glass", Map.of()));
        BlueprintFiles files = new BlueprintFiles(directory);
        files.save("中式小屋", original);
        assertEquals(original.blocks(), files.load("中式小屋").blocks());
        assertEquals(List.of("中式小屋"), files.list());
    }
    @Test void rejectsTraversalAndWindowsReservedNames() {
        BlueprintFiles files = new BlueprintFiles(directory);
        for (String name : List.of("../escape", "a/b", "a\\b", "", "CON", "NUL", "LPT1", "name ")) {
            assertThrows(IllegalArgumentException.class, () -> files.save(name, new Blueprint()));
        }
    }
    @Test void rejectsWrongVersionAndCorruptJson() throws Exception {
        Files.writeString(directory.resolve("bad.json"), "{\"format\":\"builddraft\",\"version\":2,\"blocks\":[]}");
        assertThrows(IOException.class, () -> new BlueprintFiles(directory).load("bad"));
        Files.writeString(directory.resolve("bad.json"), "not json");
        assertThrows(IOException.class, () -> new BlueprintFiles(directory).load("bad"));
    }
    @Test void rejectsDuplicateAndFractionalPositions() throws Exception {
        String entry = "{\"x\":0,\"y\":0,\"z\":0,\"block\":\"minecraft:stone\",\"properties\":{}}";
        writeBlocks(entry + "," + entry);
        assertThrows(IOException.class, () -> new BlueprintFiles(directory).load("bad"));
        writeBlocks(entry.replace("\"x\":0", "\"x\":0.5"));
        assertThrows(IOException.class, () -> new BlueprintFiles(directory).load("bad"));
    }
    @Test void rejectsOutOfBoundsCoordinates() throws Exception {
        writeBlocks("{\"x\":" + Blueprint.MAX_XZ + ",\"y\":0,\"z\":0,\"block\":\"minecraft:stone\",\"properties\":{}}");
        assertThrows(IOException.class, () -> new BlueprintFiles(directory).load("bad"));
    }
    @Test void atomicOverwriteLeavesNoTemporaryFiles() throws Exception {
        BlueprintFiles files = new BlueprintFiles(directory);
        Blueprint b = new Blueprint(); files.save("house", b);
        b.put(new GridPos(1, 2, 3), new BlockSpec("minecraft:glass", Map.of())); files.save("house", b);
        assertEquals(b.blocks(), files.load("house").blocks());
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }
    private void writeBlocks(String entries) throws IOException {
        Files.writeString(directory.resolve("bad.json"), "{\"format\":\"builddraft\",\"version\":1,\"blocks\":[" + entries + "]}");
    }
}
