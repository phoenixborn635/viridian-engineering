package dev.viridianengineering.content.reactor;

import com.mojang.serialization.MapCodec;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lava / mana port. Carries an empty block entity because Create's pipes only treat a block with a
 * block entity as a fluid endpoint (without one, a pipe facing the port is an "open end" and pumps
 * nothing into it). The capabilities themselves are registered per block in PhaseFourCapabilities.
 */
public class ReactorPortBlock extends BaseEntityBlock implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    @Override
    public BlockPos getInformationSource(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        var reactor = ReactorControllerBlockEntity.owning(level, pos);
        if (reactor != null) return reactor.getBlockPos();
        var battery = dev.viridianengineering.content.battery.BatteryControllerBlockEntity.owning(level, pos);
        return battery == null ? pos : battery.getBlockPos();
    }


    public static final MapCodec<ReactorPortBlock> CODEC = simpleCodec(ReactorPortBlock::new);

    public ReactorPortBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(VEBlockEntities.REACTOR_PORT.get(), pos, state);
    }

    public static class Entity extends BlockEntity {
        public Entity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    }
}
