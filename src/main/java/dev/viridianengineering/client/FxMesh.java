package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Real 3D effect geometry for {@link VERenderTypes#GLOW} (additive, POSITION_COLOR): spheres, tapered
 * cones, rings, helices, lightning arcs and flat ground strips. Shading is per vertex from the view
 * angle: {@code edge} is the brightness multiplier where a surface is seen edge-on (its silhouette) and
 * 1 where it faces the camera, so {@code edge < 1} gives a soft volumetric plume and {@code edge > 1}
 * a rim-lit energy shell. Colours blend from {@code rim} at the silhouette to {@code center} face-on.
 * Positions are in the current pose's space; {@code cam} is the camera in that same space.
 */
public final class FxMesh {

    private FxMesh() {}

    /** The camera position in the space of {@code ms}'s current pose (works on Sable ships too). */
    public static Vector3f camera(PoseStack ms) {
        return new Matrix4f(ms.last().pose()).invert().transformPosition(new Vector3f());
    }

    /** Cheap smooth pseudo-noise in about [-1, 1], animated by {@code t}. */
    public static float noise(float x, float y, float z, float t) {
        return 0.5f * Mth.sin(x * 3.1f + t * 0.13f) * Mth.sin(y * 2.7f - t * 0.11f + 1.3f)
                + 0.3f * Mth.sin(z * 4.3f + y * 1.7f + t * 0.21f)
                + 0.2f * Mth.sin((x + z) * 6.1f - t * 0.31f + x * 2.3f);
    }

    static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a) {
        vc.addVertex(m, x, y, z).setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                Mth.clamp(a, 0f, 1f));
    }

    /** Facing factor in [0, 1]: |normal · view| for a surface point. */
    private static float facing(Vector3f cam, float px, float py, float pz, float nx, float ny, float nz) {
        float vx = cam.x - px, vy = cam.y - py, vz = cam.z - pz;
        float len = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-4f) return 1f;
        return Math.abs(nx * vx + ny * vy + nz * vz) / len;
    }

    // ------------------------------------------------------------------ sphere

    /**
     * A lat/long sphere of radius {@code r} at (cx, cy, cz). {@code wobble} displaces the surface by
     * that fraction of {@code r} with animated noise (a turbulent fireball); {@code detail} = rings.
     */
    public static void sphere(VertexConsumer vc, Matrix4f m, Vector3f cam, float cx, float cy, float cz, float r,
                              int center, int rim, float alpha, float edge, float wobble, float t, int detail) {
        if (r <= 0.001f || alpha <= 0.002f) return;
        int lat = Math.max(4, detail), lon = lat * 2, w = lon + 1;
        int n = (lat + 1) * w;
        float[] px = new float[n], py = new float[n], pz = new float[n], pa = new float[n];
        int[] pc = new int[n];
        for (int i = 0; i <= lat; i++) {
            float th = Mth.PI * i / lat, st = Mth.sin(th), ct = Mth.cos(th);
            for (int j = 0; j <= lon; j++) {
                float ph = Mth.TWO_PI * (j % lon) / lon;
                float dx = st * Mth.cos(ph), dy = ct, dz = st * Mth.sin(ph);
                float rr = r * (1f + wobble * noise(dx * 2f, dy * 2f, dz * 2f, t));
                int k = i * w + j;
                px[k] = cx + dx * rr; py[k] = cy + dy * rr; pz[k] = cz + dz * rr;
                float f = facing(cam, px[k], py[k], pz[k], dx, dy, dz);
                pa[k] = alpha * Mth.lerp(f, edge, 1f);
                pc[k] = mix(rim, center, f);
            }
        }
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                int a = i * w + j, b = a + w, c = b + 1, d = a + 1;
                vtx(vc, m, px[a], py[a], pz[a], pc[a], pa[a]);
                vtx(vc, m, px[b], py[b], pz[b], pc[b], pa[b]);
                vtx(vc, m, px[c], py[c], pz[c], pc[c], pa[c]);
                vtx(vc, m, px[d], py[d], pz[d], pc[d], pa[d]);
            }
    }

    // ------------------------------------------------------------------ cone / tube

    /** Two unit vectors perpendicular to {@code axis} (and to each other). */
    static Vector3f[] basis(Vector3f axis) {
        Vector3f helper = Math.abs(axis.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(axis).cross(helper).normalize();
        Vector3f v = new Vector3f(axis).cross(u).normalize();
        return new Vector3f[]{u, v};
    }

    /**
     * A tapered tube from {@code start} along {@code axis} (unit) for {@code len}: radius r0 → r1,
     * alpha a0 → a1. The surface ripples with noise flowing along the axis (a plume or beam).
     */
    public static void cone(VertexConsumer vc, Matrix4f m, Vector3f cam, Vector3f start, Vector3f axis, float len,
                            float r0, float r1, int center, int rim, float a0, float a1, float edge, float wobble,
                            float t, int seg, int rings) {
        if (len <= 0.001f || Math.max(a0, a1) <= 0.002f) return;
        Vector3f[] uv = basis(axis);
        Vector3f u = uv[0], v = uv[1];
        rings = Math.max(1, rings);
        int w = seg + 1, n = (rings + 1) * w;
        float[] px = new float[n], py = new float[n], pz = new float[n], pa = new float[n];
        int[] pc = new int[n];
        for (int i = 0; i <= rings; i++) {
            float s = i / (float) rings, along = s * len;
            float base = Mth.lerp(s, r0, r1), al = Mth.lerp(s, a0, a1);
            float ox = start.x + axis.x * along, oy = start.y + axis.y * along, oz = start.z + axis.z * along;
            for (int j = 0; j <= seg; j++) {
                float ph = Mth.TWO_PI * (j % seg) / seg, c = Mth.cos(ph), sn = Mth.sin(ph);
                float nx = u.x * c + v.x * sn, ny = u.y * c + v.y * sn, nz = u.z * c + v.z * sn;
                float rr = base * (1f + wobble * noise(c * 1.7f, sn * 1.7f, along * 1.3f - t * 0.6f, t));
                int k = i * w + j;
                px[k] = ox + nx * rr; py[k] = oy + ny * rr; pz[k] = oz + nz * rr;
                float f = facing(cam, px[k], py[k], pz[k], nx, ny, nz);
                pa[k] = al * Mth.lerp(f, edge, 1f);
                pc[k] = mix(rim, center, f);
            }
        }
        for (int i = 0; i < rings; i++)
            for (int j = 0; j < seg; j++) {
                int a = i * w + j, b = a + w, c = b + 1, d = a + 1;
                vtx(vc, m, px[a], py[a], pz[a], pc[a], pa[a]);
                vtx(vc, m, px[b], py[b], pz[b], pc[b], pa[b]);
                vtx(vc, m, px[c], py[c], pz[c], pc[c], pa[c]);
                vtx(vc, m, px[d], py[d], pz[d], pc[d], pa[d]);
            }
    }

    // ------------------------------------------------------------------ ring

    /**
     * A flat soft band around {@code c} in the plane perpendicular to {@code axis}, centred on radius
     * {@code r}, {@code width} wide (fades to nothing on both edges). With {@code dashes > 0} the band
     * is broken into that many glowing dashes, turned by {@code turn} radians (a rotating rune ring).
     */
    public static void ring(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f axis, float r, float width,
                            int rgb, float alpha, int dashes, float turn) {
        if (r <= 0.001f || alpha <= 0.002f) return;
        Vector3f[] uv = basis(axis);
        Vector3f u = uv[0], v = uv[1];
        int seg = Mth.clamp((int) (r * 12), 24, 160);
        if (dashes > 0) seg = Math.max(seg, dashes * 8);
        float[] radii = {Math.max(0, r - width / 2), r, r + width / 2};
        for (int band = 0; band < 2; band++) {
            float ri = radii[band], ro = radii[band + 1];
            float ai = band == 0 ? 0 : 1, ao = band == 0 ? 1 : 0;
            for (int j = 0; j < seg; j++) {
                float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
                float d0 = dash(p0, dashes, turn), d1 = dash(p1, dashes, turn);
                float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
                ringV(vc, m, c, u, v, c0, s0, ri, rgb, alpha * ai * d0);
                ringV(vc, m, c, u, v, c0, s0, ro, rgb, alpha * ao * d0);
                ringV(vc, m, c, u, v, c1, s1, ro, rgb, alpha * ao * d1);
                ringV(vc, m, c, u, v, c1, s1, ri, rgb, alpha * ai * d1);
            }
        }
    }

    private static float dash(float phi, int dashes, float turn) {
        if (dashes <= 0) return 1f;
        float s = 0.5f + 0.5f * Mth.sin(phi * dashes + turn);
        return s * s;
    }

    private static void ringV(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f u, Vector3f v, float cs, float sn,
                              float r, int rgb, float a) {
        vtx(vc, m, c.x + (u.x * cs + v.x * sn) * r, c.y + (u.y * cs + v.y * sn) * r, c.z + (u.z * cs + v.z * sn) * r, rgb, a);
    }

    // ------------------------------------------------------------------ helix

    /** A glowing band spiralling round {@code axis} at {@code radius}, {@code turns} times over {@code len}. */
    public static void helix(VertexConsumer vc, Matrix4f m, Vector3f start, Vector3f axis, float len, float radius,
                             float turns, float phase, float width, int rgb, float alpha) {
        if (len <= 0.01f || alpha <= 0.002f) return;
        Vector3f[] uv = basis(axis);
        Vector3f u = uv[0], v = uv[1];
        int steps = Mth.clamp((int) (turns * 24), 12, 600);
        float hw = width / 2;
        for (int i = 0; i < steps; i++) {
            float s0 = i / (float) steps, s1 = (i + 1) / (float) steps;
            float fade0 = Math.min(1, Math.min(s0 * len, (1 - s0) * len) / 1.5f);
            float fade1 = Math.min(1, Math.min(s1 * len, (1 - s1) * len) / 1.5f);
            Vector3f p0 = helixPoint(start, axis, u, v, len, radius, turns, phase, s0);
            Vector3f p1 = helixPoint(start, axis, u, v, len, radius, turns, phase, s1);
            vtx(vc, m, p0.x - axis.x * hw, p0.y - axis.y * hw, p0.z - axis.z * hw, rgb, alpha * fade0);
            vtx(vc, m, p0.x + axis.x * hw, p0.y + axis.y * hw, p0.z + axis.z * hw, rgb, alpha * fade0);
            vtx(vc, m, p1.x + axis.x * hw, p1.y + axis.y * hw, p1.z + axis.z * hw, rgb, alpha * fade1);
            vtx(vc, m, p1.x - axis.x * hw, p1.y - axis.y * hw, p1.z - axis.z * hw, rgb, alpha * fade1);
        }
    }

    private static Vector3f helixPoint(Vector3f start, Vector3f axis, Vector3f u, Vector3f v, float len, float radius,
                                       float turns, float phase, float s) {
        float ang = phase + turns * Mth.TWO_PI * s, c = Mth.cos(ang), sn = Mth.sin(ang);
        return new Vector3f(start).add(new Vector3f(axis).mul(s * len))
                .add(u.x * c * radius + v.x * sn * radius, u.y * c * radius + v.y * sn * radius, u.z * c * radius + v.z * sn * radius);
    }

    // ------------------------------------------------------------------ lines

    /** A camera-facing glowing strip from p0 to p1 (alpha a0 → a1). */
    public static void line(VertexConsumer vc, Matrix4f m, Vector3f cam, Vector3f p0, Vector3f p1, float width,
                            int rgb, float a0, float a1) {
        Vector3f dir = new Vector3f(p1).sub(p0);
        if (dir.lengthSquared() < 1e-8f) return;
        Vector3f mid = new Vector3f(p0).add(p1).mul(0.5f);
        Vector3f side = new Vector3f(dir).cross(new Vector3f(cam).sub(mid));
        if (side.lengthSquared() < 1e-8f) return;
        side.normalize().mul(width / 2);
        vtx(vc, m, p0.x - side.x, p0.y - side.y, p0.z - side.z, rgb, a0);
        vtx(vc, m, p0.x + side.x, p0.y + side.y, p0.z + side.z, rgb, a0);
        vtx(vc, m, p1.x + side.x, p1.y + side.y, p1.z + side.z, rgb, a1);
        vtx(vc, m, p1.x - side.x, p1.y - side.y, p1.z - side.z, rgb, a1);
    }

    /** A jagged lightning arc from {@code a} to {@code b}: a bright core with a wider soft glow. */
    public static void arc(VertexConsumer vc, Matrix4f m, Vector3f cam, Vector3f a, Vector3f b, long seed,
                           float jag, float width, int core, int glow, float alpha) {
        RandomSource r = RandomSource.create(seed);
        int segs = 9;
        Vector3f[] uv = basis(new Vector3f(b).sub(a).normalize());
        Vector3f prev = new Vector3f(a);
        for (int i = 1; i <= segs; i++) {
            float s = i / (float) segs, env = Mth.sin(s * Mth.PI) * jag;
            Vector3f p = new Vector3f(a).lerp(b, s);
            if (i < segs) p.add(new Vector3f(uv[0]).mul((r.nextFloat() - .5f) * 2 * env))
                    .add(new Vector3f(uv[1]).mul((r.nextFloat() - .5f) * 2 * env));
            line(vc, m, cam, prev, p, width * 4, glow, alpha * 0.25f, alpha * 0.25f);
            line(vc, m, cam, prev, p, width, core, alpha, alpha);
            prev = p;
        }
    }

    /** A flat horizontal strip on the ground from (x0, y0, z0) to (x1, y1, z1), {@code width} wide. */
    public static void flat(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float width, int rgb, float a0, float a1) {
        float dx = x1 - x0, dz = z1 - z0, len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1e-5f) return;
        float sx = -dz / len * width / 2, sz = dx / len * width / 2;
        vtx(vc, m, x0 - sx, y0, z0 - sz, rgb, a0);
        vtx(vc, m, x0 + sx, y0, z0 + sz, rgb, a0);
        vtx(vc, m, x1 + sx, y1, z1 + sz, rgb, a1);
        vtx(vc, m, x1 - sx, y1, z1 - sz, rgb, a1);
    }
}
