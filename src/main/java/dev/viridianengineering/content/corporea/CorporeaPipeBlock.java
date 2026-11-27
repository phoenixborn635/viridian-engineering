package dev.viridianengineering.content.corporea;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.content.pipe.VEPipeShape;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Logical item connections only; deliberately exposes no mana or fluid capability. Shaped and
 * drawn exactly like a Create / Manasteel pipe ({@link VEPipeShape}, client {@code VEPipeModel});
 * the travelling light is drawn live by {@code CorporeaPulseRenderer} from {@link CorporeaPipeBlockEntity}.
 */
public class CorporeaPipeBlock extends PipeBlock implements CorporeaNode, IWrenchable, VEPipeShape.Pipe, IBE<CorporeaPipeBlockEntity> {
    public static final MapCodec<CorporeaPipeBlock> CODEC = simpleCodec(CorporeaPipeBlock::new);

    public CorporeaPipeBlock(Properties properties) {
        super(4 / 16f, properties);
        BlockState state = stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state.setValue(UP, true).setValue(DOWN, true));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private BlockState shapeFor(BlockGetter level, BlockPos pos, BlockState state) {
        boolean[] linked = new boolean[6];
        for (Direction side : Direction.values())
            linked[side.get3DDataValue()] = CorporeaNode.links(state, side, level.getBlockState(pos.relative(side)));
        return VEPipeShape.withOpenings(state, linked);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return shapeFor(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return shapeFor(level, pos, state);
    }

    @Override public boolean linked(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side) {
        return CorporeaNode.links(state, side, world.getBlockState(pos.relative(side)));
    }

    @Override public boolean samePipe(BlockState other) { return other.getBlock() instanceof CorporeaPipeBlock; }

    @Override public AttachmentTypes connectedAttachment(BlockState state, Direction side, boolean joinsPipe) {
        // The neighbour owns its socket or stub. The Create drain would extend into that space.
        return AttachmentTypes.CONNECTION;
    }

    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }

    @Override protected MapCodec<? extends PipeBlock> codec() { return CODEC; }

    @Override protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        CorporeaNetworkManager.invalidate(level);
    }

    @Override protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moving) {
        CorporeaNetworkManager.invalidate(level);
        IBE.onRemove(state, level, pos, newState);
    }

    @Override public Class<CorporeaPipeBlockEntity> getBlockEntityClass() { return CorporeaPipeBlockEntity.class; }
    @Override public BlockEntityType<? extends CorporeaPipeBlockEntity> getBlockEntityType() { return VEBlockEntities.CORPOREA_PIPE.get(); }
}
