package dev.viridianengineering.content.pipe;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.registry.VEFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class ManasteelGlassFluidPipeBlockEntity extends StraightPipeBlockEntity {
    public ManasteelGlassFluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.removeIf(FluidTransportBehaviour.class::isInstance);
        behaviours.add(new StraightPipeFluidTransportBehaviour(this) {
            @Override
            public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
                return fluid.getFluidType() != VEFluids.LIQUID_MANA_TYPE.get()
                        && super.canPullFluidFrom(fluid, state, direction);
            }

            @Override
            public void addPressure(Direction side, boolean inbound, float pressure) {
                super.addPressure(side, inbound, ManasteelFluidPipeBlockEntity.scalePressure(pressure));
            }
        });
    }
}
