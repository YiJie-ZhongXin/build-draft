package dev.builddraft;

import dev.builddraft.io.BlueprintFiles;
import dev.builddraft.screen.DraftEditorScreen;
import dev.builddraft.screen.DraftScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
    public static final BlueprintFiles LIBRARY = new BlueprintFiles(ROOT.resolve("blueprints"));
    private static DraftSession session;
    private static ClientWorld sessionWorld;
    private static BlueprintFiles autosaves;
    private static long savedRevision = -1;
    private static int ticks;

    @Override public void onInitializeClient() {
        DraftKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(BuildDraftClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(BuildDraftClient::reset));
        WorldRenderEvents.AFTER_ENTITIES.register(DraftRenderer::renderModels);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(DraftRenderer::captureGuides);
        HudRenderCallback.EVENT.register((draw, delta) -> {
            var client = MinecraftClient.getInstance();
            if (session == null || !session.projection || client.currentScreen != null || client.options.hudHidden) return;
            String status = "筑稿 · " + (session.solidPreview ? "实景" : "虚影") + " · " + session.blueprint.blocks().size() + " 格  |  "
                + DraftKeys.label(DraftKeys.EDITOR) + " 编辑 / " + DraftKeys.label(DraftKeys.PROJECTION) + " 显隐 / " + DraftKeys.label(DraftKeys.PREVIEW) + " 实虚";
            draw.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(status, draw.getScaledWindowWidth() - 16), 8, 8, 0xff9de4ed);
        });
    }

    private static void tick(MinecraftClient client) {
        if (session != null && client.world != sessionWorld) reset();
        if (client.world == null || client.player == null) return;
        if (client.currentScreen == null) {
            if (DraftKeys.EDITOR.wasPressed()) {
                ensureSession(client);
                client.setScreen(new DraftEditorScreen(session));
                DraftKeys.clearPending();
                return;
            }
            while (DraftKeys.PROJECTION.wasPressed()) {
                if (session != null) session.projection = !session.projection;
            }
            while (DraftKeys.PREVIEW.wasPressed()) {
                if (session != null) session.solidPreview = !session.solidPreview;
            }
            while (DraftKeys.ANCHOR.wasPressed()) {
                if (session != null && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos target = hit.getBlockPos().offset(hit.getSide());
                    if (target.getY() >= client.world.getBottomY() && target.getY() < client.world.getTopY()) {
                        session.moveOrigin(target);
                        message("蓝图原点已移至 " + target.toShortString());
                    }
                }
            }
        } else DraftKeys.clearPending();
        if (++ticks % 200 == 0) autosave();
    }

    public static DraftSession session() { return session; }
    public static boolean editing() { return MinecraftClient.getInstance().currentScreen instanceof DraftEditorScreen; }
    private static void ensureSession(MinecraftClient client) {
        if (session != null) return;
        BlockPos center = client.player.getBlockPos();
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) center = hit.getBlockPos().up();
        center = new BlockPos(center.getX(), Math.max(client.world.getBottomY(), Math.min(client.world.getTopY() - 1, center.getY())), center.getZ());
        session = new DraftSession(center.add(-16, 0, -16));
        sessionWorld = client.world;
        session.importPlayerHotbar();
        String server = client.getServer() != null ? "local:" + client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath()
            : "server:" + (client.getCurrentServerEntry() == null ? "unknown" : client.getCurrentServerEntry().address);
        String identity = server + "/" + client.world.getRegistryKey().getValue();
        autosaves = new BlueprintFiles(ROOT.resolve("autosaves").resolve(digest(identity)));
        // Recovery is explicit through the library button; never silently re-anchor old work.
        savedRevision = -1;
    }

    public static BlueprintFiles autosaves() { return autosaves; }
    public static void markUnsaved() { savedRevision = -1; }
    public static void autosave() {
        if (session == null || autosaves == null || savedRevision == session.blueprint.revision()) return;
        try {
            // Empty new sessions must not overwrite the previous recovery copy.
            if (!session.blueprint.blocks().isEmpty() || savedRevision >= 0) autosaves.save("recovery", session.blueprint);
            savedRevision = session.blueprint.revision();
        } catch (Exception e) { LOGGER.error("Could not autosave blueprint", e); message("草稿自动保存失败：" + e.getMessage()); }
    }

    public static void reset() {
        autosave();
        if (MinecraftClient.getInstance().currentScreen instanceof DraftScreen) MinecraftClient.getInstance().setScreen(null);
        session = null; sessionWorld = null; autosaves = null; savedRevision = -1;
        DraftRenderer.clear();
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
