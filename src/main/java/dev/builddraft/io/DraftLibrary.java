package dev.builddraft.io;

import dev.builddraft.model.Blueprint;
import dev.builddraft.model.GridPos;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Named snapshots belong to one world and dimension; switching never discards an unsaved draft. */
public final class DraftLibrary {
    private final BlueprintFiles files;
    public record Entry(String name, int blocks, GridPos origin, long modified, String error) { }
    public DraftLibrary(Path directory) { files = new BlueprintFiles(directory); }

    public List<Entry> list() throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String name : files.list()) {
            long modified = 0;
            try {
                modified = files.modified(name);
                var snapshot = files.loadSnapshot(name);
                entries.add(new Entry(name, snapshot.blueprint().blocks().size(), snapshot.origin(), modified, ""));
            } catch (IOException | IllegalArgumentException e) {
                entries.add(new Entry(name, 0, null, modified, e.getMessage()));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::modified).reversed().thenComparing(Entry::name));
        return List.copyOf(entries);
    }
    public void create(String name, Blueprint blueprint, GridPos origin) throws IOException {
        if (files.exists(name)) throw new IOException("同名草稿已存在，请换名称或选择更新");
        update(name, blueprint, origin);
    }
    public void update(String name, Blueprint blueprint, GridPos origin) throws IOException {
        if (origin == null) throw new IOException("草稿缺少世界原点");
        files.save(name, blueprint, origin);
    }
    public BlueprintFiles.Snapshot load(String name) throws IOException {
        var snapshot = files.loadSnapshot(name);
        if (snapshot.origin() == null) throw new IOException("草稿缺少原点，不能确定放置位置");
        return snapshot;
    }
    public void delete(String name) throws IOException {
        if (!files.delete(name)) throw new IOException("草稿已不存在，请刷新列表");
    }
    /** No duplicate backup when the same blocks and origin are already saved. */
    public String preserve(Blueprint blueprint, GridPos origin) throws IOException {
        if (blueprint.blocks().isEmpty()) return "";
        for (String name : files.list()) {
            try {
                var snapshot = files.loadSnapshot(name);
                if (Objects.equals(origin, snapshot.origin()) && blueprint.blocks().equals(snapshot.blueprint().blocks())) return name;
            } catch (IOException | IllegalArgumentException ignored) { /* Keep corrupt files; save a separate backup. */ }
        }
        String base = "切换前 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String name = base;
        for (int suffix = 2; files.exists(name); suffix++) name = base + " " + suffix;
        create(name, blueprint, origin);
        return name;
    }
}
