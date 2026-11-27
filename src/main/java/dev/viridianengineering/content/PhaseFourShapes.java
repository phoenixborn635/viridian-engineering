package dev.viridianengineering.content;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Hitboxes of the shaped Phase 4 models (tools/gen_phase4.py; keep both in sync). Boxes are given
 * for the NORTH-facing model (front at z = 0) and rotated the same way the blockstates rotate it
 * (y 90/180/270 for east/south/west, x 270 for up, x 90 for down).
 */
public final class PhaseFourShapes {

    public static final Map<Direction, VoxelShape> FUNNEL = build(
            box(1, 1, 0, 15, 15, 4),     // elementium mouth
            box(1, 0, 4, 15, 16, 12),    // living-wood body (filter slot on top)
            box(0, 0, 12, 16, 16, 16));  // living-rock back plate

    public static final Map<Direction, VoxelShape> TUNNEL = build(
            box(0, 0, 0, 16, 6, 16),     // living-rock trough
            box(13, 6, 0, 16, 14, 16),   // solid wall (east)
            box(0, 6, 0, 3, 14, 3),      // diverter posts (west)
            box(0, 6, 13, 3, 14, 16),
            box(0, 14, 0, 16, 16, 16));  // elementium roof (filter slot)

    public static final Map<Direction, VoxelShape> REQUEST_INTERFACE = build(
            box(2, 0, 2, 14, 4, 14),
            box(4, 4, 4, 12, 10, 12),
            box(1, 10, 1, 15, 13, 15),
            box(6, 13, 6, 10, 16, 10));

    public static final Map<Direction, VoxelShape> CANNON = build(
            box(1, 1, 8, 15, 15, 16),    // living-rock mount
            box(2, 2, 6, 14, 14, 8),     // gaia ring
            box(5, 5, 0, 11, 11, 6));    // barrel

    private PhaseFourShapes() {}

    private static double[] box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[] {x0, y0, z0, x1, y1, z1};
    }

    private static double[] rotate(double x, double y, double z, Direction f) {
        return switch (f) {
            case NORTH -> new double[] {x, y, z};
            case SOUTH -> new double[] {16 - x, y, 16 - z};
            case EAST -> new double[] {16 - z, y, x};
            case WEST -> new double[] {z, y, 16 - x};
            case UP -> new double[] {x, 16 - z, y};
            case DOWN -> new double[] {x, z, 16 - y};
        };
    }

    private static Map<Direction, VoxelShape> build(double[]... boxes) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction f : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double[] a = rotate(b[0], b[1], b[2], f), c = rotate(b[3], b[4], b[5], f);
                shape = Shapes.or(shape, Block.box(Math.min(a[0], c[0]), Math.min(a[1], c[1]), Math.min(a[2], c[2]),
                        Math.max(a[0], c[0]), Math.max(a[1], c[1]), Math.max(a[2], c[2])));
            }
            map.put(f, shape.optimize());
        }
        return map;
    }
}
