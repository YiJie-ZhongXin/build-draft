package dev.builddraft.render;

import net.minecraft.client.render.VertexConsumer;

/** Keeps original texture, tint, UV and lighting; only scales vertex opacity. */
public class AlphaVertexConsumer implements VertexConsumer {
    protected final VertexConsumer delegate;
    private final float alpha;
    public AlphaVertexConsumer(VertexConsumer delegate, float alpha) { this.delegate = delegate; this.alpha = alpha; }
    @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, Math.round(a * alpha)); return this; }
    @Override public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
    @Override public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
    @Override public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    @Override public void next() { delegate.next(); }
    @Override public void fixedColor(int r, int g, int b, int a) { delegate.fixedColor(r, g, b, Math.round(a * alpha)); }
    @Override public void unfixColor() { delegate.unfixColor(); }
}
