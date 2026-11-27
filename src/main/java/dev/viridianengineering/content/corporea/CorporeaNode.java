package dev.viridianengineering.content.corporea;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Marker for blocks participating in the physical Corporea graph. */
public interface CorporeaNode {

    /** Whether a corporea link may attach on {@code side}. Terminals and pylons keep their front (and shaft / crystal) clear. */
    default boolean acceptsPipe(BlockState state, Direction side) {
        return true;
    }

    /** True when {@code a} and its neighbour {@code b} (in direction {@code towardB}) are linked in the graph. */
    static boolean links(BlockState a, Direction towardB, BlockState b) {
        return a.getBlock() instanceof CorporeaNode na && b.getBlock() instanceof CorporeaNode nb
                && na.acceptsPipe(a, towardB) && nb.acceptsPipe(b, towardB.getOpposite());
    }

    /** A pipe socket is drawn on {@code side} of a non-pipe node when a corporea pipe links there. */
    static boolean socketAt(BlockState self, Direction side, BlockState neighbor) {
        return neighbor.getBlock() instanceof CorporeaPipeBlock && links(self, side, neighbor);
    }
}
