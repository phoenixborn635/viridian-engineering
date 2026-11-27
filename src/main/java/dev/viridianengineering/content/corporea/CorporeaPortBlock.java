package dev.viridianengineering.content.corporea;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The front face points at the inventory. Bridge ports never expose an item handler. */
public class CorporeaPortBlock extends DirectionalBlock implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener, CorporeaNode, IWrenchable {
    private static final java.util.Map<BlockState, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();
    public static final MapCodec<CorporeaPortBlock> CODEC = simpleCodec(CorporeaPortBlock::new);
    public CorporeaPortBlock(Properties properties) {
        super(properties);
        BlockState def = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (Direction d : Direction.values()) def = def.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), false);
        registerDefaultState(def);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        for (Direction d : Direction.values())
            state = withStub(state, d, context.getLevel().getBlockState(context.getClickedPos().relative(d)));
        return state;
    }
    /** A side stub (visual joint) is drawn where a corporea pipe meets the port from the side. */
    private static BlockState withStub(BlockState state, Direction side, BlockState neighbor) {
        boolean stub = side.getAxis() != state.getValue(FACING).getAxis() && neighbor.getBlock() instanceof CorporeaPipeBlock;
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), stub);
    }
    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                               net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withStub(state, side, neighbor);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return SHAPES.computeIfAbsent(state, s -> {
            net.minecraft.world.phys.shapes.VoxelShape shape = CorporeaShapes.PORT.get(s.getValue(FACING));
            for (Direction d : Direction.values())
                if (s.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) shape = net.minecraft.world.phys.shapes.Shapes.or(shape, CorporeaShapes.PORT_STUB.get(d));
            return shape.optimize();
        });
    }
    @Override protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        CorporeaNetworkManager.invalidate(level);
    }
    @Override protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moving) {
        CorporeaNetworkManager.invalidate(level);
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Sable moved this block onto or off a ship: its saved records follow it (spec 0.2 endpoint identity). */
    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                          BlockState state, BlockPos oldPos, BlockPos newPos) {
        dev.viridianengineering.compat.EndpointRekey.moved(oldLevel, newLevel, oldPos, newPos);
    }
}
