package dev.viridianengineering.content.reactor;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Reactor blocks: controller, shaft port. The core, lava port and mana port are plain blocks. */
public final class ReactorBlocks {

    private ReactorBlocks() {}

    public static class Controller extends HorizontalDirectionalBlock implements IBE<ReactorControllerBlockEntity>,
            com.simibubi.create.content.equipment.wrench.IWrenchable {
        public static final MapCodec<Controller> CODEC = simpleCodec(Controller::new);
        public Controller(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
        @Override public Class<ReactorControllerBlockEntity> getBlockEntityClass() { return ReactorControllerBlockEntity.class; }
        @Override public BlockEntityType<? extends ReactorControllerBlockEntity> getBlockEntityType() { return VEBlockEntities.REACTOR_CONTROLLER.get(); }
        @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m) { IBE.onRemove(s, l, p, n); }
        /** Right-click opens the status panel (client only, read-only). */
        @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
            if (level.isClientSide) dev.viridianengineering.client.ControllerScreens.openReactor(pos);
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Faces outward; the shaft enters through FACING. */
    public static class ShaftPort extends DirectionalKineticBlock implements IBE<ReactorShaftPortBlockEntity>,
            com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
        @Override
        public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
            var reactor = ReactorControllerBlockEntity.owning(level, pos);
            return reactor == null ? pos : reactor.getBlockPos();
        }

        public ShaftPort(Properties p) { super(p); }
        @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
        @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
            return face == state.getValue(FACING);
        }
        @Override public Class<ReactorShaftPortBlockEntity> getBlockEntityClass() { return ReactorShaftPortBlockEntity.class; }
        @Override public BlockEntityType<? extends ReactorShaftPortBlockEntity> getBlockEntityType() { return VEBlockEntities.REACTOR_SHAFT_PORT.get(); }
    }
}
