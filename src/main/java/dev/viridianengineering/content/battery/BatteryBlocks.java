package dev.viridianengineering.content.battery;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class BatteryBlocks {

    private BatteryBlocks() {}

    /** Faces outward from the base layer; the base centre is the block behind it. */
    public static class Controller extends HorizontalDirectionalBlock implements IBE<BatteryControllerBlockEntity> {
        public static final MapCodec<Controller> CODEC = simpleCodec(Controller::new);
        public Controller(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
        @Override public Class<BatteryControllerBlockEntity> getBlockEntityClass() { return BatteryControllerBlockEntity.class; }
        @Override public BlockEntityType<? extends BatteryControllerBlockEntity> getBlockEntityType() { return VEBlockEntities.BATTERY_CONTROLLER.get(); }
        @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m) { IBE.onRemove(s, l, p, n); }
        /** Right-click opens the status panel (client only, read-only). */
        @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
            if (level.isClientSide) dev.viridianengineering.client.ControllerScreens.openBattery(pos);
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
}
