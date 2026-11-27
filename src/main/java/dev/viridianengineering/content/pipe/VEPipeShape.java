package dev.viridianengineering.content.pipe;

import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's fluid-pipe shape rules, shared by our non-fluid pipes (Terrasteel mana pipe, Corporea
 * pipe) so they look and connect exactly like Create pipes and the Manasteel pipe:
 * <ul>
 *   <li>The six {@code PipeBlock} booleans mean "open on this side". A pipe is open where it links;
 *       with one link it is also open on the opposite side (a straight end with a rim), and with no
 *       links it stands upright.</li>
 *   <li>Per open side: another pipe of the same kind gets a plain connection; a linked machine
 *       gets a drain flange; an open end with nothing
 *       linked gets a rim; a Create pump draws its own coupling.</li>
 *   <li>More than two openings in one plane draw Create's junction casing.</li>
 * </ul>
 */
public final class VEPipeShape {

    private VEPipeShape() {}

    /** Implemented by our pipe blocks. */
    public interface Pipe {
        /** Whether this pipe is really linked to what is on {@code side} (not merely open there). */
        boolean linked(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side);

        /** Whether {@code other} is a pipe of the same kind (they join with plain connections). */
        boolean samePipe(BlockState other);

        /** Fitting for a pipe that actually joins this block. */
        default AttachmentTypes connectedAttachment(BlockState state, Direction side, boolean joinsPipe) {
            return joinsPipe ? AttachmentTypes.CONNECTION : AttachmentTypes.DRAIN;
        }
    }

    public static boolean open(BlockState state, Direction side) {
        return state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    /** Applies Create's opening rule to the linked sides (index = {@code Direction.get3DDataValue()}). */
    public static BlockState withOpenings(BlockState state, boolean[] linked) {
        int count = 0;
        Direction only = null;
        for (Direction d : Direction.values())
            if (linked[d.get3DDataValue()]) { count++; only = d; }
        for (Direction d : Direction.values()) {
            boolean open = linked[d.get3DDataValue()]
                    || (count == 1 && d == only.getOpposite())
                    || (count == 0 && d.getAxis() == Direction.Axis.Y);
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), open);
        }
        return state;
    }

    /** The axis of a straight pipe (exactly two opposite openings), else null. */
    public static Direction.Axis straightAxis(BlockState state) {
        Direction.Axis axis = null;
        int count = 0;
        for (Direction d : Direction.values()) {
            if (!open(state, d)) continue;
            count++;
            if (axis == null) axis = d.getAxis();
            else if (axis != d.getAxis()) return null;
        }
        return count == 2 ? axis : null;
    }

    /** Create's {@code FluidPipeBlock.shouldDrawCasing}: more than two openings around one axis. */
    public static boolean isJunction(BlockState state) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            int around = 0;
            for (Direction d : Direction.values())
                if (d.getAxis() != axis && open(state, d)) around++;
            if (around > 2) return true;
        }
        return false;
    }

    public static AttachmentTypes attachment(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side) {
        if (!(state.getBlock() instanceof Pipe pipe) || !open(state, side)) return AttachmentTypes.NONE;
        BlockState neighbor = world.getBlockState(pos.relative(side));
        if (pipe.samePipe(neighbor) && open(neighbor, side.getOpposite()))
            return pipe.connectedAttachment(state, side, true);
        if (neighbor.getBlock() instanceof PumpBlock)
            return AttachmentTypes.DETAILED_CONNECTION; // flange meets the pump's coupling
        return pipe.linked(world, pos, state, side)
                ? pipe.connectedAttachment(state, side, false) : AttachmentTypes.RIM;
    }
}
