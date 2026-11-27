package dev.viridianengineering.content.airship;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Bifrost trail block (spec 3.8): non-ticking (no block entity, no random ticks), light-neutral
 * (emits none, blocks none). {@link BifrostTrails} places it, fades it ({@link #AGE} 0..3) and removes
 * it when its 200 ticks are up. Players can walk on it; airships pass through it, so a ship never
 * snags on its own trail.
 */
public class BifrostTrailBlock extends Block implements BlockSubLevelCollisionShape {

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);

    public BifrostTrailBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(AGE);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1f; }

    /** Neighbouring trail blocks don't draw the faces between them (one smooth ribbon, like glass). */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction dir) {
        return neighbour.getBlock() == this || super.skipRendering(state, neighbour, dir);
    }
}
