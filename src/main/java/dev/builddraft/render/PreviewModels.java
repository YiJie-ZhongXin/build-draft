package dev.builddraft.render;

import dev.builddraft.*;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

public final class PreviewModels {
    public static void render(DraftSession session, DraftWorldView view, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumerProvider consumers) {
        render(session, view, state, pos, matrices, consumers, session.solidPreview ? 1f : session.ghostAlpha);
    }
    public static void render(DraftSession session, DraftWorldView view, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumerProvider consumers, float alpha) {
        var client = MinecraftClient.getInstance();
        boolean translucent = alpha < .999f;
        var blocks = client.getBlockRenderManager();
        int light = WorldRenderer.getLightmapCoordinates(client.world, pos);
        matrices.push();
        try {
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            if (state.getRenderType() == BlockRenderType.MODEL) {
                var layer = translucent ? RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE) : RenderLayers.getEntityBlockLayer(state, false);
                var consumer = new AlphaVertexConsumer(consumers.getBuffer(layer), alpha);
                // World-aware renderer retains per-face tint indices and draft-neighbor culling.
                blocks.getModelRenderer().render(view, blocks.getModel(state), state, pos, matrices, consumer, true,
                    net.minecraft.util.math.random.Random.create(state.getRenderingSeed(pos)), state.getRenderingSeed(pos), OverlayTexture.DEFAULT_UV);
            }
            if (state.hasBlockEntity()) {
                var entity = view.getBlockEntity(pos);
                if (entity != null && client.getBlockEntityRenderDispatcher().get(entity) != null) {
                    VertexConsumerProvider provider = translucent ? layer -> ghostBuffer(consumers, layer, alpha) : consumers;
                    client.getBlockEntityRenderDispatcher().renderEntity(entity, matrices, provider, light, OverlayTexture.DEFAULT_UV);
                }
            }
        } finally { matrices.pop(); }

        if (!state.getFluidState().isEmpty()) {
            // FluidRenderer emits chunk-local positions; transform them through the current world matrix.
            matrices.push();
            try {
                matrices.translate(pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
                var matrix = matrices.peek();
                var consumer = new AlphaVertexConsumer(consumers.getBuffer(RenderLayer.getTranslucent()), alpha) {
                    @Override public VertexConsumer vertex(double x, double y, double z) {
                        var transformed = matrix.getPositionMatrix().transformPosition(new Vector3f((float) x, (float) y, (float) z));
                        return super.vertex(transformed.x, transformed.y, transformed.z);
                    }
                    @Override public VertexConsumer normal(float x, float y, float z) {
                        var transformed = matrix.getNormalMatrix().transform(new Vector3f(x, y, z));
                        return super.normal(transformed.x, transformed.y, transformed.z);
                    }
                };
                blocks.renderFluid(pos, view, consumer, state, state.getFluidState());
            } finally { matrices.pop(); }
        }
    }
    private static VertexConsumer ghostBuffer(VertexConsumerProvider consumers, RenderLayer layer, float alpha) {
        if (layer instanceof RenderLayer.MultiPhase multiPhase) {
            var texture = multiPhase.phases.texture.getId();
            if (texture.isPresent()) return new AlphaVertexConsumer(consumers.getBuffer(RenderLayer.getEntityTranslucent(texture.get())), alpha);
        }
        return new AlphaVertexConsumer(consumers.getBuffer(layer), alpha);
    }
}
