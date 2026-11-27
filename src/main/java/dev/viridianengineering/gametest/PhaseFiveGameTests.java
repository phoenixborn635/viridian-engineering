package dev.viridianengineering.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.airship.AirshipBlocks;
import dev.viridianengineering.content.airship.BifrostProjectorBlockEntity;
import dev.viridianengineering.content.airship.BifrostTrailBlock;
import dev.viridianengineering.content.airship.BifrostTrails;
import dev.viridianengineering.content.airship.FlugelNavigationCoreBlockEntity;
import dev.viridianengineering.content.airship.GaiaThrusterBlockEntity;
import dev.viridianengineering.content.airship.LevitationPylonBlockEntity;
import dev.viridianengineering.content.airship.ShipFlight;
import dev.viridianengineering.content.battery.BatteryControllerBlockEntity;
import dev.viridianengineering.content.battery.BatteryFailureSavedData;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 5: the airship blocks on real assembled Sable ships. Every test builds its ship in the
 * template, assembles it with Sable's own assembler and measures the spec numbers. Each test has its
 * own batch: ships fly, and must not meet another test's structure.
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseFiveGameTests {

    private static final String EMPTY = "empty_12x6x12";

    // ====================================================================== ship helpers

    /**
     * Where a flight test builds its ship: open sky near the world origin, force-loaded.
     * <p>
     * Not inside the test structure: GameTest structures sit millions of blocks out, and Sable's physics
     * stores positions as 32-bit floats, which out there only resolve about a block, so a ship's small
     * per-step moves round away and it never travels horizontally. The structure is also walled in with
     * barriers. A fresh random spot per test keeps runs from meeting each other's ships.
     */
    static final class Site {
        final GameTestHelper h;
        final ServerLevel level;
        final BlockPos base;
        private final List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();

        Site(GameTestHelper h) {
            this.h = h;
            this.level = h.getLevel();
            net.minecraft.util.RandomSource r = level.getRandom();
            base = new BlockPos((r.nextInt(6000) - 3000) & ~15, 200, (r.nextInt(6000) - 3000) & ~15);
            int cx = base.getX() >> 4, cz = base.getZ() >> 4;
            for (int x = cx - 2; x <= cx + 5; x++) for (int z = cz - 2; z <= cz + 3; z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
                forced.add(new net.minecraft.world.level.ChunkPos(x, z));
            }
        }

        BlockPos abs(BlockPos rel) { return base.offset(rel); }

        void set(BlockPos rel, BlockState state) { level.setBlock(abs(rel), state, 3); }

        void set(BlockPos rel, net.minecraft.world.level.block.Block block) { set(rel, block.defaultBlockState()); }

        <T> T at(BlockPos rel, Class<T> type) {
            Object be = level.getBlockEntity(abs(rel));
            h.assertTrue(type.isInstance(be), type.getSimpleName() + " missing at " + rel);
            return type.cast(be);
        }

        void release() {
            for (var c : forced) level.setChunkForced(c.x, c.z, false);
            forced.clear();
        }
    }

    /** An assembled ship and where its blocks went (plot = world + offset). */
    record Ship(ServerSubLevel ship, BlockPos offset) {
        BlockPos plot(BlockPos abs) { return abs.offset(offset); }
        Vector3d com() { return ShipFlight.worldCom(ship); }
    }

    static Ship assemble(GameTestHelper h, List<BlockPos> abs) {
        BlockPos anchor = abs.get(0);
        ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), anchor, abs, BoundingBox3i.from(abs));
        h.assertTrue(ship != null, "Sable refused to assemble the ship");
        return new Ship(ship, ship.getPlot().getCenterBlock().subtract(anchor));
    }

    static Ship assemble(Site site, List<BlockPos> rel) {
        List<BlockPos> abs = new ArrayList<>();
        for (BlockPos p : rel) abs.add(site.abs(p));
        return assemble(site.h, abs);
    }

    static void remove(GameTestHelper h, Ship s) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(h.getLevel());
        if (c != null && !s.ship.isRemoved())
            c.removeSubLevel(s.ship.getPlot().plotPos.x - SubLevelContainer.DEFAULT_ORIGIN,
                    s.ship.getPlot().plotPos.z - SubLevelContainer.DEFAULT_ORIGIN, SubLevelRemovalReason.REMOVED);
    }

    static void finish(Site site, Ship s) {
        remove(site.h, s);
        site.release();
        site.h.succeed();
    }

    /** A 5x5 stone deck at y = 2 (x, z 3..7) with a pylon standing in the middle; returns all block positions. */
    static List<BlockPos> deckWithPylon(Site site, long pylonMana) {
        List<BlockPos> rel = new ArrayList<>();
        for (int x = 3; x <= 7; x++) for (int z = 3; z <= 7; z++) {
            site.set(new BlockPos(x, 2, z), Blocks.STONE);
            rel.add(new BlockPos(x, 2, z));
        }
        BlockState pylon = VEBlocks.MANA_LEVITATION_PYLON.getDefaultState();
        site.set(new BlockPos(5, 3, 5), pylon);
        site.set(new BlockPos(5, 4, 5), pylon.setValue(AirshipBlocks.LevitationPylon.HALF, DoubleBlockHalf.UPPER));
        rel.add(0, new BlockPos(5, 3, 5));
        rel.add(new BlockPos(5, 4, 5));
        site.at(new BlockPos(5, 3, 5), LevitationPylonBlockEntity.class).mana.set(pylonMana);
        return rel;
    }

    /**
     * A 3x3x3 Gaia Thruster (centre + parts, 10,000 mana) with its bell pointing {@code bell}; adds all 27
     * positions to the ship and returns the cell in front of its redstone port (relative), where a
     * redstone block sets full throttle.
     */
    static BlockPos thruster(Site site, List<BlockPos> rel, BlockPos centre, Direction bell) {
        site.set(centre, VEBlocks.GAIA_THRUSTER.getDefaultState().setValue(DirectionalBlock.FACING, bell));
        AirshipBlocks.GaiaThruster.placeParts(site.level, site.abs(centre));
        GaiaThrusterBlockEntity t = site.at(centre, GaiaThrusterBlockEntity.class);
        t.mana.set(10_000);
        rel.add(centre);
        for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(centre)) rel.add(p);
        return t.redstoneInput().subtract(site.base);
    }

    static <T> T be(Site site, Ship s, BlockPos rel, Class<T> type) {
        Object be = site.level.getBlockEntity(s.plot(site.abs(rel)));
        site.h.assertTrue(type.isInstance(be), type.getSimpleName() + " missing on the ship at " + rel);
        return type.cast(be);
    }

    // ====================================================================== levitation pylon

    /** A hovering ship holds its altitude and the pylon draws exactly 15 mana/t. */
    @GameTest(template = EMPTY, timeoutTicks = 300, skyAccess = true, batch = "viridian_p5_hover")
    public static void pylonHoversAtSpecMana(GameTestHelper h) {
        Site site = new Site(h);
        List<BlockPos> rel = deckWithPylon(site, 10_000);
        double startY = site.abs(new BlockPos(5, 2, 5)).getY() + 0.5;
        Ship s = assemble(site, rel);
        LevitationPylonBlockEntity p = be(site, s, new BlockPos(5, 3, 5), LevitationPylonBlockEntity.class);
        AtomicLong m0 = new AtomicLong();
        double[] y0 = new double[1];
        h.runAfterDelay(40, () -> { m0.set(p.mana.getStoredMana()); y0[0] = s.com().y; });
        h.runAfterDelay(140, () -> {
            double y1 = s.com().y;
            long used = m0.get() - p.mana.getStoredMana();
            h.assertTrue(p.mode() == LevitationPylonBlockEntity.Mode.HOVER, "Pylon not hovering: " + p.mode());
            h.assertTrue(used == 100L * VEConfig.LEVITATION_HOVER_MANA_PER_TICK.get(), "Hover used " + used + " mana in 100 ticks, not 1,500");
            h.assertTrue(Math.abs(y1 - y0[0]) < 0.5, "Altitude drifted from " + y0[0] + " to " + y1);
            h.assertTrue(y1 > startY - 1.5, "The ship sank to " + y1 + " (assembled at " + startY + ")");
            finish(site, s);
        });
    }

    /** Full climb signal: the ship rises, the pylon draws 45 mana/t; signal off: back to hover at 15/t. */
    @GameTest(template = EMPTY, timeoutTicks = 300, skyAccess = true, batch = "viridian_p5_climb")
    public static void pylonClimbsAtSpecMana(GameTestHelper h) {
        Site site = new Site(h);
        List<BlockPos> rel = deckWithPylon(site, 10_000);
        Ship s = assemble(site, rel);
        LevitationPylonBlockEntity p = be(site, s, new BlockPos(5, 3, 5), LevitationPylonBlockEntity.class);
        setPylonSignal(site, s, 7);
        AtomicLong m0 = new AtomicLong();
        double[] y0 = new double[1];
        h.runAfterDelay(20, () -> { m0.set(p.mana.getStoredMana()); y0[0] = s.com().y; });
        h.runAfterDelay(80, () -> {
            long used = m0.get() - p.mana.getStoredMana();
            double rise = s.com().y - y0[0];
            h.assertTrue(p.mode() == LevitationPylonBlockEntity.Mode.CLIMB, "Pylon not climbing: " + p.mode());
            h.assertTrue(used == 60L * VEConfig.LEVITATION_ACTIVE_MANA_PER_TICK.get(), "Climb used " + used + " mana in 60 ticks, not 2,700");
            h.assertTrue(rise > 2.0, "Climbed only " + rise + " blocks in 3 s");
            setPylonSignal(site, s, 0);
            m0.set(p.mana.getStoredMana());
        });
        h.runAfterDelay(120, () -> {
            long used = m0.get() - p.mana.getStoredMana();
            h.assertTrue(p.mode() == LevitationPylonBlockEntity.Mode.HOVER, "Pylon not back to hover: " + p.mode());
            h.assertTrue(used == 40L * VEConfig.LEVITATION_HOVER_MANA_PER_TICK.get(), "Hover after climbing used " + used + " in 40 ticks");
            finish(site, s);
        });
    }

    static void setPylonSignal(Site site, Ship ship, int strength) {
        BlockPos wire = ship.plot(site.abs(new BlockPos(6, 3, 5)));
        if (strength == 0) {
            site.level.setBlock(wire, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        site.level.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER, strength), Block.UPDATE_CLIENTS);
        // Vanilla resets power when dust is first placed. Simulate the analog source after placement.
        site.level.setBlock(wire, site.level.getBlockState(wire)
                .setValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER, strength), Block.UPDATE_CLIENTS);
    }

    // ====================================================================== thruster + endpoint keys

    /**
     * Full throttle pushes the ship away from the nozzle at exactly 60 mana/t; the ship crosses a chunk
     * border and every machine key on it stays the same and resolves to the block's plot position.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300, skyAccess = true, batch = "viridian_p5_thrust")
    public static void thrusterPushesAcrossChunksAtSpecMana(GameTestHelper h) {
        Site site = new Site(h);
        List<BlockPos> rel = deckWithPylon(site, 10_000);
        BlockPos thruster = new BlockPos(1, 3, 5);
        BlockPos signal = thruster(site, rel, thruster, Direction.WEST);
        Ship s = assemble(site, rel);
        ServerLevel level = h.getLevel();
        GaiaThrusterBlockEntity t = be(site, s, thruster, GaiaThrusterBlockEntity.class);
        BlockPos tPlot = s.plot(site.abs(thruster));
        MachineEndpointKey key = SableCompat.endpointKey(level, tPlot, Direction.UP);
        h.assertTrue(key.sableSubLevelId() != null, "Thruster key not ship-relative");
        AtomicLong m0 = new AtomicLong();
        AtomicInteger ticks = new AtomicInteger(-1);
        double[] x0 = new double[1];
        int[] chunk0 = new int[1];
        h.runAfterDelay(20, () -> {
            x0[0] = s.com().x;
            chunk0[0] = ((int) Math.floor(x0[0])) >> 4;
            level.setBlock(s.plot(site.abs(signal)), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            m0.set(t.mana.getStoredMana());
            ticks.set(0);
        });
        h.onEachTick(() -> {
            if (ticks.get() < 0) return;
            int n = ticks.incrementAndGet();
            double x = s.com().x;
            boolean crossed = (((int) Math.floor(x)) >> 4) != chunk0[0];
            if (!crossed && n < 120) return;
            ticks.set(Integer.MIN_VALUE / 2);
            level.setBlock(s.plot(site.abs(signal)), Blocks.AIR.defaultBlockState(), 3);
            long used = m0.get() - t.mana.getStoredMana();
            h.assertTrue(crossed, "Ship did not cross a chunk border in 6 s (moved " + (x - x0[0]) + ")");
            h.assertTrue(x - x0[0] > 1.0, "Thrust pushed the ship " + (x - x0[0]) + " blocks east");
            // the signal is read the tick after it's placed, so n - 1 ticks of full throttle
            long expect = (long) VEConfig.GAIA_THRUSTER_MAX_MANA_PER_TICK.get() * (n - 1);
            h.assertTrue(Math.abs(used - expect) <= VEConfig.GAIA_THRUSTER_MAX_MANA_PER_TICK.get(),
                    "Thruster used " + used + " mana over " + n + " ticks, expected ~" + expect);
            MachineEndpointKey after = SableCompat.endpointKey(level, tPlot, Direction.UP);
            h.assertTrue(after.equals(key), "Machine key changed while the ship flew: " + key + " -> " + after);
            h.assertTrue(tPlot.equals(SableCompat.resolve(level, key)), "Key no longer resolves to the thruster");
            finish(site, s);
        });
    }

    // ====================================================================== bifrost

    /**
     * A moving ship leaves Bifrost blocks behind its projector at exactly 50 mana each; they fade, survive
     * a simulated restart of the trail list, and are all gone 200 ticks after they were placed.
     */
    @GameTest(template = EMPTY, timeoutTicks = 500, skyAccess = true, batch = "viridian_p5_bifrost")
    public static void bifrostTrailFollowsShip(GameTestHelper h) {
        Site site = new Site(h);
        List<BlockPos> rel = deckWithPylon(site, 10_000);
        BlockPos thruster = new BlockPos(1, 3, 5), projector = new BlockPos(5, 2, 7);
        BlockPos signal = thruster(site, rel, thruster, Direction.WEST);
        site.set(projector, VEBlocks.BIFROST_PROJECTOR.getDefaultState().setValue(DirectionalBlock.FACING, Direction.DOWN));
        site.at(projector, BifrostProjectorBlockEntity.class).mana.set(5_000);
        Ship s = assemble(site, rel);
        ServerLevel level = h.getLevel();
        BifrostProjectorBlockEntity proj = be(site, s, projector, BifrostProjectorBlockEntity.class);
        UUID id = s.ship.getUniqueId();
        h.runAfterDelay(20, () -> level.setBlock(s.plot(site.abs(signal)), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
        h.runAfterDelay(28, () -> level.setBlock(s.plot(site.abs(signal)), Blocks.AIR.defaultBlockState(), 3));
        int[] placed = new int[1];
        h.runAfterDelay(100, () -> {
            placed[0] = proj.placedTotal();
            BifrostTrails trails = BifrostTrails.get(level);
            h.assertTrue(placed[0] >= 2, "Projector left only " + placed[0] + " trail blocks");
            h.assertTrue(5_000 - proj.mana.getStoredMana() == 50L * placed[0],
                    "Trail cost " + (5_000 - proj.mana.getStoredMana()) + " mana for " + placed[0] + " blocks (spec: 50 each)");
            h.assertTrue(trails.activeFor(id) == placed[0], "Trail list holds " + trails.activeFor(id) + " of " + placed[0]);
            h.assertTrue(trails.activeFor(id) <= VEConfig.BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP.get(), "Over the per-ship cap");
            int found = 0, faded = 0;
            for (BlockPos p : BlockPos.betweenClosed(site.abs(new BlockPos(-2, -1, 5)), site.abs(new BlockPos(44, 4, 9)))) {
                BlockState st = level.getBlockState(p);
                if (st.is(VEBlocks.BIFROST_TRAIL.get())) {
                    found++;
                    if (st.getValue(BifrostTrailBlock.AGE) > 0) faded++;
                    h.assertTrue(level.getBlockEntity(p) == null, "Trail block has a block entity");
                    h.assertTrue(!st.isRandomlyTicking() && st.getLightEmission(level, p) == 0 && st.getLightBlock(level, p) == 0,
                            "Trail block ticks or changes light");
                }
            }
            h.assertTrue(found == placed[0], "Found " + found + " trail blocks in the world, projector placed " + placed[0]);
            h.assertTrue(faded > 0, "No trail block has started to fade after 3+ s");
            // simulated restart of the trail list
            CompoundTag saved = trails.save(new CompoundTag(), level.registryAccess());
            level.getDataStorage().set(BifrostTrails.ID, BifrostTrails.load(saved, level.registryAccess()));
            h.assertTrue(BifrostTrails.get(level).activeFor(id) == placed[0], "Trail list lost entries over a restart");
            // a redstone signal switches the projector off, so nothing new is laid while we wait for expiry
            level.setBlock(s.plot(site.abs(projector.east())), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        });
        h.runAfterDelay(102, () -> placed[0] = proj.placedTotal());
        h.runAfterDelay(310, () -> {
            h.assertTrue(BifrostTrails.get(level).activeFor(id) == 0, "Trail blocks outlived 200 ticks: " + BifrostTrails.get(level).activeFor(id));
            for (BlockPos p : BlockPos.betweenClosed(site.abs(new BlockPos(-2, -1, 5)), site.abs(new BlockPos(44, 4, 9))))
                h.assertTrue(!level.getBlockState(p).is(VEBlocks.BIFROST_TRAIL.get()), "Trail block left at " + p);
            finish(site, s);
        });
    }

    /** At most N blocks per ship: the oldest is retired early. Claim/spawn protection is respected by tryPlace. */
    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "viridian_p5_bifrost_cap")
    public static void bifrostCapPerShip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int old = VEConfig.BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP.get();
        VEConfig.BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP.set(3);
        try {
            BifrostTrails trails = BifrostTrails.get(level);
            UUID ship = UUID.randomUUID();
            for (int i = 0; i < 5; i++)
                h.assertTrue(trails.tryPlace(level, h.absolutePos(new BlockPos(2 + i, 2, 2)), ship, null), "Placement " + i + " refused");
            h.assertTrue(trails.activeFor(ship) == 3, "Cap of 3 not kept: " + trails.activeFor(ship));
            h.assertTrue(h.getBlockState(new BlockPos(2, 2, 2)).isAir() && h.getBlockState(new BlockPos(3, 2, 2)).isAir(),
                    "Oldest blocks not retired");
            h.assertTrue(h.getBlockState(new BlockPos(6, 2, 2)).is(VEBlocks.BIFROST_TRAIL.get()), "Newest block missing");
            h.setBlock(new BlockPos(8, 2, 2), Blocks.STONE);
            h.assertTrue(!trails.tryPlace(level, h.absolutePos(new BlockPos(8, 2, 2)), ship, null), "Trail replaced a solid block");
        } finally {
            VEConfig.BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP.set(old);
        }
        h.succeed();
    }

    // ====================================================================== flügel autopilot

    /** With a bound Eye the core flies the ship to the target and stops; exactly 10 mana per navigating tick. */
    @GameTest(template = EMPTY, timeoutTicks = 900, skyAccess = true, batch = "viridian_p5_flugel")
    public static void flugelAutopilotArrives(GameTestHelper h) {
        Site site = new Site(h);
        List<BlockPos> rel = deckWithPylon(site, 10_000);
        BlockPos core = new BlockPos(5, 3, 7);
        site.set(core, VEBlocks.FLUGEL_NAVIGATION_CORE.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        site.at(core, FlugelNavigationCoreBlockEntity.class).mana.set(10_000);
        rel.add(core);
        Ship s = assemble(site, rel);
        ServerLevel level = h.getLevel();
        FlugelNavigationCoreBlockEntity c = be(site, s, core, FlugelNavigationCoreBlockEntity.class);
        LevitationPylonBlockEntity p = be(site, s, new BlockPos(5, 3, 5), LevitationPylonBlockEntity.class);
        Vector3d start = s.com();
        BlockPos target = BlockPos.containing(start.x + 14, start.y - VEConfig.FLUGEL_ARRIVAL_HEIGHT.get(), start.z + 3);
        c.setEye(boundEye(level, target));
        AtomicInteger nav = new AtomicInteger();
        AtomicLong m0 = new AtomicLong(c.mana.getStoredMana());
        boolean[] sawAutopilot = new boolean[1];
        h.onEachTick(() -> {
            if (c.status() == FlugelNavigationCoreBlockEntity.Status.NAVIGATING) nav.incrementAndGet();
            if (p.mode() == LevitationPylonBlockEntity.Mode.AUTOPILOT) sawAutopilot[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(c.status() == FlugelNavigationCoreBlockEntity.Status.ARRIVED, "Status " + c.status() + ", " + Math.round(c.distance()) + " blocks to go");
            Vector3d at = s.com();
            double dh = Math.hypot(at.x - (target.getX() + 0.5), at.z - (target.getZ() + 0.5));
            h.assertTrue(dh <= VEConfig.FLUGEL_ARRIVAL_RADIUS.get() + 0.01, "Arrived " + dh + " blocks off target");
            h.assertTrue(sawAutopilot[0], "Pylons never followed the autopilot");
            long used = m0.get() - c.mana.getStoredMana();
            h.assertTrue(used == 10L * nav.get(), "Core used " + used + " mana over " + nav.get() + " navigating ticks (spec: 10/t)");
            remove(h, s);
            site.release();
        });
    }

    static ItemStack boundEye(ServerLevel level, BlockPos target) {
        ItemStack eye = new ItemStack(velrondevs.botania.registry.BotaniaItems.flugelEye);
        velrondevs.botania.common.helper.ItemNBTHelper.set(eye, "target_" + level.dimension().location(),
                BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, target).getOrThrow());
        return eye;
    }

    // ====================================================================== battery aboard

    /**
     * Assembling a full (100%: always detonates when broken) battery into a ship and disassembling it
     * again never rolls a detonation; the battery keeps its mana and re-forms both times.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200, skyAccess = true, batch = "viridian_p5_battery")
    public static void batteryAssemblyNeverRolls(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos controller = new BlockPos(5, 2, 4);
        BatteryControllerBlockEntity b = PhaseFourGameTests.buildBattery(level, h.absolutePos(controller), 2);
        h.assertTrue(b.isFormed(), "Battery not formed before assembly");
        b.mana.set(b.mana.getManaCapacity());
        long full = b.mana.getStoredMana();
        int before = BatteryFailureSavedData.get(level).entries().size();
        List<BlockPos> rel = new ArrayList<>();
        rel.add(controller);
        for (int y = 2; y <= 3; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            rel.add(controller.south().offset(dx, y - 2, dz));
        Ship s = assemble(h, rel.stream().map(h::absolutePos).toList());
        BlockPos cPlot = s.plot(h.absolutePos(controller));
        h.runAfterDelay(40, () -> {
            h.assertTrue(BatteryFailureSavedData.get(level).entries().size() == before, "Assembly rolled a detonation");
            BatteryControllerBlockEntity on = (BatteryControllerBlockEntity) level.getBlockEntity(cPlot);
            h.assertTrue(on != null && on.isFormed(), "Battery not formed on the ship");
            h.assertTrue(on.mana.getStoredMana() == full, "Battery lost mana on assembly: " + on.mana.getStoredMana());
            // disassemble back where it came from (Simulated's own disassembler)
            dev.simulated_team.simulated.util.SimAssemblyHelper.disassembleSubLevel(level, s.ship, cPlot, h.absolutePos(controller),
                    Rotation.NONE, false);
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(BatteryFailureSavedData.get(level).entries().size() == before, "Disassembly rolled a detonation");
            BatteryControllerBlockEntity back = (BatteryControllerBlockEntity) level.getBlockEntity(h.absolutePos(controller));
            h.assertTrue(back != null && back.isFormed(), "Battery not formed after disassembly");
            h.assertTrue(back.mana.getStoredMana() == full, "Battery lost mana on disassembly: " + back.mana.getStoredMana());
            back.mana.set(0); // so the template cleanup can't ever roll
            remove(h, s);
            h.succeed();
        });
    }

    // ====================================================================== reactor aboard

    /**
     * Phase 5b: a formed, running Mana Reactor assembled into a ship re-forms aboard and keeps the spec rate
     * (125 mana/t at 256 RPM) while the ship hovers.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "viridian_p5_reactor")
    public static void reactorRunsAboardShip(GameTestHelper h) {
        Site site = new Site(h);
        ServerLevel level = h.getLevel();
        BlockPos core = new BlockPos(6, 3, 6), ctrl = core.offset(0, 0, -3), lava = core.offset(-3, -1, 0),
                mana = core.offset(3, 0, 0), shaft = core.offset(0, -1, 3), motor = shaft.south();
        List<BlockPos> rel = new ArrayList<>();
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) { site.set(new BlockPos(x, 1, z), Blocks.STONE); rel.add(new BlockPos(x, 1, z)); }
        for (int dy = -1; dy <= 0; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            var slot = dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.slotAt(dx, dy, dz);
            if (slot == dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.Slot.OUTER
                    || slot == dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.Slot.INNER
                    || slot == dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.Slot.PEDESTAL) {
                site.set(core.offset(dx, dy, dz), VEBlocks.TERRASTEEL_REACTOR_CASING.getDefaultState());
                rel.add(core.offset(dx, dy, dz));
            }
        }
        site.set(core, VEBlocks.TERRASTEEL_MANA_REACTOR_CORE.getDefaultState());
        site.set(lava, VEBlocks.TERRASTEEL_LAVA_PORT.getDefaultState());
        site.set(mana, VEBlocks.TERRASTEEL_MANA_PORT.getDefaultState());
        site.set(shaft, VEBlocks.TERRASTEEL_SHAFT_PORT.getDefaultState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        site.set(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.NORTH));
        site.set(ctrl, VEBlocks.TERRASTEEL_MANA_REACTOR_CONTROLLER.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        for (BlockPos p : List.of(core, lava, mana, shaft, motor, ctrl)) if (!rel.contains(p)) rel.add(p);
        BlockState pylon = VEBlocks.MANA_LEVITATION_PYLON.getDefaultState();
        for (BlockPos p : List.of(new BlockPos(1, 2, 1), new BlockPos(11, 2, 1), new BlockPos(1, 2, 11), new BlockPos(11, 2, 11),
                new BlockPos(6, 2, 1), new BlockPos(1, 2, 6))) {
            site.set(p, pylon);
            site.set(p.above(), pylon.setValue(AirshipBlocks.LevitationPylon.HALF, DoubleBlockHalf.UPPER));
            site.at(p, LevitationPylonBlockEntity.class).mana.set(10_000);
            rel.add(p);
            rel.add(p.above());
        }
        h.runAfterDelay(2, () -> {
            site.at(motor, com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity.class).generatedSpeed.setValue(256);
            var fluid = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, site.abs(lava), Direction.WEST);
            h.assertTrue(fluid != null, "No lava port capability");
        });
        h.runAfterDelay(40, () -> {
            var r = site.at(ctrl, dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.class);
            h.assertTrue(r.isFormed(), "Reactor did not form on the ground");
            var fluid = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, site.abs(lava), Direction.WEST);
            fluid.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 4_000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        Ship[] ship = new Ship[1];
        long[] start = new long[2];
        h.runAfterDelay(60, () -> {
            h.assertTrue(site.at(ctrl, dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.class).isRunning(),
                    "Reactor not running on the ground");
            ship[0] = assemble(site, rel);
        });
        h.runAfterDelay(110, () -> {
            var r = be(site, ship[0], ctrl, dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.class);
            h.assertTrue(r.isFormed(), "Reactor did not re-form aboard the ship");
            h.assertTrue(r.isRunning(), "Reactor not running aboard the ship");
            start[0] = r.mana.getStoredMana();
            start[1] = level.getGameTime();
        });
        h.runAfterDelay(210, () -> {
            var r = be(site, ship[0], ctrl, dev.viridianengineering.content.reactor.ReactorControllerBlockEntity.class);
            long ticks = level.getGameTime() - start[1], made = r.mana.getStoredMana() - start[0];
            h.assertTrue(Math.abs(made - 125 * ticks) <= 125, "Aboard: made " + made + " mana in " + ticks + " ticks, expected " + 125 * ticks);
            double drop = ship[0].com().y - (site.base.getY() + 1.5);
            h.assertTrue(drop > -2, "The reactor ship sank " + drop + " blocks");
            finish(site, ship[0]);
        });
    }
}
