package dev.viridianengineering.content.pipe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Spec 3.1: a Create fluid pipe whose pump pressure is multiplied by
 * {@code manasteelPipeThroughputMultiplier} (2.0x by default), see
 * {@link ManasteelFluidPipeBlockEntity}. It carries ordinary fluids only; mana
 * travels exclusively through Terrasteel mana pipes.
 */
public class ManasteelFluidPipeBlock extends FluidPipeBlock {

    public static final MapCodec<ManasteelFluidPipeBlock> CODEC = simpleCodec(ManasteelFluidPipeBlock::new);

    public ManasteelFluidPipeBlock(Properties properties) {
        super(properties);
    }

    /**
     * A wrench on the side of a straight pipe swaps in a Manasteel sight glass.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (tryRemoveBracket(context))
            return InteractionResult.SUCCESS;
        var axis = FluidPropagator.getStraightPipeAxis(state);
        if (axis == null || context.getClickedFace().getAxis() == axis) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!level.isClientSide) {
            FluidTransportBehaviour.cacheFlows(level, pos);
            level.setBlockAndUpdate(pos, VEBlocks.MANASTEEL_GLASS_FLUID_PIPE.getDefaultState()
                    .setValue(GlassFluidPipeBlock.AXIS, axis)
                    .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED)));
            FluidTransportBehaviour.loadFlows(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return VEBlockEntities.MANASTEEL_FLUID_PIPE.get();
    }

    @Override
    protected @NotNull MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }
}
