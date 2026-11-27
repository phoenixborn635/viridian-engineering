package dev.viridianengineering.content.manapipe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Spec 3.2: mana-only pipe. Connects to other mana pipes, to blocks exposing
 * {@code MANA_PIPE_NODE} or Botania's {@code MANA_RECEIVER}, and to Create Mechanical
 * Pumps lying along the same axis. It never exposes a fluid handler.
 */
public class TerrasteelManaPipeBlock extends PipeBlock implements IBE<TerrasteelManaPipeBlockEntity>, IWrenchable,
        dev.viridianengineering.content.pipe.VEPipeShape.Pipe {

    public static final MapCodec<TerrasteelManaPipeBlock> CODEC = simpleCodec(TerrasteelManaPipeBlock::new);

    public TerrasteelManaPipeBlock(Properties properties) {
        super(4 / 16f, properties);
        BlockState state = stateDefinition.any();
        for (var prop : PROPERTY_BY_DIRECTION.values())
            state = state.setValue(prop, false);
        registerDefaultState(state.setValue(UP, true).setValue(DOWN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return updateConnections(level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean isMoving) {
        // A block entity (e.g. a mana pool) can gain or lose its capability without a block change.
        BlockState updated = updateConnections(level, pos, state);
        if (updated != state)
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    /**
     * Open sides follow Create's pipe rule (see VEPipeShape); which of them are real links is kept on
     * the block entity, so the model can tell a drain flange (linked machine) from an open rim.
     */
    public static BlockState updateConnections(LevelAccessor level, BlockPos pos, BlockState state) {
        boolean[] linked = new boolean[6];
        int mask = 0;
        for (Direction d : Direction.values()) {
            boolean link = connectsTo(level, pos, d, level.getBlockState(pos.relative(d)));
            linked[d.get3DDataValue()] = link;
            if (link) mask |= 1 << d.get3DDataValue();
        }
        if (level.getBlockEntity(pos) instanceof TerrasteelManaPipeBlockEntity be) be.setLinkedMask(mask);
        return dev.viridianengineering.content.pipe.VEPipeShape.withOpenings(state, linked);
    }

    @Override
    public boolean linked(net.minecraft.world.level.BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side) {
        BlockState n = world.getBlockState(pos.relative(side));
        if (n.getBlock() instanceof TerrasteelManaPipeBlock) return true;
        if (n.getBlock() instanceof PumpBlock) return n.getValue(PumpBlock.FACING).getAxis() == side.getAxis();
        return world.getBlockEntity(pos) instanceof TerrasteelManaPipeBlockEntity be && be.isLinked(side);
    }

    @Override
    public boolean samePipe(BlockState other) {
        return other.getBlock() instanceof TerrasteelManaPipeBlock;
    }

    /** Real links only (bit = Direction 3D index). */
    public static int linkedMask(LevelAccessor level, BlockPos pos) {
        int mask = 0;
        for (Direction d : Direction.values())
            if (connectsTo(level, pos, d, level.getBlockState(pos.relative(d)))) mask |= 1 << d.get3DDataValue();
        return mask;
    }

    public static boolean connectsTo(LevelAccessor level, BlockPos pos, Direction direction, BlockState neighbor) {
        if (neighbor.getBlock() instanceof TerrasteelManaPipeBlock)
            return true;
        if (neighbor.getBlock() instanceof PumpBlock)
            return neighbor.getValue(PumpBlock.FACING).getAxis() == direction.getAxis();
        if (level instanceof Level realLevel)
            return ManaEndpoint.isConnectable(realLevel, pos.relative(direction), direction.getOpposite());
        return false;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<TerrasteelManaPipeBlockEntity> getBlockEntityClass() {
        return TerrasteelManaPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TerrasteelManaPipeBlockEntity> getBlockEntityType() {
        return VEBlockEntities.TERRASTEEL_MANA_PIPE.get();
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }
}
