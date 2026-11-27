package dev.viridianengineering.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiConsumer;

/**
 * A structural block of a formed multiblock (Immersive-Engineering style): while {@link #FORMED} its
 * own model is invisible and the controller's renderer draws the whole structure as one shaped model.
 * Removing a formed part tells its controller to re-validate at once, so the big model never shows a
 * hole.
 */
public class FormedPartBlock extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(FORMED)) return pos;
        BlockPos reactor = dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.INDEX.controllerOf(level, pos);
        if (reactor != null) return reactor;
        var battery = dev.viridianengineering.content.battery.BatteryControllerBlockEntity.owning(level, pos);
        return battery == null ? pos : battery.getBlockPos();
    }


    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private final BiConsumer<Level, BlockPos> onFormedPartRemoved;

    public FormedPartBlock(Properties p, BiConsumer<Level, BlockPos> onFormedPartRemoved) {
        super(p);
        this.onFormedPartRemoved = onFormedPartRemoved;
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        // moved = a Sable ship (dis)assembling: the structure travels as a whole and re-forms at its new place
        if (!level.isClientSide && !moved && state.getValue(FORMED) && !newState.is(this)) onFormedPartRemoved.accept(level, pos);
    }

    // While formed the block is an invisible stand-in: it must not cull its neighbours' faces, block
    // light or darken ambient occlusion, or the controller-drawn structure renders in the dark.
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 1f : super.getShadeBrightness(state, level, pos);
    }

    /** Sets FORMED on a block if it has the property; no neighbour updates (pipes and shapes are unaffected). */
    public static void mark(Level level, BlockPos pos, boolean formed) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(FORMED) && s.getValue(FORMED) != formed)
            level.setBlock(pos, s.setValue(FORMED, formed), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

}
