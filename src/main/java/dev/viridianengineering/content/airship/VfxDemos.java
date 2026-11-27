package dev.viridianengineering.content.airship;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.viridianengineering.content.cannon.GaiaCannonBlockEntity;
import dev.viridianengineering.network.VENetwork;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Operator-only previews of the machine effects ({@code /viridian demo cannons | thruster | explosion}).
 * They build ordinary machines (filled with mana and powered) so every effect can be looked at without
 * wiring a whole build; {@code explosion} only plays the battery detonation's visuals for the caller.
 * Results starting with "!" are failures.
 */
final class VfxDemos {

    private VfxDemos() {}

    /** Free 3x3x3 around {@code c}, inside the world and loaded. */
    private static boolean free(ServerLevel level, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(p) || !level.isLoaded(p) || !level.getBlockState(p).canBeReplaced()) return false;
                }
        return true;
    }

    /** Two cannons on the ground 6 blocks ahead, pointing where the player looks: beam (left) and charged shot (right). */
    static String cannons(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Direction fwd = player.getDirection(), left = fwd.getCounterClockWise();
        BlockPos base = player.blockPosition().relative(fwd, 6).above(1);
        BlockPos beam = base.relative(left, 3), shot = base.relative(left, -3);
        if (!free(level, beam) || !free(level, shot)) return "!Not enough room: the cannons need two clear 3x3x3 spaces 6 blocks ahead.";
        place(level, beam, fwd, GaiaCannonBlockEntity.Mode.BEAM);
        place(level, shot, fwd, GaiaCannonBlockEntity.Mode.PROJECTILE);
        return "Two powered Gaia cannons: beam on the left, charged shot on the right (break its redstone block to fire).";
    }

    private static void place(ServerLevel level, BlockPos centre, Direction facing, GaiaCannonBlockEntity.Mode mode) {
        level.setBlock(centre, VEBlocks.GAIA_ARCANE_CANNON.getDefaultState().setValue(DirectionalBlock.FACING, facing), Block.UPDATE_ALL);
        BlockState part = VEBlocks.GAIA_ARCANE_CANNON_PART.getDefaultState();
        for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(centre)) level.setBlock(p, part, Block.UPDATE_ALL);
        if (level.getBlockEntity(centre) instanceof GaiaCannonBlockEntity be) {
            be.setMode(mode);
            be.mana.set(be.mana.getManaCapacity());
            level.setBlock(be.redstoneInput(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * A hovering deck 6 blocks up and 6 ahead with two Gaia thrusters pointing opposite ways off its
     * ends, both at full throttle: the pushes cancel, so it hangs in place while both plumes fire
     * (until their mana runs out, ~8 s).
     */
    static String thrusters(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Direction fwd = player.getDirection(), side = fwd.getClockWise();
        BlockPos origin = player.blockPosition().relative(fwd, 8).above(6);
        // deck runs sideways (along `side`) from -3..3, thrusters off both ends
        List<BlockPos> ship = new ArrayList<>();
        Set<BlockPos> all = new LinkedHashSet<>();
        for (int s = -3; s <= 3; s++)
            for (int f = -2; f <= 2; f++) all.add(origin.relative(side, s).relative(fwd, f));
        BlockPos tA = origin.relative(side, 5), tB = origin.relative(side, -5);
        for (BlockPos p : all) if (!free1(level, p)) return "!Something is in the way: the thruster demo needs clear air ahead and above.";
        if (!free(level, tA) || !free(level, tB)) return "!Something is in the way: the thruster demo needs clear air ahead and above.";

        BlockState stone = Blocks.POLISHED_ANDESITE.defaultBlockState();
        for (BlockPos p : all) { level.setBlock(p, stone, Block.UPDATE_ALL); ship.add(p); }
        BlockState pylon = VEBlocks.MANA_LEVITATION_PYLON.getDefaultState();
        for (int s : new int[]{-1, 1}) {
            BlockPos lo = origin.relative(side, s).above();
            level.setBlock(lo, pylon, Block.UPDATE_ALL);
            level.setBlock(lo.above(), pylon.setValue(AirshipBlocks.LevitationPylon.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            ship.add(lo); ship.add(lo.above());
            if (level.getBlockEntity(lo) instanceof LevitationPylonBlockEntity p) p.mana.set(p.mana.getManaCapacity());
        }
        thruster(level, ship, tA, side);
        thruster(level, ship, tB, side.getOpposite());
        var s = SubLevelAssemblyHelper.assembleBlocks(level, ship.get(0), ship, BoundingBox3i.from(ship));
        return s == null ? "!The thruster demo could not be assembled." : "Thruster demo: both plumes fire for ~8 s while it hovers.";
    }

    private static boolean free1(ServerLevel level, BlockPos p) {
        return !level.isOutsideBuildHeight(p) && level.isLoaded(p) && level.getBlockState(p).canBeReplaced();
    }

    private static void thruster(ServerLevel level, List<BlockPos> ship, BlockPos centre, Direction nozzle) {
        level.setBlock(centre, VEBlocks.GAIA_THRUSTER.getDefaultState().setValue(DirectionalBlock.FACING, nozzle), Block.UPDATE_ALL);
        AirshipBlocks.GaiaThruster.placeParts(level, centre);
        ship.add(centre);
        ship.addAll(dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(centre));
        if (level.getBlockEntity(centre) instanceof GaiaThrusterBlockEntity be) {
            be.mana.set(be.mana.getManaCapacity());
            BlockPos rs = be.redstoneInput();
            level.setBlock(rs, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            if (!ship.contains(rs)) ship.add(rs);
        }
    }

    /**
     * A running 7x7 Mana Reactor 7 blocks ahead (the GameTest layout): lava port west, mana port east,
     * shaft port south with a creative motor at 256 RPM, controller north; the lava tank is filled
     * (4,000 mB = about 40 s of burning). Forms within a second.
     */
    static String reactor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition().relative(player.getDirection(), 7).above();
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    if (!free1(level, c.offset(dx, dy, dz)))
                        return "!Not enough room: the reactor needs a clear 7 x 2 x 9 space 7 blocks ahead.";
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    var slot = dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.slotAt(dx, dy, dz);
                    if (slot == dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.Slot.OUTER
                            || slot == dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.Slot.INNER
                            || slot == dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.Slot.PEDESTAL)
                        level.setBlock(c.offset(dx, dy, dz), VEBlocks.TERRASTEEL_REACTOR_CASING.getDefaultState(), Block.UPDATE_ALL);
                }
        BlockPos lava = c.offset(-3, -1, 0), mana = c.offset(3, 0, 0), shaft = c.offset(0, -1, 3), ctrl = c.offset(0, 0, -3);
        level.setBlock(c, VEBlocks.TERRASTEEL_MANA_REACTOR_CORE.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(lava, VEBlocks.TERRASTEEL_LAVA_PORT.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(mana, VEBlocks.TERRASTEEL_MANA_PORT.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(shaft, VEBlocks.TERRASTEEL_SHAFT_PORT.getDefaultState().setValue(DirectionalBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        level.setBlock(shaft.south(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(ctrl, VEBlocks.TERRASTEEL_MANA_REACTOR_CONTROLLER.getDefaultState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(shaft.south()) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity m)
            m.generatedSpeed.setValue(256);
        if (level.getBlockEntity(ctrl) instanceof dev.viridianengineering.content.reactor.ReactorControllerBlockEntity r)
            r.lava.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 4000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        return "Running Mana Reactor demo (7x7, 256 RPM, ~40 s of lava).";
    }

    /** The battery detonation's visuals only (no damage, nothing broken), on the ground 30 blocks ahead. */
    static String explosion(ServerPlayer player, float radius) {
        ServerLevel level = player.serverLevel();
        BlockPos ahead = player.blockPosition().relative(player.getDirection(), (int) Math.max(30, radius * 1.2f));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead.getX(), ahead.getZ());
        PacketDistributor.sendToPlayer(player, new VENetwork.BatteryDetonated(ahead.getX() + 0.5, y + 1.5, ahead.getZ() + 0.5, radius, 1f));
        return "Detonation preview (radius " + Math.round(radius) + "), visuals only.";
    }
}
