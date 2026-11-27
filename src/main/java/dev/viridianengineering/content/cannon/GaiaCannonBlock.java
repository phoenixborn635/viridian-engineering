package dev.viridianengineering.content.cannon;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.registry.VEBlockEntities;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Gaia Arcane Cannon: a 3x3x3 machine placed as one item. This block is the centre (it holds the
 * block entity and draws the whole cannon model); the other 26 positions are invisible
 * {@link Part}s that forward clicks, mana and redstone to it. Breaking any part breaks the cannon.
 */
public class GaiaCannonBlock extends DirectionalBlock implements IBE<GaiaCannonBlockEntity> {

    public static final MapCodec<GaiaCannonBlock> CODEC = simpleCodec(GaiaCannonBlock::new);

    public GaiaCannonBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    // light passes through: the cannon model spans the whole 3x3x3 and is lit from inside it
    @Override protected boolean propagatesSkylightDown(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return true; }
    @Override protected int getLightBlock(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return 0; }
    @Override protected float getShadeBrightness(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return 1f; }


    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    // ------------------------------------------------------------------ ports
    // The model is authored floor-mounted (FACING up); the blockstate turns it with x then y quarter
    // turns. The ports sit in the foot layer: mana at local (+1, -1, 0) facing +x, redstone at
    // (-1, -1, 0) facing -x. Only those two faces take pipes / signals.

    private static int[] turns(Direction facing) {
        return switch (facing) {
            case UP -> new int[]{0, 0};
            case DOWN -> new int[]{2, 0};
            case NORTH -> new int[]{1, 0};
            case SOUTH -> new int[]{1, 2};
            case WEST -> new int[]{1, 3};
            case EAST -> new int[]{1, 1};
        };
    }

    /** Turns a model-local vector the way the blockstate turns the model for {@code facing}. */
    static int[] toWorld(int x, int y, int z, Direction facing) {
        int[] t = turns(facing);
        for (int i = 0; i < t[0]; i++) { int ny = z, nz = -y; y = ny; z = nz; }  // x quarter turn: up -> north
        for (int i = 0; i < t[1]; i++) { int nx = -z, nz = x; x = nx; z = nz; }  // y quarter turn: north -> east
        return new int[]{x, y, z};
    }

    /** The part holding the mana port (mana = true) or the redstone port. */
    public static BlockPos portCell(BlockPos centre, Direction facing, boolean mana) {
        int[] v = toWorld(mana ? 1 : -1, -1, 0, facing);
        return centre.offset(v[0], v[1], v[2]);
    }

    /** The outward face of that port. */
    public static Direction portFace(Direction facing, boolean mana) {
        int[] v = toWorld(mana ? 1 : -1, 0, 0, facing);
        return Direction.fromDelta(v[0], v[1], v[2]);
    }

    /** True if {@code side} of the part at {@code pos} is the cannon's mana (or redstone) port. */
    public static boolean isPortFace(net.minecraft.world.level.BlockGetter level, BlockPos pos, @org.jetbrains.annotations.Nullable Direction side, boolean mana) {
        BlockPos m = Part.master(level, pos);
        if (m == null || side == null) return false;
        Direction f = level.getBlockState(m).getValue(FACING);
        return pos.equals(portCell(m, f, mana)) && side == portFace(f, mana);
    }

    /** The 26 positions around the centre. */
    public static List<BlockPos> parts(BlockPos center) {
        List<BlockPos> out = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0) out.add(center.offset(dx, dy, dz));
        return out;
    }

    /**
     * Mounted on the clicked face and fires away from it: floor-mounted fires up, wall-mounted
     * sideways, ceiling-mounted down. Refused when the 3x3x3 is not free.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        for (BlockPos p : parts(ctx.getClickedPos()))
            if (!level.getBlockState(p).canBeReplaced() || !level.isInWorldBounds(p)) return null;
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BlockState part = VEBlocks.GAIA_ARCANE_CANNON_PART.getDefaultState();
        for (BlockPos p : parts(pos)) level.setBlock(p, part, Block.UPDATE_ALL);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // moved = a Sable ship (dis)assembling: the whole cannon travels, parts and all
        if (!moved && !newState.is(this) && !level.isClientSide)
            for (BlockPos p : parts(pos))
                if (level.getBlockState(p).is(VEBlocks.GAIA_ARCANE_CANNON_PART.get())) level.removeBlock(p, false);
        IBE.onRemove(state, level, pos, newState);
    }

    static InteractionResult toggle(Level level, BlockPos master, Player player) {
        if (!level.isClientSide && level.getBlockEntity(master) instanceof GaiaCannonBlockEntity be) {
            be.toggleMode();
            player.displayClientMessage(Component.translatable("viridian_engineering.goggles.cannon.mode."
                    + be.mode().name().toLowerCase()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return toggle(level, pos, player);
    }

    @Override public Class<GaiaCannonBlockEntity> getBlockEntityClass() { return GaiaCannonBlockEntity.class; }
    @Override public BlockEntityType<? extends GaiaCannonBlockEntity> getBlockEntityType() { return VEBlockEntities.GAIA_ARCANE_CANNON.get(); }

    // ------------------------------------------------------------------ parts

    /** Invisible filler of the 3x3x3. */
    public static class Part extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
        public Part(Properties p) { super(p); }

        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

        // light passes through: the cannon model spans the whole 3x3x3 and is lit from inside it
        @Override protected boolean propagatesSkylightDown(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return true; }
        @Override protected int getLightBlock(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return 0; }
        @Override protected float getShadeBrightness(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p) { return 1f; }


        /** Redstone dust only joins the redstone port's face. */
        @Override
        public boolean canConnectRedstone(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                          @org.jetbrains.annotations.Nullable Direction direction) {
            return direction != null && (isPortFace(level, pos, direction, false) || isPortFace(level, pos, direction.getOpposite(), false));
        }

        /** Goggles on any part show the cannon's info (held by the centre block). */
        @Override
        public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
            BlockPos m = master(level, pos);
            return m == null ? pos : m;
        }

        /** The cannon centre this part belongs to, or null. */
        public static BlockPos master(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        if (level.getBlockState(p).getBlock() instanceof GaiaCannonBlock) return p;
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
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            BlockPos m = master(level, pos);
            return m == null ? InteractionResult.PASS : toggle(level, m, player);
        }

        @Override
        public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
            return new ItemStack(VEBlocks.GAIA_ARCANE_CANNON.get());
        }
    }

    /** Places the cannon's centre one block out from the clicked face, so its foot sits against that face. */
    public static class Item extends BlockItem {
        public Item(Block block, Properties p) { super(block, p); }

        @Override
        public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
            Direction face = ctx.getClickedFace();
            return BlockPlaceContext.at(ctx, ctx.getClickedPos().relative(face), face);
        }
    }
}
