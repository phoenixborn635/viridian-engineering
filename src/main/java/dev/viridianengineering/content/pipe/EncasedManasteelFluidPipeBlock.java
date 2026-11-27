package dev.viridianengineering.content.pipe;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import dev.viridianengineering.registry.VEBlockEntities;
import dev.viridianengineering.registry.VEBlocks;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.function.Supplier;

/** Manasteel pipe inside a Manasteel Casing. Same behaviour, never turns into a copper pipe. */
public class EncasedManasteelFluidPipeBlock extends EncasedPipeBlock {

    public EncasedManasteelFluidPipeBlock(Properties properties, Supplier<Block> casing) {
        super(properties, casing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState pipe = VEBlocks.MANASTEEL_FLUID_PIPE.get().getStateForPlacement(context);
        return pipe == null ? null : transferSixWayProperties(pipe, defaultBlockState());
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (world.isClientSide)
            return InteractionResult.SUCCESS;

        world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        BlockState equivalentPipe =
                transferSixWayProperties(state, VEBlocks.MANASTEEL_FLUID_PIPE.getDefaultState());

        Direction firstFound = Direction.UP;
        for (Direction d : Iterate.directions)
            if (state.getValue(FACING_TO_PROPERTY_MAP.get(d))) {
                firstFound = d;
                break;
            }

        FluidTransportBehaviour.cacheFlows(world, pos);
        world.setBlockAndUpdate(pos, VEBlocks.MANASTEEL_FLUID_PIPE.get()
                .updateBlockState(equivalentPipe, firstFound, null, world, pos));
        FluidTransportBehaviour.loadFlows(world, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return VEBlocks.MANASTEEL_FLUID_PIPE.asStack();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity be) {
        return ItemRequirement.of(VEBlocks.MANASTEEL_FLUID_PIPE.getDefaultState(), be);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return VEBlockEntities.MANASTEEL_FLUID_PIPE.get();
    }
}
