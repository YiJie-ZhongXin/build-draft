package dev.builddraft.io;

import com.google.gson.*;
import dev.builddraft.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class BlueprintFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    private final Path directory;
    public BlueprintFiles(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Path directory() { return directory; }

    private Path path(String name) {
        if (name == null || !name.matches("[\\p{L}\\p{N}_ -]{1,48}") || name.isBlank()) {
            throw new IllegalArgumentException("名称只能含文字、数字、空格、下划线或连字符（1–48 字）");
        }
        if (name.endsWith(" ") || name.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])")) {
            throw new IllegalArgumentException("请换一个文件名称");
        }
        Path result = directory.resolve(name + ".json").normalize();
        if (!result.getParent().equals(directory)) throw new IllegalArgumentException("无效文件路径");
        return result;
    }

    public boolean exists(String name) { return Files.isRegularFile(path(name)); }
    public long modified(String name) throws IOException { return Files.getLastModifiedTime(path(name)).toMillis(); }
    public boolean delete(String name) throws IOException { return Files.deleteIfExists(path(name)); }

    public List<String> list() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        }
    }

    public void save(String name, Blueprint blueprint) throws IOException {
        save(name, blueprint, null);
    }

    /** Origin and blocks share one atomic file so recovery never re-anchors a draft. */
    public void save(String name, Blueprint blueprint, GridPos origin) throws IOException {
        Path target = path(name);
        JsonObject root = new JsonObject();
        root.addProperty("format", "builddraft");
        root.addProperty("version", 1);
        root.addProperty("minecraft", "1.20.1");
        if (origin != null) {
            validateOrigin(origin);
            JsonObject anchor = new JsonObject();
            anchor.addProperty("x", origin.x()); anchor.addProperty("y", origin.y()); anchor.addProperty("z", origin.z());
            root.add("origin", anchor);
        }
        JsonArray entries = new JsonArray();
        blueprint.blocks().forEach((p, b) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("x", p.x()); entry.addProperty("y", p.y()); entry.addProperty("z", p.z());
            entry.addProperty("block", b.id()); entry.add("properties", GSON.toJsonTree(b.properties()));
            entries.add(entry);
        });
        root.add("blocks", entries);
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "draft-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public Blueprint load(String name) throws IOException {
        return loadSnapshot(name).blueprint();
    }

    public record Snapshot(Blueprint blueprint, GridPos origin) { }
    public Snapshot loadSnapshot(String name) throws IOException {
        Path source = path(name);
        if (Files.size(source) > MAX_BYTES) throw new IOException("蓝图文件过大");
        try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!"builddraft".equals(root.get("format").getAsString()) || integer(root, "version") != 1) {
                throw new IllegalArgumentException("不支持的蓝图格式");
            }
            JsonArray entries = root.getAsJsonArray("blocks");
            if (entries.size() > Blueprint.MAX_BLOCKS) throw new IllegalArgumentException("蓝图方块过多");
            Map<GridPos, BlockSpec> blocks = new LinkedHashMap<>();
            for (JsonElement element : entries) {
                JsonObject e = element.getAsJsonObject();
                GridPos p = new GridPos(integer(e, "x"), integer(e, "y"), integer(e, "z"));
                Map<String, String> properties = new TreeMap<>();
                e.getAsJsonObject("properties").entrySet().forEach(prop -> properties.put(prop.getKey(), prop.getValue().getAsString()));
                if (blocks.put(p, new BlockSpec(e.get("block").getAsString(), properties)) != null) {
                    throw new IllegalArgumentException("蓝图包含重复坐标");
                }
            }
            Blueprint blueprint = new Blueprint();
            blueprint.replace(blocks);
            GridPos origin = null;
            if (root.has("origin")) {
                JsonObject anchor = root.getAsJsonObject("origin");
                origin = new GridPos(integer(anchor, "x"), integer(anchor, "y"), integer(anchor, "z"));
                validateOrigin(origin);
            }
            return new Snapshot(blueprint, origin);
        } catch (RuntimeException e) { throw new IOException("蓝图损坏：" + e.getMessage(), e); }
    }

    private static void validateOrigin(GridPos origin) {
        if (origin.x() < -GridWindow.WORLD_LIMIT || origin.x() >= GridWindow.WORLD_LIMIT
            || origin.z() < -GridWindow.WORLD_LIMIT || origin.z() >= GridWindow.WORLD_LIMIT
            || origin.y() < Blueprint.MIN_Y || origin.y() >= Blueprint.MAX_Y) throw new IllegalArgumentException("无效草稿原点");
    }

    private static int integer(JsonObject object, String key) {
        return object.get(key).getAsBigDecimal().intValueExact();
    }
}
