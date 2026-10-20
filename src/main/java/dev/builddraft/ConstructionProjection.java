package dev.builddraft;

import dev.builddraft.io.DraftLibrary;
import dev.builddraft.io.ProjectionFiles;
import dev.builddraft.io.ProjectionFiles.Mode;
import net.minecraft.util.math.BlockPos;
import java.io.IOException;
import java.nio.file.Path;

/** An immutable saved snapshot, separate from the draft currently being designed. */
public final class ConstructionProjection {
    private static DraftSession projection;
    private static String name = "";
    private static Mode mode = Mode.OFF;
    private static DraftLibrary library;
    private static ProjectionFiles settings;
    public static void configure(Path worldDirectory, DraftLibrary drafts) {
        reset(); library = drafts; settings = new ProjectionFiles(worldDirectory);
        try { var saved = settings.load(); if (saved.mode() != Mode.OFF) install(saved.name(), saved.mode()); }
        catch (Exception e) { BuildDraftClient.LOGGER.warn("Could not restore construction projection", e); BuildDraftClient.message("施工投影恢复失败，草稿保留"); }
    }
    public static DraftSession snapshot(String selected) throws IOException {
        var saved = library.load(selected);
        var origin = saved.origin();
        DraftSession result = new DraftSession(new BlockPos(origin.x(), origin.y(), origin.z()));
        result.load(saved.blueprint()); return result;
    }
    private static void install(String selected, Mode selectedMode) throws IOException {
        DraftSession loaded = snapshot(selected);
        loaded.solidPreview = selectedMode == Mode.SOLID;
        projection = loaded; name = selected; mode = selectedMode;
        ConstructionAssist.reset();
    }
    public static void set(String selected, Mode selectedMode) throws IOException {
        DraftSession loaded = selectedMode == Mode.OFF ? null : snapshot(selected);
        settings.save(selected, selectedMode);
        if (loaded != null) loaded.solidPreview = selectedMode == Mode.SOLID;
        projection = loaded; name = selected; mode = selectedMode;
        ConstructionAssist.reset();
    }
    public static void stop() throws IOException { set(name, Mode.OFF); }
    public static void updated(String selected) throws IOException { if (active() && name.equals(selected)) set(selected, mode); }
    public static void deleting(String selected) throws IOException { if (active() && name.equals(selected)) stop(); }
    public static boolean active() { return projection != null && mode != Mode.OFF; }
    public static boolean selected(String selected) { return active() && name.equals(selected); }
    public static Mode mode(String selected) { return selected(selected) ? mode : Mode.OFF; }
    public static String name() { return name; }
    public static DraftSession draft() { return active() ? projection : null; }
    public static void reset() { projection = null; name = ""; mode = Mode.OFF; library = null; settings = null; ConstructionAssist.reset(); }
}
