package dev.viridianengineering.content.corporea;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network controller. The shaft connects at the back; corporea pipes plug into flange sockets on the
 * four remaining sides (never the screen or the shaft side). Socket visibility is a blockstate
 * property per side, true while a linked corporea pipe is there.
 */
public class CorporeaTerminalBlock extends DirectionalKineticBlock implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener, CorporeaNode, IBE<CorporeaTerminalBlockEntity> {

    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public CorporeaTerminalBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), false);
        registerDefaultState(state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }

    @Override public boolean acceptsPipe(BlockState state, Direction side) {
        return side.getAxis() != state.getValue(FACING).getAxis();
    }

    private static BlockState withSockets(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values())
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), CorporeaNode.socketAt(state, d, level.getBlockState(pos.relative(d))));
        return state;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withSockets(state, context.getLevel(), context.getClickedPos());
    }

    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, side, neighbor, level, pos, neighborPos);
        return updated.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), CorporeaNode.socketAt(updated, side, neighbor));
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.computeIfAbsent(state, s -> {
            VoxelShape shape = CorporeaShapes.TERMINAL.get(s.getValue(FACING));
            for (Direction d : Direction.values())
                if (s.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) shape = Shapes.or(shape, CorporeaShapes.SOCKET.get(d));
            return shape.optimize();
        });
    }
    @Override public Class<CorporeaTerminalBlockEntity> getBlockEntityClass() { return CorporeaTerminalBlockEntity.class; }
    @Override public BlockEntityType<? extends CorporeaTerminalBlockEntity> getBlockEntityType() { return VEBlockEntities.CORPOREA_TERMINAL.get(); }
    @Override public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        CorporeaNetworkManager.invalidate(level);
    }
    @Override public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!moving && !state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server
                && level.getBlockEntity(pos) instanceof CorporeaTerminalBlockEntity terminal && terminal.bindingId() != null) {
            TerminalBindings.get(server.getServer()).remove(terminal.bindingId(),
                    dev.viridianengineering.compat.SableCompat.endpointKey(server, pos, Direction.DOWN));
        }
        CorporeaNetworkManager.invalidate(level);
        super.onRemove(state, level, pos, newState, moving);
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            server.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new CorporeaTerminalMenu(id, inv, pos, false),
                            net.minecraft.network.chat.Component.translatable("block.viridian_engineering.corporea_crafting_terminal")),
                    buf -> CorporeaTerminalMenu.writeOpenData(buf, pos, false));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sable moved this block onto or off a ship: its saved records follow it (spec 0.2 endpoint identity). */
    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                          BlockState state, BlockPos oldPos, BlockPos newPos) {
        dev.viridianengineering.compat.EndpointRekey.moved(oldLevel, newLevel, oldPos, newPos);
        if (newLevel.getBlockEntity(newPos) instanceof CorporeaTerminalBlockEntity terminal && terminal.bindingId() != null)
            terminal.bindWireless(newLevel);
    }
}
