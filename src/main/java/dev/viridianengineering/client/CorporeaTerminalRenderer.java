package dev.viridianengineering.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Spinning half shaft sticking out of the back of the crafting terminal (its input side). */
public class CorporeaTerminalRenderer extends KineticBlockEntityRenderer<CorporeaTerminalBlockEntity> {

    public CorporeaTerminalRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    private static Direction back(BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getOpposite();
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CorporeaTerminalBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, back(state));
    }

    /** Flywheel counterpart (used instead of the renderer when a Flywheel backend is active). */
    public static SimpleBlockEntityVisualizer.Factory<CorporeaTerminalBlockEntity> visual() {
        return (context, be, partialTick) -> new OrientedRotatingVisual<>(context, be, partialTick,
                Direction.SOUTH, back(be.getBlockState()), Models.partial(AllPartialModels.SHAFT_HALF));
    }
}
