package dev.builddraft.io;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** One saved projection selection per world and dimension. */
public final class ProjectionFiles {
    public enum Mode { OFF("关闭"), GHOST("虚影"), SOLID("实景");
        public final String label; Mode(String label) { this.label = label; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public record Selection(String name, Mode mode) { }
    private final Path file;
    public ProjectionFiles(Path directory) { file = directory.resolve("projection.json"); }
    public Selection load() throws IOException {
        if (!Files.exists(file)) return new Selection("", Mode.OFF);
        if (Files.size(file) > 8192) throw new IOException("投影设置文件过大");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            return new Selection(json.get("name").getAsString(), Mode.valueOf(json.get("mode").getAsString()));
        } catch (RuntimeException e) { throw new IOException("投影设置损坏", e); }
    }
    public void save(String name, Mode mode) throws IOException {
        JsonObject json = new JsonObject(); json.addProperty("name", name); json.addProperty("mode", mode.name());
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "projection-", ".tmp");
        try {
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
