package dev.viridianengineering.content.pipe;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import dev.viridianengineering.registry.VEBlockEntities;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ManasteelGlassFluidPipeBlock extends GlassFluidPipeBlock {
    public ManasteelGlassFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
        Direction side = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
        ManasteelFluidPipeBlock pipe = VEBlocks.MANASTEEL_FLUID_PIPE.get();
        BlockState regular = pipe.defaultBlockState()
                .setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), true)
                .setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite()), true);
        return pipe.updateBlockState(regular, side, null, world, pos);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return context.getLevel().isClientSide ? InteractionResult.SUCCESS : super.onWrenched(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!VEBlocks.MANASTEEL_CASING.isIn(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        BlockState encased = VEBlocks.ENCASED_MANASTEEL_FLUID_PIPE.getDefaultState();
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == state.getValue(AXIS))
                encased = encased.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(direction), true);
        }
        FluidTransportBehaviour.cacheFlows(level, pos);
        level.setBlockAndUpdate(pos, encased);
        FluidTransportBehaviour.loadFlows(level, pos);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult hit, LevelReader level, BlockPos pos, Player player) {
        return VEBlocks.MANASTEEL_FLUID_PIPE.asStack();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity entity) {
        return ItemRequirement.of(VEBlocks.MANASTEEL_FLUID_PIPE.getDefaultState(), entity);
    }

    @Override
    public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
        return VEBlockEntities.MANASTEEL_GLASS_FLUID_PIPE.get();
    }
}
