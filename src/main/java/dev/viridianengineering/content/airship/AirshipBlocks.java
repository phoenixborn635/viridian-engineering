package dev.viridianengineering.content.airship;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The Phase 5 airship blocks (spec 3.8). Their logic lives in the block entities. */
public final class AirshipBlocks {

    private AirshipBlocks() {}

    // ================================================================== levitation pylon (2 tall)

    /**
     * Mana Levitation Pylon: two blocks tall, placed as one. The lower half holds the block entity;
     * the upper half carries the cradle the gem floats in. Breaking either half breaks both.
     */
    public static class LevitationPylon extends Block implements IBE<LevitationPylonBlockEntity>,
            com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
        @Override
        public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
            return base(state, pos);
        }

        @Override
        public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
            return direction != null && direction.getAxis().isHorizontal();
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide) dev.viridianengineering.client.ControllerScreens.openPylon(base(state, pos));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }


        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        public static final MapCodec<LevitationPylon> CODEC = simpleCodec(LevitationPylon::new);

        private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(4, 4, 4, 12, 16, 12));
        private static final VoxelShape UPPER = Shapes.or(Block.box(4, 0, 4, 12, 7, 12), Block.box(2, 7, 2, 14, 10, 14),
                Block.box(4, 10, 4, 12, 16, 12));

        public LevitationPylon(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        }

        @Override protected MapCodec<? extends Block> codec() { return CODEC; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(HALF); }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            BlockPos pos = ctx.getClickedPos();
            Level level = ctx.getLevel();
            if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
            return defaultBlockState();
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }

        /** The two halves keep each other: if one goes, the other turns to air (like a door). */
        @Override
        protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos npos) {
            DoubleBlockHalf half = state.getValue(HALF);
            if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP))
                return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
            return super.updateShape(state, dir, neighbour, level, pos, npos);
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }

        /** Creative break of the upper half must not drop the lower half's item. */
        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                BlockPos below = pos.below();
                BlockState b = level.getBlockState(below);
                if (b.is(this) && b.getValue(HALF) == DoubleBlockHalf.LOWER)
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Nullable
        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return state.getValue(HALF) == DoubleBlockHalf.LOWER ? IBE.super.newBlockEntity(pos, state) : null;
        }

        @Override public Class<LevitationPylonBlockEntity> getBlockEntityClass() { return LevitationPylonBlockEntity.class; }
        @Override public BlockEntityType<? extends LevitationPylonBlockEntity> getBlockEntityType() { return VEBlockEntities.MANA_LEVITATION_PYLON.get(); }

        /** Lower half of the pylon at or under {@code pos}. */
        public static BlockPos base(BlockState state, BlockPos pos) {
            return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        }
    }

    // ================================================================== gaia thruster (3x3x3)

    /**
     * Gaia Thruster: a 3x3x3 engine placed as one item, like the Gaia cannon. This block is the centre
     * (block entity + the whole model); the other 26 positions are invisible {@link ThrusterPart}s.
     * Its foot mounts against the clicked face and the bell points away from it ({@code FACING}), so
     * the ship is pushed toward the mounting face. Mana comes in through the foot's mana port and the
     * throttle signal through its redstone port (the same port layout as the cannon).
     */
    public static class GaiaThruster extends DirectionalBlock implements IBE<GaiaThrusterBlockEntity> {
        public static final MapCodec<GaiaThruster> CODEC = simpleCodec(GaiaThruster::new);

        public GaiaThruster(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
        }

        @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

        // light passes through: the model spans the whole 3x3x3 and is lit from inside it
        @Override protected boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }
        @Override protected int getLightBlock(BlockState s, BlockGetter l, BlockPos p) { return 0; }
        @Override protected float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1f; }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Level level = ctx.getLevel();
            for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(ctx.getClickedPos()))
                if (!level.getBlockState(p).canBeReplaced() || !level.isInWorldBounds(p)) return null;
            return defaultBlockState().setValue(FACING, ctx.getClickedFace());
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level.isClientSide) return;
            placeParts(level, pos);
        }

        /** Fills the 26 positions around the centre with parts (also used by tests and the demo). */
        public static void placeParts(Level level, BlockPos centre) {
            BlockState part = dev.viridianengineering.registry.VEBlocks.GAIA_THRUSTER_PART.getDefaultState();
            for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(centre)) level.setBlock(p, part, Block.UPDATE_ALL);
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            // moved = a Sable ship (dis)assembling: the whole thruster travels, parts and all
            if (!moved && !newState.is(this) && !level.isClientSide)
                for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(pos))
                    if (level.getBlockState(p).is(dev.viridianengineering.registry.VEBlocks.GAIA_THRUSTER_PART.get())) level.removeBlock(p, false);
            IBE.onRemove(state, level, pos, newState);
        }

        /** The mana (or redstone) port face of a thruster part, as for the cannon. */
        public static boolean isPortFace(BlockGetter level, BlockPos pos, @Nullable Direction side, boolean mana) {
            BlockPos m = ThrusterPart.master(level, pos);
            if (m == null || side == null) return false;
            Direction f = level.getBlockState(m).getValue(FACING);
            return pos.equals(dev.viridianengineering.content.cannon.GaiaCannonBlock.portCell(m, f, mana))
                    && side == dev.viridianengineering.content.cannon.GaiaCannonBlock.portFace(f, mana);
        }

        @Override public Class<GaiaThrusterBlockEntity> getBlockEntityClass() { return GaiaThrusterBlockEntity.class; }
        @Override public BlockEntityType<? extends GaiaThrusterBlockEntity> getBlockEntityType() { return VEBlockEntities.GAIA_THRUSTER.get(); }
    }

    /** Invisible filler of the thruster's 3x3x3: forwards goggles, mana (port face) and redstone (port face). */
    public static class ThrusterPart extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
        public ThrusterPart(Properties p) { super(p); }

        @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
            return net.minecraft.world.level.block.RenderShape.INVISIBLE;
        }
        @Override protected boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }
        @Override protected int getLightBlock(BlockState s, BlockGetter l, BlockPos p) { return 0; }
        @Override protected float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1f; }

        @Override
        public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
            return direction != null && (GaiaThruster.isPortFace(level, pos, direction, false)
                    || GaiaThruster.isPortFace(level, pos, direction.getOpposite(), false));
        }

        @Override
        public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
            BlockPos m = master(level, pos);
            return m == null ? pos : m;
        }

        @Nullable
        public static BlockPos master(BlockGetter level, BlockPos pos) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        if (level.getBlockState(p).getBlock() instanceof GaiaThruster) return p;
                    }
            return null;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            super.onRemove(state, level, pos, newState, moved);
            if (level.isClientSide || moved || newState.is(this)) return;
            BlockPos m = master(level, pos);
            if (m != null) level.destroyBlock(m, true);
        }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
            return new ItemStack(dev.viridianengineering.registry.VEBlocks.GAIA_THRUSTER.get());
        }
    }

    // ================================================================== bifrost projector

    /** Bifrost Road Projector: the lens faces away from the face it was placed on (usually down, under a hull). */
    public static class BifrostProjector extends DirectionalBlock implements IBE<BifrostProjectorBlockEntity> {
        public static final MapCodec<BifrostProjector> CODEC = simpleCodec(BifrostProjector::new);

        public BifrostProjector(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN));
        }

        @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getClickedFace());
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (placer != null) withBlockEntityDo(level, pos, be -> be.setOwner(placer.getUUID()));
        }

        @Override public Class<BifrostProjectorBlockEntity> getBlockEntityClass() { return BifrostProjectorBlockEntity.class; }
        @Override public BlockEntityType<? extends BifrostProjectorBlockEntity> getBlockEntityType() { return VEBlockEntities.BIFROST_PROJECTOR.get(); }
    }

    // ================================================================== flügel navigation core

    /**
     * Eye of the Flügel Navigation Core: its front (the gold pointer) faces the way the ship should
     * fly, i.e. the way the player faced when placing it. Right-click with an Eye of the Flügel to
     * set it in; empty hand takes it back out.
     */
    public static class FlugelNavigationCore extends HorizontalDirectionalBlock implements IBE<FlugelNavigationCoreBlockEntity> {
        public static final MapCodec<FlugelNavigationCore> CODEC = simpleCodec(FlugelNavigationCore::new);
        private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 5, 15), Block.box(4, 5, 4, 12, 9, 12),
                Block.box(2, 9, 2, 14, 12, 14));

        public FlugelNavigationCore(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            if (!FlugelNavigationCoreBlockEntity.isEye(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            return onBlockEntityUse(level, pos, be -> {
                if (!be.eye().isEmpty()) return InteractionResult.FAIL;
                be.setEye(stack);
                if (!player.isCreative()) stack.shrink(1);
                player.displayClientMessage(Component.translatable("message.viridian_engineering.flugel.inserted"), true);
                return InteractionResult.SUCCESS;
            }) == InteractionResult.SUCCESS ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return onBlockEntityUse(level, pos, be -> {
                if (be.eye().isEmpty()) return InteractionResult.PASS;
                ItemStack out = be.takeEye();
                if (!player.getInventory().add(out)) player.drop(out, false);
                return InteractionResult.SUCCESS;
            });
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
            if (!moving && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FlugelNavigationCoreBlockEntity be && !be.eye().isEmpty())
                Block.popResource(level, pos, be.takeEye());
            super.onRemove(state, level, pos, newState, moving);
        }

        @Override public Class<FlugelNavigationCoreBlockEntity> getBlockEntityClass() { return FlugelNavigationCoreBlockEntity.class; }
        @Override public BlockEntityType<? extends FlugelNavigationCoreBlockEntity> getBlockEntityType() { return VEBlockEntities.FLUGEL_NAVIGATION_CORE.get(); }
    }
}
