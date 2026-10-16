package dev.builddraft;

import dev.builddraft.model.Blueprint;
import dev.builddraft.model.GridPos;
import dev.builddraft.model.BlockSpec;
import dev.builddraft.model.BuildGeometry.*;
import dev.builddraft.render.DraftWorldView;
import dev.builddraft.render.PreviewModels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;

/** Models join the world entity consumers before shader composition; editor guides draw later. */
public final class DraftRenderer {
    private static final VertexConsumerProvider.Immediate GRID = VertexConsumerProvider.immediate(new BufferBuilder(64 * 1024));
    private static final RenderLayer WORKPLANE = new RenderLayer("builddraft_workplane", VertexFormats.LINES, VertexFormat.DrawMode.LINES, 64 * 1024, false, false,
        () -> { RenderLayer.getLines().startDrawing(); com.mojang.blaze3d.systems.RenderSystem.disableDepthTest(); com.mojang.blaze3d.systems.RenderSystem.depthMask(false); },
        () -> { com.mojang.blaze3d.systems.RenderSystem.depthMask(true); com.mojang.blaze3d.systems.RenderSystem.enableDepthTest(); RenderLayer.getLines().endDrawing(); }) { };
    private static final VertexConsumerProvider.Immediate BUFFERS = VertexConsumerProvider.immediate(new BufferBuilder(256 * 1024));
    private static final VertexConsumerProvider.Immediate FACES = VertexConsumerProvider.immediate(new BufferBuilder(64 * 1024));
    private static DraftWorldView view;
    private static DraftSession lastSession;
    private static BlockPos lastOrigin;
    private static long lastRevision = -1;
    private static Blueprint lastBlueprint;
    private static java.util.Map<GridPos, BlockSpec> lastPreview;
    private static DraftWorldView previewView;
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
        if (context == null || BuildDraftClient.session() == null) return;
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
        var draft = BuildDraftClient.session();
        var client = MinecraftClient.getInstance();
        boolean editing = BuildDraftClient.editing();
        if (draft == null || client.world == null || (!draft.projection && !editing) || context.consumers() == null) return;
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
                if (!visible(context, pos, editing ? 256 : 128) || client.world.getBlockState(pos).equals(entry.state()) || failed.contains(entry.state().getBlock())) continue;
                try {
                    float alpha = editing && draft.bypassDraft ? .12f : draft.solidPreview ? 1f : draft.ghostAlpha;
                    PreviewModels.render(draft, view, entry.state(), pos, matrices, context.consumers(), alpha);
                } catch (RuntimeException e) {
                    failed.add(entry.state().getBlock());
                    BuildDraftClient.LOGGER.warn("Preview renderer failed for {}; using an outline", entry.state(), e);
                }
            }
            if (editing && draft.placementPreview != null && draft.placementValid && !draft.extrusionTool && !draft.erasingPreview) {
                var position = draft.worldPos(draft.placementPreview);
                try { PreviewModels.render(draft, view, draft.placementState == null ? draft.brush : draft.placementState, position, matrices, context.consumers(), .32f); }
                catch (RuntimeException e) { /* The placement outline remains available for special models. */ }
            }
            if (editing && !draft.extrusionSelection.isEmpty()) {
                if (lastPreview != draft.extrusionPreview) {
                    lastPreview = draft.extrusionPreview;
                    previewView = new DraftWorldView(draft, client.world, lastPreview);
                }
                for (var addition : draft.extrusionPreview.entrySet()) {
                    var pos = draft.worldPos(addition.getKey());
                    if (!visible(context, pos, 256)) continue;
                    try { PreviewModels.render(draft, previewView, BlockStates.resolve(addition.getValue()), pos, matrices, context.consumers(), .55f); }
                    catch (RuntimeException e) { /* Keep the bounded outline for unsupported models. */ }
                }
            } else { lastPreview = null; previewView = null; }
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
        DraftSession draft = BuildDraftClient.session();
        var client = MinecraftClient.getInstance();
        boolean editing = BuildDraftClient.editing();
        if (draft == null || client.world == null || (!draft.projection && !editing)) return;
        updateView(draft);
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            VertexConsumer lines = BUFFERS.getBuffer(RenderLayer.getLines());
            VertexConsumer faces = FACES.getBuffer(RenderLayer.getDebugFilledBox());
            if (editing && draft.showGrid) {
                drawGrid(draft, matrices, GRID.getBuffer(WORKPLANE));
            }
            for (var entry : draft.plannedBlocks()) {
                BlockPos pos = draft.worldPos(entry.relative());
                if (pos.getY() < client.world.getBottomY() || pos.getY() >= client.world.getTopY()) continue;
                int renderDistance = editing ? 256 : 128;
                if (pos.getSquaredDistance(camera) > renderDistance * renderDistance || !client.world.isChunkLoaded(pos)) continue;
                Box box = new Box(pos).expand(.002);
                if (context.frustum() != null && !context.frustum().isVisible(box)) continue;
                var actual = client.world.getBlockState(pos);
                boolean complete = actual.equals(entry.state());
                if (!editing && complete && !draft.showComplete) continue;
                boolean conflict = !complete && !actual.isReplaceable();
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
            if (editing && draft.hovered != null) {
                WorldRenderer.drawBox(matrices, lines, new Box(draft.worldPos(draft.hovered)).expand(.008), 1, 1, .65f, 1);
            }
            if (editing && draft.selection != null) {
                Box face = faceBox(draft.worldPos(draft.selection.block()), draft.selection.face(), draft.selection.point());
                fill(matrices, faces, face, 1, .82f, .25f, .3f);
                WorldRenderer.drawBox(matrices, lines, face, 1, .9f, .4f, 1);
            }
            if (editing && draft.placementPreview != null) {
                var position = draft.worldPos(draft.placementPreview);
                var bounds = new Box(position).expand(.006);
                boolean green = draft.placementValid && !draft.erasingPreview;
                WorldRenderer.drawBox(matrices, draft.lockedPlane != null ? GRID.getBuffer(WORKPLANE) : lines, bounds, green ? .5f : 1, green ? 1 : .25f, .4f, 1);
            }
            if (editing && !draft.extrusionSelection.isEmpty()) {
                for (GridPos pos : draft.extrusionSelection) {
                    Box face = faceBox(draft.worldPos(pos), draft.extrusionFace, null);
                    fill(matrices, faces, face, .25f, 1, .75f, .3f);
                    WorldRenderer.drawBox(matrices, lines, face, .4f, 1, .8f, 1);
                }
                for (var addition : draft.extrusionPreview.entrySet()) {
                    BlockPos pos = draft.worldPos(addition.getKey());
                    if (pos.getSquaredDistance(camera) > 256 * 256) continue;
                    Box bounds = new Box(pos);
                    if (context.frustum() != null && !context.frustum().isVisible(bounds)) continue;
                    WorldRenderer.drawBox(matrices, lines, bounds.expand(.003), .35f, 1, .8f, .7f);
                }
            }
            if (editing && draft.rectangleStart != null && draft.placementPreview != null) {
                BlockPos a = draft.worldPos(draft.rectangleStart), b = draft.worldPos(draft.placementPreview);
                Box region = new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
                WorldRenderer.drawBox(matrices, lines, region.expand(.01), 1f, .85f, .2f, 1f);
            }
            FACES.draw();
            BUFFERS.draw();
            GRID.draw();
        } finally { matrices.pop(); }
    }
    private static void fill(net.minecraft.client.util.math.MatrixStack matrices, VertexConsumer buffer, Box box, float r, float g, float b, float alpha) {
        WorldRenderer.renderFilledBox(matrices, buffer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, alpha);
    }
    private static Box faceBox(BlockPos pos, Face face, Vec3d point) {
        double x0 = pos.getX() - .003, y0 = pos.getY() - .003, z0 = pos.getZ() - .003;
        double x1 = pos.getX() + 1.003, y1 = pos.getY() + 1.003, z1 = pos.getZ() + 1.003;
        if (face.axis == 0) { double x = point != null ? point.x : pos.getX() + (face.x > 0 ? 1 : 0); x0 = x - .004; x1 = x + .004; }
        if (face.axis == 1) { double y = point != null ? point.y : pos.getY() + (face.y > 0 ? 1 : 0); y0 = y - .004; y1 = y + .004; }
        if (face.axis == 2) { double z = point != null ? point.z : pos.getZ() + (face.z > 0 ? 1 : 0); z0 = z - .004; z1 = z + .004; }
        return new Box(x0, y0, z0, x1, y1, z1);
    }
    private static void drawGrid(DraftSession draft, net.minecraft.client.util.math.MatrixStack matrices, VertexConsumer lines) {
        Plane plane = draft.rectangleStart != null && draft.rectanglePlane != null ? draft.rectanglePlane : draft.lockedPlane;
        int axis = plane == null ? 1 : plane.axis();
        int coordinate = plane == null ? draft.layer : plane.coordinate();
        double offset = plane == null ? .015 : plane.offset();
        int u = axis == 0 ? 1 : 0, v = axis == 2 ? 1 : 2;
        var world = MinecraftClient.getInstance().world;
        int[] min = {draft.gridX, Math.max(world.getBottomY() - draft.origin.getY(), draft.layer - 32), draft.gridZ};
        int[] max = {draft.gridX + Blueprint.WIDTH, Math.min(world.getTopY() - draft.origin.getY(), draft.layer + 32), draft.gridZ + Blueprint.DEPTH};
        for (int n = min[u]; n <= max[u]; n++) gridLine(draft, matrices, lines, axis, coordinate, offset, u, n, v, min[v], max[v]);
        for (int n = min[v]; n <= max[v]; n++) gridLine(draft, matrices, lines, axis, coordinate, offset, v, n, u, min[u], max[u]);
    }
    private static void gridLine(DraftSession draft, net.minecraft.client.util.math.MatrixStack matrices, VertexConsumer lines,
                                 int axis, int coordinate, double offset, int fixedAxis, int fixed, int variableAxis, int minimum, int maximum) {
        double[] start = {draft.origin.getX(), draft.origin.getY(), draft.origin.getZ()};
        start[axis] += coordinate + offset; start[fixedAxis] += fixed;
        double[] end = start.clone(); start[variableAxis] += minimum; end[variableAxis] += maximum;
        WorldRenderer.drawBox(matrices, lines, start[0], start[1], start[2], end[0], end[1], end[2], .3f, .72f, .77f, .38f);
    }
    public static void clear() { guideContext = null; view = null; lastSession = null; lastBlueprint = null; lastOrigin = null; lastPreview = null; previewView = null; lastRevision = -1; failed.clear(); }
}
