package dev.builddraft;

import dev.builddraft.io.BlueprintFiles;
import dev.builddraft.io.DraftLibrary;
import dev.builddraft.screen.DraftEditorScreen;
import dev.builddraft.screen.DraftScreen;
import dev.builddraft.screen.DraftLibraryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class BuildDraftClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("builddraft");
    public static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("build-draft");
    private static DraftSession session;
    private static ClientWorld sessionWorld;
    private static BlueprintFiles autosaves;
    private static DraftLibrary library;
    private static boolean forceAutosave;
    private static long savedRevision = -1;
    private static int ticks;

    @Override public void onInitializeClient() {
        DraftKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(BuildDraftClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(BuildDraftClient::reset));
        WorldRenderEvents.AFTER_ENTITIES.register(DraftRenderer::renderModels);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(DraftRenderer::captureGuides);
    }

    private static void tick(MinecraftClient client) {
        if (session != null && client.world != sessionWorld) reset();
        if (client.world == null || client.player == null) return;
        ensureSession(client);
        if (client.currentScreen == null) {
            if (DraftKeys.EDITOR.wasPressed()) {
                ensureSession(client);
                client.setScreen(new DraftEditorScreen(session));
                DraftKeys.clearPending();
                return;
            }
            if (DraftKeys.LIBRARY.wasPressed()) {
                client.setScreen(new DraftLibraryScreen(null, session, library));
                DraftKeys.clearPending(); return;
            }
        } else DraftKeys.clearPending();
        ConstructionAssist.tick();
        if (++ticks % 200 == 0) autosave();
    }

    public static DraftSession session() { return session; }
    public static void openLibrary() {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        ensureSession(client); client.setScreen(new DraftLibraryScreen(null, session, library)); DraftKeys.clearPending();
    }
    public static DraftLibrary library() { return library; }
    public static void draftChanged() { forceAutosave = true; autosave(); }
    public static boolean editing() { return MinecraftClient.getInstance().currentScreen instanceof DraftEditorScreen; }
    public static DraftSession renderedDraft() {
        return editing() ? session : MinecraftClient.getInstance().currentScreen == null ? ConstructionProjection.draft() : null;
    }
    private static void ensureSession(MinecraftClient client) {
        if (session != null) return;
        BlockPos center = client.player.getBlockPos();
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) center = hit.getBlockPos().up();
        center = new BlockPos(center.getX(), Math.max(client.world.getBottomY(), Math.min(client.world.getTopY() - 1, center.getY())), center.getZ());
        var grid = dev.builddraft.model.GridWindow.atWorld(center.getX() - 16, center.getZ() - 16);
        session = new DraftSession(new BlockPos(grid.x(), center.getY(), grid.z()));
        sessionWorld = client.world;
        session.importPlayerHotbar();
        String server = client.getServer() != null ? "local:" + client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath()
            : "server:" + (client.getCurrentServerEntry() == null ? "unknown" : client.getCurrentServerEntry().address);
        String identity = server + "/" + client.world.getRegistryKey().getValue();
        autosaves = new BlueprintFiles(ROOT.resolve("autosaves").resolve(digest(identity)));
        library = new DraftLibrary(autosaves.directory().resolve("drafts"));
        forceAutosave = false;
        savedRevision = -1;
        try {
            if (autosaves.exists("session")) {
                var recovered = autosaves.loadSnapshot("session");
                if (recovered.origin() == null) throw new java.io.IOException("自动保存缺少原点");
                session.load(recovered.blueprint());
                var origin = recovered.origin();
                session.origin = new BlockPos(origin.x(), origin.y(), origin.z());
                savedRevision = session.blueprint.revision();
            }
        } catch (Exception e) {
            LOGGER.error("Could not recover draft session", e);
            message("草稿恢复失败，原文件保留：" + e.getMessage());
            // Preserve unreadable recovery data; only explicit repair can resume writing this session.
            autosaves = null;
        }
        ConstructionProjection.configure(ROOT.resolve("autosaves").resolve(digest(identity)), library);
    }

    public static void autosave() {
        if (session == null || autosaves == null || !forceAutosave && savedRevision == session.blueprint.revision()) return;
        try {
            // Empty new sessions must not overwrite the previous recovery copy.
            if (forceAutosave || !session.blueprint.blocks().isEmpty() || savedRevision >= 0) autosaves.save("session", session.blueprint,
                new dev.builddraft.model.GridPos(session.origin.getX(), session.origin.getY(), session.origin.getZ()));
            savedRevision = session.blueprint.revision();
            forceAutosave = false;
        } catch (Exception e) { LOGGER.error("Could not autosave blueprint", e); message("草稿自动保存失败：" + e.getMessage()); }
    }

    public static void reset() {
        autosave();
        if (MinecraftClient.getInstance().currentScreen instanceof DraftScreen) MinecraftClient.getInstance().setScreen(null);
        session = null; sessionWorld = null; autosaves = null; library = null; forceAutosave = false; savedRevision = -1;
        DraftRenderer.clear();
        ConstructionProjection.reset();
    }

    public static void message(String text) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.sendMessage(Text.literal("[筑稿] " + text), false);
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
