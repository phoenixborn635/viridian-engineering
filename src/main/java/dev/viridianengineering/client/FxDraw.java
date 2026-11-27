package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Untextured glow geometry for {@code RenderType.lightning()} (POSITION_COLOR, additive). */
final class FxDraw {

    private FxDraw() {}

    static void quad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int rgb, float alpha) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255, al = (int) (Math.max(0, Math.min(1, alpha)) * 255);
        vc.addVertex(m, a.x, a.y, a.z).setColor(r, g, bl, al);
        vc.addVertex(m, b.x, b.y, b.z).setColor(r, g, bl, al);
        vc.addVertex(m, c.x, c.y, c.z).setColor(r, g, bl, al);
        vc.addVertex(m, d.x, d.y, d.z).setColor(r, g, bl, al);
        // back side too, so the glow shows from every angle without culling tricks
        vc.addVertex(m, d.x, d.y, d.z).setColor(r, g, bl, al);
        vc.addVertex(m, c.x, c.y, c.z).setColor(r, g, bl, al);
        vc.addVertex(m, b.x, b.y, b.z).setColor(r, g, bl, al);
        vc.addVertex(m, a.x, a.y, a.z).setColor(r, g, bl, al);
    }

    /** Axis-aligned box (all 6 faces). */
    static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float alpha) {
        Vector3f[] v = {
                new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0), new Vector3f(x1, y1, z0), new Vector3f(x0, y1, z0),
                new Vector3f(x0, y0, z1), new Vector3f(x1, y0, z1), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
        quad(vc, m, v[0], v[1], v[2], v[3], rgb, alpha);
        quad(vc, m, v[5], v[4], v[7], v[6], rgb, alpha);
        quad(vc, m, v[4], v[0], v[3], v[7], rgb, alpha);
        quad(vc, m, v[1], v[5], v[6], v[2], rgb, alpha);
        quad(vc, m, v[3], v[2], v[6], v[7], rgb, alpha);
        quad(vc, m, v[4], v[5], v[1], v[0], rgb, alpha);
    }

    /** Cube centred on (cx, cy, cz). */
    static void cube(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float half, int rgb, float alpha) {
        box(vc, m, cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, rgb, alpha);
    }

    /** A rough glowing sphere: three nested cubes rotated by the caller's pose look like a soft orb. */
    static void orb(VertexConsumer vc, Matrix4f m, float r, int core, int halo, float alpha) {
        cube(vc, m, 0, 0, 0, r * 0.45f, core, alpha);
        cube(vc, m, 0, 0, 0, r * 0.7f, halo, alpha * 0.55f);
        cube(vc, m, 0, 0, 0, r, halo, alpha * 0.25f);
    }
}
