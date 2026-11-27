package dev.viridianengineering.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.airship.AirshipBlocks;
import dev.viridianengineering.content.airship.BifrostProjectorBlockEntity;
import dev.viridianengineering.content.airship.BifrostTrailBlock;
import dev.viridianengineering.content.airship.BifrostTrails;
import dev.viridianengineering.content.airship.FlugelNavigationCoreBlockEntity;
import dev.viridianengineering.content.airship.LevitationPylonBlockEntity;
import dev.viridianengineering.content.autocraft.CraftPattern;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import dev.viridianengineering.content.autocraft.PatternStore;
import dev.viridianengineering.content.battery.BatteryControllerBlockEntity;
import dev.viridianengineering.content.battery.BatteryFailureSavedData;
import dev.viridianengineering.content.cannon.GaiaCannonBlockEntity;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlock;
import dev.viridianengineering.content.drive.ArchivePylonBlock;
import dev.viridianengineering.content.drive.ArchivePylonBlockEntity;
import dev.viridianengineering.content.drive.DriveStorageRecord;
import dev.viridianengineering.content.drive.ItemStackKey;
import dev.viridianengineering.content.drive.TerraDriveItem;
import dev.viridianengineering.content.drive.TerraDriveSavedData;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlock;
import dev.viridianengineering.gametest.PhaseFiveGameTests.Ship;
import dev.viridianengineering.gametest.PhaseFiveGameTests.Site;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 5 verification gate (spec): a full end-to-end flight on the (dedicated) GameTest server.
 * An airship powered by a Terrasteel Mana Battery hovers on Levitation Pylons, leaves a fading Bifrost
 * trail, navigates by Eye of the Flügel, completes an onboard sequenced-assembly autocrafting job in
 * flight, and fires a Gaia Cannon through a Create Redstone Link from the ground.
 * <p>
 * Ship layout (relative to the site; deck of stone at y = 1, x 0..12, z 0..13):
 * <pre>
 *   Corporea line (Phase 3 gate, one block up): terminal (0,2,2) on a motor, Archive Pylon (0,2,3),
 *     corporea pipes x 1..6 z 2, machine ports (1,2,3) (6,2,3), belt x 1..6 z 4 + motor (1,2,5),
 *     three deployers x 2..4 at y 4 + motor (1,4,4).
 *   Battery: 2 layers x 9..11 z 4..6, controller (10,2,4), port (11,2,5).
 *   Mana: port -> pipe (12,2,5) -> Mana Pump (12,2,6) at 256 RPM -> pipes (12,2,7..8), x 1..11 at z 8,
 *     (6,2,9) down to the projector, (11,2,9..12) to the cannon's mana port.
 *   Pylons (2|4|8|10, 2, 9); Bifrost projector (6,1,9) lens down; Flügel core (0,2,8) facing east;
 *   Gaia cannon centre (9,3,12) firing up, Redstone Link receiver on its redstone port at (7,2,12).
 *   On the ground (not part of the ship): the link transmitter at (-3,3,12).
 * </pre>
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseFiveGateTest {

    static final BlockPos T = new BlockPos(0, 2, 2), ARCHIVE = new BlockPos(0, 2, 3);
    static final BlockPos LINE_IN = new BlockPos(1, 2, 3), LINE_OUT = new BlockPos(6, 2, 3);
    static final BlockPos BELT_START = new BlockPos(1, 2, 4), BELT_END = new BlockPos(6, 2, 4), BELT_MOTOR = new BlockPos(1, 2, 5);
    static final BlockPos DEPLOYER_MOTOR = new BlockPos(1, 4, 4);
    static final BlockPos BATTERY = new BlockPos(10, 2, 4), BATTERY_PORT = new BlockPos(11, 2, 5);
    static final BlockPos PUMP = new BlockPos(12, 2, 6), PUMP_COG = new BlockPos(13, 2, 6), PUMP_MOTOR = new BlockPos(13, 2, 5);
    static final List<BlockPos> PYLONS = List.of(new BlockPos(2, 2, 9), new BlockPos(4, 2, 9), new BlockPos(8, 2, 9), new BlockPos(10, 2, 9));
    static final BlockPos PROJECTOR = new BlockPos(6, 1, 9), CORE = new BlockPos(0, 2, 8);
    static final BlockPos CANNON = new BlockPos(9, 3, 12), RECEIVER = new BlockPos(7, 2, 12);
    static final BlockPos TRANSMITTER = new BlockPos(-3, 3, 12), TRANSMITTER_POWER = new BlockPos(-3, 4, 12);
    static final int TRAVEL = 40;

    private static ItemStackKey key(Item item) { return ItemStackKey.of(new ItemStack(item)); }

    /** Mutable test state. */
    private static final class Flight {
        Ship ship;
        dev.viridianengineering.content.autocraft.CraftingJob job;
        long assembledAt = -1, navStartAt = -1, jobAt = -1, fireAt = -1, arrivedAt = -1, jobDoneAt = -1;
        double startY, startX;
        int idlePylonTicks, maxAltitudeError;
        float maxBeam;
        long cannonManaBeforeFire;
        int trailsSeen, fadedSeen;
        long batteryAtAssembly;
        boolean done;
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 6_000, skyAccess = true, batch = "viridian_p5_gate")
    public static void gateFullFlight(GameTestHelper h) {
        Site site = new Site(h);
        ServerLevel level = h.getLevel();
        List<BlockPos> ship = new ArrayList<>();
        build(site, ship);
        Flight f = new Flight();

        // ---- on the ground: power up, fill everything from the battery, record patterns, set the link frequencies
        h.runAfterDelay(2, () -> {
            for (BlockPos m : List.of(T.below(), BELT_MOTOR, DEPLOYER_MOTOR, PUMP_MOTOR))
                site.at(m, CreativeMotorBlockEntity.class).generatedSpeed.setValue(256);
            BatteryControllerBlockEntity b = site.at(BATTERY, BatteryControllerBlockEntity.class);
            b.validate();
            h.assertTrue(b.isFormed() && b.ports().contains(site.abs(BATTERY_PORT)), "Battery not formed with its port");
            b.mana.set(b.mana.getManaCapacity());

            ArchivePylonBlockEntity archive = site.at(ARCHIVE, ArchivePylonBlockEntity.class);
            h.assertTrue(archive.mount(VEStorage.TERRA_DRIVE_64K.asStack()) == ArchivePylonBlockEntity.MountResult.MOUNTED, "Archive refused a drive");
            var drives = TerraDriveSavedData.get(level.getServer());
            DriveStorageRecord store = drives.find(TerraDriveItem.getId(archive.driveIn(0)));
            drives.insert(store, new ItemStack(AllItems.GOLDEN_SHEET.get()), 8, false);
            drives.insert(store, new ItemStack(AllBlocks.COGWHEEL.get()), 64, false);
            drives.insert(store, new ItemStack(AllBlocks.LARGE_COGWHEEL.get()), 64, false);
            drives.insert(store, new ItemStack(Items.IRON_NUGGET), 64, false);
            MachineEndpointKey groundKey = SableCompat.endpointKey(level, site.abs(T), Direction.DOWN);
            for (CraftPattern p : PhaseThreeAutocraftTests.precisionPatterns(level)) PatternStore.get(level.getServer()).add(groundKey, p);

            site.at(CANNON, GaiaCannonBlockEntity.class).setMode(GaiaCannonBlockEntity.Mode.BEAM);
            site.at(CORE, FlugelNavigationCoreBlockEntity.class).setEye(PhaseFiveGameTests.boundEye(level, site.abs(new BlockPos(6 + TRAVEL, -5, 6))));
        });

        h.runAfterDelay(20, () -> {
            for (BlockPos l : List.of(RECEIVER, TRANSMITTER)) {
                LinkBehaviour link = BlockEntityBehaviour.get(level, site.abs(l), LinkBehaviour.TYPE);
                h.assertTrue(link != null, "No link behaviour at " + l + ": " + level.getBlockState(site.abs(l)) + " / "
                        + level.getBlockEntity(site.abs(l)) + " below " + level.getBlockState(site.abs(l).below())
                        + " tickBlocks=" + level.shouldTickBlocksAt(site.abs(l)) + " entityTicking=" + level.isPositionEntityTicking(site.abs(l))
                        + " motorSpeed=" + site.at(T.below(), CreativeMotorBlockEntity.class).getSpeed());
                link.setFrequency(true, new ItemStack(Items.AMETHYST_SHARD));
                link.setFrequency(false, new ItemStack(Items.GOLD_NUGGET));
            }
        });

        // ---- assemble once the pump has filled the pylons, cannon, projector and core
        h.runAfterDelay(240, () -> {
            for (BlockPos p : PYLONS)
                h.assertTrue(site.at(p, LevitationPylonBlockEntity.class).mana.getStoredMana() > 2_000, "Pylon " + p + " not filled from the battery");
            h.assertTrue(site.at(CANNON, GaiaCannonBlockEntity.class).mana.getStoredMana() >= 8_000, "Cannon not filled from the battery: "
                    + site.at(CANNON, GaiaCannonBlockEntity.class).mana.getStoredMana());
            int rolls = BatteryFailureSavedData.get(level).entries().size();
            MachineEndpointKey groundKey = SableCompat.endpointKey(level, site.abs(T), Direction.DOWN);
            f.ship = PhaseFiveGameTests.assemble(site, ship);
            f.assembledAt = level.getGameTime();
            for (BlockPos r : ship)
                h.assertTrue(!level.getBlockState(plot(site, f, r)).isAir(), "Block " + r + " did not make it onto the ship");
            f.batteryAtAssembly = be(site, f, BATTERY, BatteryControllerBlockEntity.class).mana.getStoredMana();
            h.assertTrue(BatteryFailureSavedData.get(level).entries().size() == rolls, "Assembling the ship rolled a battery detonation");
            MachineEndpointKey shipKey = SableCompat.endpointKey(level, plot(site, f, T), Direction.DOWN);
            h.assertTrue(shipKey.sableSubLevelId() != null, "Terminal key not ship-relative after assembly");
            h.assertTrue(PatternStore.get(level.getServer()).patterns(groundKey).isEmpty()
                    && !PatternStore.get(level.getServer()).patterns(shipKey).isEmpty(), "Patterns did not follow the terminal onto the ship");
            Vector3d com = f.ship.com();
            f.startY = com.y;
            f.startX = com.x;
            ViridianEngineering.LOGGER.info("[VE p5 gate] ship {} assembled: mass {}, {} blocks, com {}", f.ship.ship().getUniqueId(),
                    f.ship.ship().getMassTracker().getMass(), ship.size(), com);
        });

        h.onEachTick(() -> {
            if (f.assembledAt < 0 || f.done) return;
            long now = level.getGameTime(), t = now - f.assembledAt;
            Vector3d com = f.ship.com();
            FlugelNavigationCoreBlockEntity core = be(site, f, CORE, FlugelNavigationCoreBlockEntity.class);
            GaiaCannonBlockEntity cannon = be(site, f, CANNON, GaiaCannonBlockEntity.class);
            BifrostProjectorBlockEntity projector = be(site, f, PROJECTOR, BifrostProjectorBlockEntity.class);

            // hover: every pylon lifting (never out of mana) and the altitude held
            if (t > 5) {
                for (BlockPos p : PYLONS)
                    if (be(site, f, p, LevitationPylonBlockEntity.class).mode() == LevitationPylonBlockEntity.Mode.IDLE) f.idlePylonTicks++;
                f.maxAltitudeError = Math.max(f.maxAltitudeError, (int) Math.round(Math.abs(com.y - f.startY) * 10));
            }
            if (f.navStartAt < 0 && core.status() == FlugelNavigationCoreBlockEntity.Status.NAVIGATING) f.navStartAt = now;

            // mid-flight: start the onboard job once we are on our way
            if (f.jobAt < 0 && f.navStartAt >= 0 && com.x - f.startX > 3) {
                var res = CraftingScheduler.request(level, plot(site, f, T), key(AllItems.PRECISION_MECHANISM.get()), 1);
                h.assertTrue(res.job() != null, "Onboard job refused: " + (res.error() == null ? "" : res.error().getString()));
                f.jobAt = now;
                f.job = res.job();
                ViridianEngineering.LOGGER.info("[VE p5 gate] job started {} blocks into the flight", Math.round(com.x - f.startX));
            }
            // mid-flight: fire the cannon from the ground through the Redstone Link (beam, 40 ticks)
            if (f.fireAt < 0 && f.jobAt >= 0 && now - f.jobAt > 20) {
                f.fireAt = now;
                f.cannonManaBeforeFire = cannon.mana.getStoredMana();
                level.setBlock(site.abs(TRANSMITTER_POWER), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            }
            if (f.fireAt >= 0 && now - f.fireAt < 60) f.maxBeam = Math.max(f.maxBeam, cannon.beamLength());
            if (f.fireAt >= 0 && now - f.fireAt == 40) level.setBlock(site.abs(TRANSMITTER_POWER), Blocks.AIR.defaultBlockState(), 3);

            // trail
            int active = BifrostTrails.get(level).activeFor(f.ship.ship().getUniqueId());
            f.trailsSeen = Math.max(f.trailsSeen, projector.placedTotal());
            if (now % 20 == 0 && active > 0) {
                int faded = 0;
                BlockPos lo = BlockPos.containing(f.startX - 10, f.startY - 8, com.z - 10), hi = BlockPos.containing(com.x + 10, f.startY + 2, com.z + 10);
                for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
                    var st = level.getBlockState(p);
                    if (st.is(VEBlocks.BIFROST_TRAIL.get()) && st.getValue(BifrostTrailBlock.AGE) > 0) faded++;
                }
                f.fadedSeen = Math.max(f.fadedSeen, faded);
            }

            if (f.arrivedAt < 0 && core.status() == FlugelNavigationCoreBlockEntity.Status.ARRIVED) {
                f.arrivedAt = now;
                ViridianEngineering.LOGGER.info("[VE p5 gate] arrived after {} ticks, {} blocks east", now - f.navStartAt, Math.round(com.x - f.startX));
            }
            if (f.job != null && f.jobDoneAt < 0 && (now - f.jobAt) % 200 == 0 && now - f.jobAt <= 600) {
                var job = f.job;
                StringBuilder sb = new StringBuilder();
                for (var st : job.steps)
                    sb.append(st.pattern.outputKey().toStack(1).getHoverName().getString()).append(" rem=").append(st.remaining)
                            .append(" fly=").append(st.inFlight).append(" park=").append(st.parked).append(" ep=").append(st.endpoint != null).append("; ");
                var caps = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
                StringBuilder segs = new StringBuilder(), dep = new StringBuilder();
                for (int x = 1; x <= 6; x++) {
                    var seg = level.getCapability(caps, plot(site, f, new BlockPos(x, 2, 4)), Direction.UP);
                    segs.append(seg == null ? "null" : seg.getStackInSlot(0)).append(' ');
                }
                for (int x = 2; x <= 4; x++) {
                    var d = level.getCapability(caps, plot(site, f, new BlockPos(x, 4, 4)), null);
                        dep.append(d == null ? "null" : d.getStackInSlot(d.getSlots() - 1)).append(' ');
                }
                var beltBe = level.getBlockEntity(plot(site, f, BELT_START));
                ViridianEngineering.LOGGER.info("[VE p5 gate] job {} {} | escrow {} | collected {} | missing {} | segments {}| deployers {}| belt {} | endpoints {}",
                        job.status, sb, CraftingScheduler.describe(job.escrow), job.collected, job.missing, segs, dep,
                        beltBe instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity bb ? ("len=" + bb.beltLength + " speed=" + bb.getSpeed()
                                + " controller=" + bb.getController() + " self=" + bb.getBlockPos()) : String.valueOf(beltBe),
                        dev.viridianengineering.content.autocraft.MachineDiscovery.endpoints(level, plot(site, f, T)));
            }
            if (f.jobAt >= 0 && f.jobDoneAt < 0) {
                Map<ItemStackKey, Long> stock = CraftingScheduler.networkStock(level, plot(site, f, T));
                if (stock.getOrDefault(key(AllItems.PRECISION_MECHANISM.get()), 0L) >= 1) {
                    f.jobDoneAt = now;
                    ViridianEngineering.LOGGER.info("[VE p5 gate] onboard job done after {} ticks (ship {} blocks along, altitude off by {})",
                            now - f.jobAt, Math.round(com.x - f.startX), String.format("%.2f", com.y - f.startY));
                }
            }

            if (f.arrivedAt >= 0 && f.jobDoneAt >= 0 && f.fireAt >= 0 && now - f.fireAt > 60) {
                f.done = true;
                h.assertTrue(f.idlePylonTicks == 0, "Pylons ran dry or stopped lifting for " + f.idlePylonTicks + " pylon-ticks");
                h.assertTrue(f.maxAltitudeError <= 30, "Altitude wandered " + f.maxAltitudeError / 10.0 + " blocks while hovering/cruising");
                h.assertTrue(com.x - f.startX > TRAVEL - 6, "Ship only got " + (com.x - f.startX) + " of " + TRAVEL + " blocks east");
                h.assertTrue(f.maxBeam > 0, "The Redstone Link never fired the cannon");
                h.assertTrue(f.trailsSeen >= 5, "Only " + f.trailsSeen + " Bifrost blocks laid over the flight");
                h.assertTrue(f.fadedSeen > 0, "Never saw a fading Bifrost block");
                long battery = be(site, f, BATTERY, BatteryControllerBlockEntity.class).mana.getStoredMana();
                h.assertTrue(battery < f.batteryAtAssembly, "The battery powered nothing in flight");
                h.assertTrue(f.jobDoneAt > f.assembledAt && f.jobAt > f.navStartAt, "The job did not run in flight");
                ViridianEngineering.LOGGER.info("[VE p5 gate] PASS: arrived t+{}, job {} ticks, beam {} blocks, {} trail blocks ({} fading seen), battery used {}",
                        f.arrivedAt - f.assembledAt, f.jobDoneAt - f.jobAt, f.maxBeam, f.trailsSeen, f.fadedSeen, f.batteryAtAssembly - battery);
                // the test removes its ship: close its job too (a job whose ship is gone would wait forever)
                f.job.status = dev.viridianengineering.content.autocraft.CraftingJob.Status.DONE;
                dev.viridianengineering.content.autocraft.CraftingJobSavedData.get(level.getServer()).removeDone();
                PhaseFiveGameTests.remove(h, f.ship);
                level.setBlock(site.abs(TRANSMITTER), Blocks.AIR.defaultBlockState(), 3);
                site.release();
                h.succeed();
            }
        });
    }

    static BlockPos plot(Site site, Flight f, BlockPos rel) { return f.ship.plot(site.abs(rel)); }

    static <T> T be(Site site, Flight f, BlockPos rel, Class<T> type) {
        Object be = site.level.getBlockEntity(plot(site, f, rel));
        site.h.assertTrue(type.isInstance(be), type.getSimpleName() + " missing on the ship at " + rel);
        return type.cast(be);
    }

    // ------------------------------------------------------------------ build

    private static void put(Site site, List<BlockPos> ship, BlockPos rel, BlockState state) {
        site.set(rel, state);
        if (!ship.contains(rel)) ship.add(rel);
    }

    static void build(Site site, List<BlockPos> ship) {
        ServerLevel level = site.level;
        // deck
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 13; z++) put(site, ship, new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());

        // corporea line (the Phase 3 gate's, one block up)
        put(site, ship, T, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState().setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        put(site, ship, T.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        put(site, ship, ARCHIVE, VEStorage.ARCHIVE_PYLON.getDefaultState().setValue(ArchivePylonBlock.FACING, Direction.SOUTH));
        for (int x = 1; x <= 6; x++) put(site, ship, new BlockPos(x, 2, 2), VEBlocks.CORPOREA_PIPE.getDefaultState());
        BlockState port = VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.SOUTH);
        put(site, ship, LINE_IN, port);
        put(site, ship, LINE_OUT, port);
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(ShaftBlock.AXIS, Direction.Axis.Z);
        site.set(BELT_START, shaft);
        site.set(BELT_END, shaft);
        BeltConnectorItem.createBelts(level, site.abs(BELT_START), site.abs(BELT_END));
        for (int x = 1; x <= 6; x++) ship.add(new BlockPos(x, 2, 4));
        put(site, ship, BELT_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        BlockState deployer = AllBlocks.DEPLOYER.getDefaultState().setValue(DeployerBlock.FACING, Direction.DOWN)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true);
        for (int x = 2; x <= 4; x++) put(site, ship, new BlockPos(x, 4, 4), deployer);
        put(site, ship, DEPLOYER_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        // On the ground the deployers may hang in the air (the Phase 3 gate's do); on a ship every block must be
        // connected to the hull, or Sable splits it off as a separate ship. A small pillar ties them to the deck.
        put(site, ship, new BlockPos(1, 4, 5), Blocks.STONE.defaultBlockState());
        put(site, ship, new BlockPos(1, 3, 5), Blocks.STONE.defaultBlockState());

        // battery with its port
        PhaseFourGameTests.buildBattery(level, site.abs(BATTERY), 2);
        for (int y = 2; y <= 3; y++) for (int x = 9; x <= 11; x++) for (int z = 4; z <= 6; z++) ship.add(new BlockPos(x, y, z));
        site.set(BATTERY_PORT, VEBlocks.TERRASTEEL_MANA_BATTERY_PORT.getDefaultState());

        // pumped mana line
        List<BlockPos> pipes = new ArrayList<>(List.of(new BlockPos(12, 2, 5), new BlockPos(12, 2, 7), new BlockPos(12, 2, 8), new BlockPos(6, 2, 9)));
        for (int x = 1; x <= 11; x++) pipes.add(new BlockPos(x, 2, 8));
        for (int z = 9; z <= 12; z++) pipes.add(new BlockPos(11, 2, z));
        put(site, ship, PUMP, VEBlocks.MANA_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING, Direction.SOUTH));
        put(site, ship, PUMP_COG, AllBlocks.COGWHEEL.getDefaultState().setValue(CogWheelBlock.AXIS, Direction.Axis.Z));
        put(site, ship, PUMP_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.SOUTH));

        // pylons, projector, core, cannon
        for (BlockPos p : PYLONS) {
            put(site, ship, p, VEBlocks.MANA_LEVITATION_PYLON.getDefaultState());
            put(site, ship, p.above(), VEBlocks.MANA_LEVITATION_PYLON.getDefaultState().setValue(AirshipBlocks.LevitationPylon.HALF, DoubleBlockHalf.UPPER));
        }
        put(site, ship, PROJECTOR, VEBlocks.BIFROST_PROJECTOR.getDefaultState().setValue(DirectionalBlock.FACING, Direction.DOWN));
        put(site, ship, CORE, VEBlocks.FLUGEL_NAVIGATION_CORE.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockState cannon = VEBlocks.GAIA_ARCANE_CANNON.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP);
        put(site, ship, CANNON, cannon);
        VEBlocks.GAIA_ARCANE_CANNON.get().setPlacedBy(level, site.abs(CANNON), cannon, null, ItemStack.EMPTY);
        for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(CANNON)) if (!ship.contains(p)) ship.add(p);
        put(site, ship, RECEIVER, AllBlocks.REDSTONE_LINK.getDefaultState().setValue(RedstoneLinkBlock.FACING, Direction.UP)
                .setValue(RedstoneLinkBlock.RECEIVER, true));

        for (BlockPos p : pipes) put(site, ship, p, VEBlocks.TERRASTEEL_MANA_PIPE.getDefaultState());
        for (BlockPos p : pipes) {
            BlockPos a = site.abs(p);
            level.setBlock(a, TerrasteelManaPipeBlock.updateConnections(level, a, level.getBlockState(a)), 3);
        }

        // on the ground, not part of the ship: the transmitter on a little pillar
        site.set(TRANSMITTER.below(), Blocks.STONE.defaultBlockState());
        site.set(TRANSMITTER, AllBlocks.REDSTONE_LINK.getDefaultState().setValue(RedstoneLinkBlock.FACING, Direction.UP));
        // Create adds a link's behaviour on its block entity's first NBT read, before its first tick; a block
        // placed with setBlock never gets one (a chunk load or a Sable move would), so give both links one now
        for (BlockPos l : List.of(RECEIVER, TRANSMITTER)) {
            var linkBe = level.getBlockEntity(site.abs(l));
            linkBe.loadWithComponents(linkBe.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        }
        // the deck positions under the terminal motor and projector were replaced above; keep the list unique
        ship.remove(T.below());
        ship.add(0, T.below());
    }
}
