package dev.viridianengineering.content.corporea;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hitboxes for the shaped Corporea models (see tools/gen_corporea_models.py; keep them in sync).
 * Each part is a box that is square and centred across the block, described by its side span
 * [lo, hi] and its depth [d0, d1] measured from the front face, so one table covers all 6 facings.
 */
public final class CorporeaShapes {

    /** Mouth plate, living-rock body, pipe neck. */
    public static final Map<Direction, VoxelShape> PORT = build(
            new double[] {1, 15, 0, 3},
            new double[] {3, 13, 3, 11},
            new double[] {4, 12, 11, 16});

    /** Side joint drawn where a corporea pipe meets a port; keyed by the side it sits on. */
    public static final Map<Direction, VoxelShape> PORT_STUB = build(new double[] {4, 12, 0, 3});

    /** Flange plate where a corporea pipe plugs into a terminal or pylon; keyed by the side it sits on. */
    public static final Map<Direction, VoxelShape> SOCKET = build(
            new double[] {2.875, 13.125, -.625, .5},
            new double[] {4, 12, 0, 1.25});

    /** Front frame, rear housing, and the half shaft at the back. */
    public static final Map<Direction, VoxelShape> TERMINAL = build(
            new double[] {0, 16, 3, 8},
            new double[] {1, 15, 8, 14},
            new double[] {6, 10, 14, 16});

    private static Map<Direction, VoxelShape> build(double[]... parts) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction front : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] p : parts)
                shape = Shapes.or(shape, box(front, p[0], p[1], p[2], p[3]));
            map.put(front, shape.optimize());
        }
        return map;
    }

    private static VoxelShape box(Direction front, double lo, double hi, double d0, double d1) {
        double n0 = d0, n1 = d1, p0 = 16 - d1, p1 = 16 - d0; // depth along -axis / +axis
        return switch (front) {
            case NORTH -> Block.box(lo, lo, n0, hi, hi, n1);
            case SOUTH -> Block.box(lo, lo, p0, hi, hi, p1);
            case WEST -> Block.box(n0, lo, lo, n1, hi, hi);
            case EAST -> Block.box(p0, lo, lo, p1, hi, hi);
            case DOWN -> Block.box(lo, n0, lo, hi, n1, hi);
            case UP -> Block.box(lo, p0, lo, hi, p1, hi);
        };
    }

    private CorporeaShapes() {}
}
