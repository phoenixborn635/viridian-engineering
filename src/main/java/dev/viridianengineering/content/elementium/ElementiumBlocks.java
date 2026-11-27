package dev.viridianengineering.content.elementium;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.serialization.MapCodec;
import dev.viridianengineering.content.PhaseFourShapes;

/** The three Elementium logistics blocks. Models face NORTH; hitboxes come from {@link PhaseFourShapes}. */
public final class ElementiumBlocks {

    private ElementiumBlocks() {}

    /** Faces the inventory it fills (any of 6 directions); placed against the target, pulling from behind. */
    public static class SmartFunnel extends DirectionalBlock implements IBE<SmartFunnelBlockEntity>, IWrenchable {
        public static final MapCodec<SmartFunnel> CODEC = simpleCodec(SmartFunnel::new);
        public SmartFunnel(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
        @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            // Clicking a chest's face places the funnel facing into that chest.
            return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
        }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return PhaseFourShapes.FUNNEL.get(s.getValue(FACING));
        }
        @Override public Class<SmartFunnelBlockEntity> getBlockEntityClass() { return SmartFunnelBlockEntity.class; }
        @Override public BlockEntityType<? extends SmartFunnelBlockEntity> getBlockEntityType() { return VEBlockEntities.ELEMENTIUM_SMART_FUNNEL.get(); }
        @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m) { IBE.onRemove(s, l, p, n); }
    }

    /** Horizontal router: flow runs along FACING; requested or filtered items leave on the counter-clockwise side. */
    public static class SmartTunnel extends HorizontalDirectionalBlock implements IBE<SmartTunnelBlockEntity>, IWrenchable {
        public static final MapCodec<SmartTunnel> CODEC = simpleCodec(SmartTunnel::new);
        public SmartTunnel(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return PhaseFourShapes.TUNNEL.get(s.getValue(FACING));
        }
        @Override public Class<SmartTunnelBlockEntity> getBlockEntityClass() { return SmartTunnelBlockEntity.class; }
        @Override public BlockEntityType<? extends SmartTunnelBlockEntity> getBlockEntityType() { return VEBlockEntities.ELEMENTIUM_SMART_TUNNEL.get(); }
        @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m) { IBE.onRemove(s, l, p, n); }
    }

    /**
     * Holds the open requests. Right-click with an item: request that stack (sneak: 64 more of it).
     * Empty hand opens the status panel; sneak with an empty hand clears the requests.
     */
    public static class RequestInterface extends HorizontalDirectionalBlock implements IBE<RequestInterfaceBlockEntity>, IWrenchable {
        public static final MapCodec<RequestInterface> CODEC = simpleCodec(RequestInterface::new);
        public RequestInterface(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return PhaseFourShapes.REQUEST_INTERFACE.get(s.getValue(FACING));
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty() || stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem)
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) withBlockEntityDo(level, pos, be -> {
                long n = player.isShiftKeyDown() ? 64 : stack.getCount();
                be.addRequest(stack, n);
                player.displayClientMessage(Component.translatable("viridian_engineering.request_interface.added", n, stack.getHoverName(),
                        be.outstanding(stack)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            });
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) withBlockEntityDo(level, pos, be -> {
                    be.clearRequests();
                    player.displayClientMessage(Component.translatable("viridian_engineering.request_interface.cleared"), true);
                });
            } else if (level.isClientSide) {
                dev.viridianengineering.client.ControllerScreens.openRequests(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override public Class<RequestInterfaceBlockEntity> getBlockEntityClass() { return RequestInterfaceBlockEntity.class; }
        @Override public BlockEntityType<? extends RequestInterfaceBlockEntity> getBlockEntityType() { return VEBlockEntities.ELEMENTIUM_REQUEST_INTERFACE.get(); }
        @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m) { IBE.onRemove(s, l, p, n); }
    }
}
