package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A branching network of glowing fractures that runs out across the ground from a centre, hugging
 * the terrain (each point sits on the top face of the block below it). Built once from the level and
 * then revealed progressively: {@code reveal} is how far along its paths (in blocks) it has spread.
 */
final class GroundCracks {

    /** Per segment: x0, y0, z0, x1, y1, z1 (relative to the centre), d0, d1 (path distance), width. */
    private final float[] seg;
    final int count;
    final float maxDistance;
    final double cx, cy, cz;

    private GroundCracks(float[] seg, int count, float maxDistance, double cx, double cy, double cz) {
        this.seg = seg; this.count = count; this.maxDistance = maxDistance; this.cx = cx; this.cy = cy; this.cz = cz;
    }

    /**
     * Builds the network around (cx, cy, cz): branches start {@code start} blocks out and run up to
     * {@code maxLen}; the ground is searched from {@code up} blocks above {@code cy} to {@code down} below.
     */
    static GroundCracks build(BlockGetter level, double cx, double cy, double cz, float start, float maxLen,
                              long seed, int up, int down) {
        RandomSource r = RandomSource.create(seed);
        List<float[]> out = new ArrayList<>();
        int branches = 10 + r.nextInt(6);
        for (int b = 0; b < branches; b++) {
            float ang = b * Mth.TWO_PI / branches + (r.nextFloat() - .5f) * 0.45f;
            float x = Mth.cos(ang) * start, z = Mth.sin(ang) * start;
            float y = surface(level, cx + x, cz + z, cy, up, down);
            if (Float.isNaN(y)) continue;
            walk(level, r, out, cx, cy, cz, up, down, x, y, z, ang, 0, maxLen * (0.6f + 0.4f * r.nextFloat()), 0.2f, 0);
        }
        float[] flat = new float[out.size() * 9];
        float max = 1;
        for (int i = 0; i < out.size(); i++) {
            System.arraycopy(out.get(i), 0, flat, i * 9, 9);
            max = Math.max(max, out.get(i)[7]);
        }
        return new GroundCracks(flat, out.size(), max, cx, cy, cz);
    }

    private static void walk(BlockGetter level, RandomSource r, List<float[]> out, double cx, double cy, double cz,
                             int up, int down, float x, float y, float z, float ang, float d, float len, float width, int depth) {
        int guard = 0;
        while (d < len && guard++ < 400 && out.size() < 4000) {
            float step = 0.45f + r.nextFloat() * 0.5f;
            ang += (r.nextFloat() - .5f) * 0.75f;
            float nx = x + Mth.cos(ang) * step, nz = z + Mth.sin(ang) * step;
            float ny = surface(level, cx + nx, cz + nz, cy, up, down);
            if (Float.isNaN(ny)) return; // ran off a cliff or into a wall
            out.add(new float[]{x, y, z, nx, ny, nz, d, d + step, width});
            if (depth < 3 && r.nextFloat() < 0.075f) {
                float turn = (0.45f + r.nextFloat() * 0.7f) * (r.nextBoolean() ? 1 : -1);
                walk(level, r, out, cx, cy, cz, up, down, nx, ny, nz, ang + turn, d + step,
                        d + step + (len - d) * (0.35f + 0.4f * r.nextFloat()), width * 0.6f, depth + 1);
            }
            x = nx; y = ny; z = nz; d += step;
        }
    }

    /** Height (relative to cy) of the top face of the ground at (x, z), or NaN if none in range. */
    private static float surface(BlockGetter level, double x, double z, double cy, int up, int down) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int base = Mth.floor(cy);
        for (int y = base + up; y >= base - down; y--) {
            p.set(Mth.floor(x), y, Mth.floor(z));
            VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            p.move(Direction.UP);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return Float.NaN; // buried
            return (float) (y + shape.max(Direction.Axis.Y) - cy) + 0.03f;
        }
        return Float.NaN;
    }

    /**
     * Draws the cracks spread {@code reveal} blocks out. (ox, oy, oz) = centre in the pose's space.
     * Fresh tips burn brighter; widths taper toward the ends.
     */
    void render(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float reveal, float alpha,
                int core, int glow, float widthMul) {
        if (alpha <= 0.002f || reveal <= 0) return;
        for (int i = 0; i < count; i++) {
            int o = i * 9;
            float d0 = seg[o + 6], d1 = seg[o + 7];
            if (d0 >= reveal) continue;
            float f = Math.min(1f, (reveal - d0) / (d1 - d0));
            float x0 = seg[o], y0 = seg[o + 1], z0 = seg[o + 2];
            float x1 = Mth.lerp(f, x0, seg[o + 3]), y1 = Mth.lerp(f, y0, seg[o + 4]), z1 = Mth.lerp(f, z0, seg[o + 5]);
            float w = seg[o + 8] * (1f - 0.7f * d0 / maxDistance) * widthMul;
            float tip = reveal - d0 < 2.5f ? 1f + 0.8f * (1f - (reveal - d0) / 2.5f) : 1f;
            FxMesh.flat(vc, m, ox + x0, oy + y0, oz + z0, ox + x1, oy + y1, oz + z1, w * 5f, glow, alpha * 0.16f, alpha * 0.16f);
            FxMesh.flat(vc, m, ox + x0, oy + y0 + 0.005f, oz + z0, ox + x1, oy + y1 + 0.005f, oz + z1, w, core,
                    alpha * tip, alpha * tip);
        }
    }

    /** A random point on the cracks already revealed (relative to the centre), or null. */
    Vec3 randomPoint(RandomSource r, float reveal) {
        for (int tries = 0; tries < 8 && count > 0; tries++) {
            int o = r.nextInt(count) * 9;
            if (seg[o + 6] < reveal) return new Vec3(seg[o], seg[o + 1], seg[o + 2]);
        }
        return null;
    }
}
