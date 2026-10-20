package dev.builddraft;

import dev.builddraft.model.Blueprint;
import dev.builddraft.render.DraftWorldView;
import dev.builddraft.render.PreviewModels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;

/** Models join the world entity consumers before shader composition; editor guides draw later. */
public final class DraftRenderer {
    private static final VertexConsumerProvider.Immediate BUFFERS = VertexConsumerProvider.immediate(new BufferBuilder(256 * 1024));
    private static DraftWorldView view;
    private static DraftSession lastSession;
    private static BlockPos lastOrigin;
    private static long lastRevision = -1;
    private static Blueprint lastBlueprint;
    private static final java.util.Set<net.minecraft.block.Block> failed = new java.util.HashSet<>();
    private static WorldRenderContext guideContext;
    private static final org.joml.Matrix4f guideView = new org.joml.Matrix4f();
    private static final org.joml.Matrix3f guideNormal = new org.joml.Matrix3f();
    private static final org.joml.Matrix4f guideProjection = new org.joml.Matrix4f();
    private static final org.joml.Matrix4f guideModelView = new org.joml.Matrix4f();

    public static void captureGuides(WorldRenderContext context) {
        guideContext = context;
        guideView.set(context.matrixStack().peek().getPositionMatrix());
        guideNormal.set(context.matrixStack().peek().getNormalMatrix());
        guideProjection.set(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        guideModelView.set(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
    }

    /** Run after WorldRenderer returns: Iris has completed its final pass and stopped overriding shaders. */
    public static void renderGuides() {
        var context = guideContext;
        guideContext = null;
        if (context == null || BuildDraftClient.renderedDraft() == null) return;
        var projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        var sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        var modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.push();
        try {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
            modelView.peek().getPositionMatrix().set(guideModelView);
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(guideProjection, com.mojang.blaze3d.systems.VertexSorter.BY_DISTANCE);
            var matrices = new net.minecraft.client.util.math.MatrixStack();
            matrices.peek().getPositionMatrix().set(guideView);
            matrices.peek().getNormalMatrix().set(guideNormal);
            render(context, matrices);
        } finally {
            modelView.pop();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    private static void updateView(DraftSession draft) {
        if (draft != lastSession || draft.blueprint != lastBlueprint || draft.blueprint.revision() != lastRevision || !draft.origin.equals(lastOrigin)) {
            view = draft.worldView();
            lastSession = draft; lastRevision = draft.blueprint.revision(); lastOrigin = draft.origin;
            lastBlueprint = draft.blueprint;
        }
    }

    public static void renderModels(WorldRenderContext context) {
        var draft = BuildDraftClient.renderedDraft();
        var client = MinecraftClient.getInstance();
        boolean editing = BuildDraftClient.editing();
        if (draft == null || client.world == null || context.consumers() == null) return;
        updateView(draft);
        var matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            var sorted = new java.util.ArrayList<>(draft.plannedBlocks());
            if (!draft.solidPreview || editing && draft.bypassDraft) sorted.sort(java.util.Comparator.comparingDouble((DraftSession.PlannedBlock p) -> draft.worldPos(p.relative()).getSquaredDistance(camera)).reversed());
            for (var entry : sorted) {
                var pos = draft.worldPos(entry.relative());
                if (!visible(context, pos, editing ? 256 : 128) || failed.contains(entry.state().getBlock())) continue;
                var actual = client.world.getBlockState(pos);
                if (editing ? actual.equals(entry.state()) : ConstructionState.complete(entry.state(), actual)) continue;
                if (!editing && !ConstructionState.canFill(entry.state(), actual)) continue;
                try {
                    float alpha = editing && draft.bypassDraft ? .12f : draft.solidPreview ? 1f : draft.ghostAlpha;
                    PreviewModels.render(draft, view, entry.state(), pos, matrices, context.consumers(), alpha);
                } catch (RuntimeException e) {
                    failed.add(entry.state().getBlock());
                    BuildDraftClient.LOGGER.warn("Preview renderer failed for {}; using an outline", entry.state(), e);
                }
            }
            // The owner (vanilla or Iris) flushes these consumers in its own solid/translucent phases.
        } finally { matrices.pop(); }
    }

    private static boolean visible(WorldRenderContext context, BlockPos pos, int distance) {
        var world = MinecraftClient.getInstance().world;
        return pos.getY() >= world.getBottomY() && pos.getY() < world.getTopY()
            && pos.getSquaredDistance(context.camera().getPos()) <= distance * distance && world.isChunkLoaded(pos)
            && (context.frustum() == null || context.frustum().isVisible(new Box(pos).expand(.002)));
    }

    private static void render(WorldRenderContext context, net.minecraft.client.util.math.MatrixStack matrices) {
        DraftSession draft = BuildDraftClient.renderedDraft();
        var client = MinecraftClient.getInstance();
        boolean editing = BuildDraftClient.editing();
        if (draft == null || client.world == null) return;
        updateView(draft);
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            VertexConsumer lines = BUFFERS.getBuffer(RenderLayer.getLines());
            for (var entry : draft.plannedBlocks()) {
                BlockPos pos = draft.worldPos(entry.relative());
                if (pos.getY() < client.world.getBottomY() || pos.getY() >= client.world.getTopY()) continue;
                int renderDistance = editing ? 256 : 128;
                if (pos.getSquaredDistance(camera) > renderDistance * renderDistance || !client.world.isChunkLoaded(pos)) continue;
                Box box = new Box(pos).expand(.002);
                if (context.frustum() != null && !context.frustum().isVisible(box)) continue;
                var actual = client.world.getBlockState(pos);
                boolean complete = editing ? actual.equals(entry.state()) : ConstructionState.complete(entry.state(), actual);
                if (!editing && complete) continue;
                boolean conflict = !complete && (editing ? !actual.isReplaceable() : !ConstructionState.canFill(entry.state(), actual));
                float r = complete ? .3f : conflict ? 1f : .3f;
                float g = complete ? .95f : conflict ? .48f : .85f;
                float b = complete ? .48f : conflict ? .22f : 1f;
                float alpha = editing && draft.bypassDraft ? .15f : .85f;
                boolean fallback = failed.contains(entry.state().getBlock());
                if (draft.solidPreview && !conflict && !complete && !fallback && entry.state().getRenderType() != net.minecraft.block.BlockRenderType.INVISIBLE) continue;
                // Outline the intended shape, including slabs and stairs, where available.
                var shape = entry.state().getOutlineShape(view, pos);
                var boxes = shape.isEmpty() ? java.util.List.of(new Box(0, 0, 0, 1, 1, 1)) : shape.getBoundingBoxes();
                for (Box shapeBox : boxes) {
                    Box bounds = shapeBox.offset(pos).expand(.002);
                    WorldRenderer.drawBox(matrices, lines, bounds, r, g, b, alpha);
                }
            }
            BUFFERS.draw();
        } finally { matrices.pop(); }
    }
    public static void clear() { guideContext = null; view = null; lastSession = null; lastBlueprint = null; lastOrigin = null; lastRevision = -1; failed.clear(); }
}
