package net.schemplanner.render;

import net.minecraft.client.render.VertexConsumer;

/**
 * A VertexConsumer wrapper that forces semi-transparency on vertex colors.
 */
public class TranslucentVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int alpha;

    public TranslucentVertexConsumer(VertexConsumer delegate, int alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int originalAlpha) {
        // Enforce semi-transparency
        delegate.color(red, green, blue, this.alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void next() {
        delegate.next();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        delegate.fixedColor(red, green, blue, this.alpha);
    }

    @Override
    public void unfixColor() {
        delegate.unfixColor();
    }
}
