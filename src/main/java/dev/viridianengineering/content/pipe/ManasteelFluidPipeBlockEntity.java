package dev.viridianengineering.content.pipe;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Create decides how many mB/tick a pump moves from the pressure stored on the pipe
 * connections next to the fluid source ({@code transferSpeed = pressure / 2}), and how
 * fast flow fronts advance from the same pressure. Multiplying the pressure a pump
 * deposits on Manasteel pipes therefore scales their throughput with pump RPM:
 * at the same RPM a Manasteel line moves {@code multiplier} times as much fluid as a copper one.
 */
public class ManasteelFluidPipeBlockEntity extends FluidPipeBlockEntity {

    public ManasteelFluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        for (int i = 0; i < behaviours.size(); i++) {
            if (behaviours.get(i) instanceof FluidTransportBehaviour) {
                behaviours.set(i, new ManasteelPipeTransportBehaviour(this));
                return;
            }
        }
        behaviours.add(new ManasteelPipeTransportBehaviour(this));
    }

    static float scalePressure(float pressure) {
        // A Manasteel Pump already boosts its pressure.
        return dev.viridianengineering.content.pump.VEPumps.PumpBoost.active()
                ? pressure : pressure * VEConfig.MANASTEEL_PIPE_THROUGHPUT_MULTIPLIER.get().floatValue();
    }

    static class ManasteelPipeTransportBehaviour extends FluidTransportBehaviour {

        ManasteelPipeTransportBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
            return fluid.getFluidType() != VEFluids.LIQUID_MANA_TYPE.get()
                    && super.canPullFluidFrom(fluid, state, direction);
        }

        @Override
        public void addPressure(Direction side, boolean inbound, float pressure) {
            super.addPressure(side, inbound, scalePressure(pressure));
        }

        // ---- identical to Create's standard pipe behaviour (FluidPipeBlockEntity, MIT) ----

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return (FluidPipeBlock.isPipe(state) || state.getBlock() instanceof EncasedPipeBlock)
                    && state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction));
        }

        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                        Direction direction) {
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);

            BlockPos offsetPos = pos.relative(direction);
            BlockState otherState = world.getBlockState(offsetPos);

            if (state.getBlock() instanceof EncasedPipeBlock && attachment != AttachmentTypes.DRAIN)
                return AttachmentTypes.NONE;

            if (attachment == AttachmentTypes.RIM) {
                if (!FluidPipeBlock.isPipe(otherState) && !(otherState.getBlock() instanceof EncasedPipeBlock)
                        && !(otherState.getBlock() instanceof GlassFluidPipeBlock)) {
                    FluidTransportBehaviour pipeBehaviour =
                            BlockEntityBehaviour.get(world, offsetPos, FluidTransportBehaviour.TYPE);
                    if (pipeBehaviour != null && pipeBehaviour.canHaveFlowToward(otherState, direction.getOpposite()))
                        return AttachmentTypes.DETAILED_CONNECTION;
                }

                if (!FluidPipeBlock.shouldDrawRim(world, pos, state, direction))
                    return FluidPropagator.getStraightPipeAxis(state) == direction.getAxis()
                            ? AttachmentTypes.CONNECTION
                            : AttachmentTypes.DETAILED_CONNECTION;
            }

            if (attachment == AttachmentTypes.NONE
                    && state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction)))
                return AttachmentTypes.DETAILED_CONNECTION;

            return attachment;
        }
    }
}
