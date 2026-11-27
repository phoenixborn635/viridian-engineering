package dev.viridianengineering.content.drive;

import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaNode;
import dev.viridianengineering.content.corporea.CorporeaShapes;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.PipeBlock;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Corporea Archive Pylon pedestal. Right-click with a Terra Drive to mount it; right-click with an
 * empty hand to take the last drive out. The four front bays light up per active drive; a floating
 * crystal is drawn above by {@code ArchivePylonRenderer}. Connects to corporea pipes like any node.
 */
public class ArchivePylonBlock extends Block implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener, CorporeaNode, IBE<ArchivePylonBlockEntity> {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty[] BAY = {
            BooleanProperty.create("bay_0"), BooleanProperty.create("bay_1"),
            BooleanProperty.create("bay_2"), BooleanProperty.create("bay_3")};

    /** Plinth, body and cap; matches tools/gen_archive_pylon.py (square, so facing-independent). */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(1, 2, 1, 15, 11, 15),
            Block.box(0, 11, 0, 16, 13, 16)).optimize();
    private static final java.util.Map<BlockState, VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public ArchivePylonBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty bay : BAY) state = state.setValue(bay, false);
        for (Direction d : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING).add(BAY);
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }

    /** Pipes plug into the three plain sides and the bottom; the drive bays (front) and the crystal (top) stay clear. */
    @Override
    public boolean acceptsPipe(BlockState state, Direction side) {
        return side != state.getValue(FACING) && side != Direction.UP;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (Direction d : Direction.values())
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d),
                    CorporeaNode.socketAt(state, d, context.getLevel().getBlockState(context.getClickedPos().relative(d))));
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), CorporeaNode.socketAt(state, side, neighbor));
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, s -> {
            VoxelShape shape = SHAPE;
            for (Direction d : Direction.values())
                if (s.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) shape = Shapes.or(shape, CorporeaShapes.SOCKET.get(d));
            return shape.optimize();
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof TerraDriveItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return onBlockEntityUseItemOn(level, pos, pylon -> {
            ArchivePylonBlockEntity.MountResult result = pylon.mount(stack);
            if (result == ArchivePylonBlockEntity.MountResult.MOUNTED) {
                stack.consume(1, player);
                return ItemInteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.viridian_engineering.archive_pylon."
                    + result.name().toLowerCase(java.util.Locale.ROOT)), true);
            return ItemInteractionResult.FAIL;
        });
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return onBlockEntityUse(level, pos, pylon -> {
            ItemStack drive = pylon.ejectLast();
            if (drive.isEmpty()) return InteractionResult.PASS;
            if (!player.addItem(drive)) player.drop(drive, false);
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        CorporeaNetworkManager.invalidate(level);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        CorporeaNetworkManager.invalidate(level);
        if (!moving && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArchivePylonBlockEntity pylon)
            pylon.dropDrives();
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override public Class<ArchivePylonBlockEntity> getBlockEntityClass() { return ArchivePylonBlockEntity.class; }
    @Override public BlockEntityType<? extends ArchivePylonBlockEntity> getBlockEntityType() { return VEStorage.ARCHIVE_PYLON_BE.get(); }

    /** Sable moved this block onto or off a ship: its saved records follow it (spec 0.2 endpoint identity). */
    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                          BlockState state, BlockPos oldPos, BlockPos newPos) {
        dev.viridianengineering.compat.EndpointRekey.moved(oldLevel, newLevel, oldPos, newPos);
    }
}
