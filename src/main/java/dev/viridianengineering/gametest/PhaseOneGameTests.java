package dev.viridianengineering.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.elementium.RequestInterfaceBlockEntity;
import dev.viridianengineering.content.infusion.IAlfheimPress;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlock;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlockEntity;
import dev.viridianengineering.registry.ModCapabilities;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.registry.VEFluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.mana.ManaPool;

import java.util.List;

/**
 * Phase 1 verification gate, as automated GameTests.
 * Run with {@code gradlew runGameTestServer}; results print to the console and the
 * server exits non-zero if any test fails.
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseOneGameTests {

    private static final String EMPTY = "empty_12x6x12";
    private static final int POOL_START = 500_000;

    private PhaseOneGameTests() {}

    // ------------------------------------------------------------------ helpers

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private static void connectManaPipes(GameTestHelper h, List<BlockPos> pipes) {
        ServerLevel level = h.getLevel();
        for (BlockPos rel : pipes) {
            BlockPos abs = h.absolutePos(rel);
            level.setBlock(abs, TerrasteelManaPipeBlock.updateConnections(level, abs, level.getBlockState(abs)), 3);
        }
    }

    private static void connectFluidPipes(GameTestHelper h, List<BlockPos> pipes) {
        ServerLevel level = h.getLevel();
        for (BlockPos rel : pipes) {
            BlockPos abs = h.absolutePos(rel);
            BlockState state = level.getBlockState(abs);
            if (state.getBlock() instanceof FluidPipeBlock pipe)
                level.setBlock(abs, pipe.updateBlockState(state, Direction.EAST, null, level, abs), 3);
        }
    }

    /** Motor -> cogwheel -> pump (pump facing east), spinning at {@code rpm}. */
    private static void placePump(GameTestHelper h, BlockPos pumpRel, int rpm) {
        h.setBlock(pumpRel, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        BlockPos cog = pumpRel.north();
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(CogWheelBlock.AXIS, Direction.Axis.X));
        BlockPos motor = cog.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        h.runAfterDelay(2, () -> {
            if (h.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
                m.generatedSpeed.setValue(rpm);
        });
    }

    private static ManaPool pool(GameTestHelper h, BlockPos rel) {
        if (h.getBlockEntity(rel) instanceof ManaPool p) return p;
        throw new IllegalStateException("no mana pool at " + rel);
    }

    private static long pipeMana(GameTestHelper h, List<BlockPos> pipes) {
        long sum = 0;
        for (BlockPos p : pipes)
            if (h.getBlockEntity(p) instanceof TerrasteelManaPipeBlockEntity be) sum += be.getStoredMana();
        return sum;
    }

    // ------------------------------------------------------------------ 3.2 mana pipes

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void passiveManaJunctionFeedsBothBranches(GameTestHelper h) {
        BlockPos cross = new BlockPos(5, 1, 5);
        BlockPos source = cross.west(), secondSource = cross.east();
        h.setBlock(source, block("botania:mana_pool"));
        h.setBlock(secondSource, block("botania:mana_pool"));
        placeManaJunction(h, cross, cross.north(), cross.south());
        h.runAfterDelay(1, () -> pool(h, source).receiveMana(POOL_START));
        h.runAfterDelay(30, () -> pool(h, secondSource).receiveMana(POOL_START));
        h.runAtTickTime(70, () -> {
            long north = receivedMana(h, cross.north());
            long south = receivedMana(h, cross.south());
            h.assertTrue(north > 500 && south > 500, "A passive branch was starved: " + north + ", " + south);
            long total = north + south + pipeMana(h, List.of(cross))
                    + pool(h, source).getCurrentMana() + pool(h, secondSource).getCurrentMana();
            h.assertTrue(total == 2L * POOL_START, "Mana was lost or duplicated at the junction: " + total);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void manaJunctionFeedsBothBranchesUnderSuction(GameTestHelper h) {
        BlockPos cross = new BlockPos(5, 1, 5);
        BlockPos intake = cross.east(), pump = intake.east(), outlet = pump.east();
        placePump(h, pump, 128);
        h.setBlock(outlet, block("botania:mana_pool"));
        h.setBlock(cross.west(), block("botania:creative_pool"));
        h.setBlock(intake, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        placeManaJunction(h, cross, cross.north(), cross.south());
        connectManaPipes(h, List.of(intake));
        h.runAfterDelay(1, () -> pool(h, outlet).receiveMana(pool(h, outlet).getMaxMana()));
        h.runAtTickTime(65, () -> {
            h.assertTrue(((TerrasteelManaPipeBlockEntity) h.getBlockEntity(cross)).isPressurized(),
                    "The junction did not receive suction pressure");
            h.assertTrue(receivedMana(h, cross.north()) > 1_000 && receivedMana(h, cross.south()) > 1_000,
                    "A full pump outlet blocked a separate input branch");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void manaJunctionAcceptsSourcesOnPumpDischarge(GameTestHelper h) {
        BlockPos cross = new BlockPos(5, 1, 5);
        BlockPos discharge = cross.west(), pump = discharge.west();
        placePump(h, pump, 128); // The pump supplies pressure, while the two sources supply mana.
        h.setBlock(discharge, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        h.setBlock(cross.north(), block("botania:creative_pool"));
        h.setBlock(cross.south(), block("botania:creative_pool"));
        placeManaJunction(h, cross, cross.east());
        connectManaPipes(h, List.of(discharge));
        h.runAtTickTime(65, () -> {
            h.assertTrue(((TerrasteelManaPipeBlockEntity) h.getBlockEntity(cross)).isPressurized(),
                    "The junction did not receive discharge pressure");
            h.assertTrue(receivedMana(h, cross.east()) > 1_000, "Pump pressure blocked the other sources");
            h.succeed();
        });
    }

    private static void placeManaJunction(GameTestHelper h, BlockPos cross, BlockPos... receivers) {
        for (BlockPos receiver : receivers) h.setBlock(receiver, VEBlocks.ELEMENTIUM_REQUEST_INTERFACE.get());
        h.setBlock(cross, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        connectManaPipes(h, List.of(cross));
    }

    private static long receivedMana(GameTestHelper h, BlockPos pos) {
        return ((RequestInterfaceBlockEntity) h.getBlockEntity(pos)).mana.getStoredMana();
    }

    /** Passive flow: pool -> 3 pipes -> pool, 40 mana/tick per link, mana conserved exactly. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void manaPipePassiveFlowConserves(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(5, 1, 1);
        List<BlockPos> pipes = List.of(new BlockPos(2, 1, 1), new BlockPos(3, 1, 1), new BlockPos(4, 1, 1));
        h.setBlock(a, block("botania:mana_pool"));
        h.setBlock(b, block("botania:mana_pool"));
        for (BlockPos p : pipes) h.setBlock(p, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        connectManaPipes(h, pipes);
        h.runAfterDelay(1, () -> pool(h, a).receiveMana(POOL_START));

        h.runAtTickTime(121, () -> {
            long ma = pool(h, a).getCurrentMana(), mb = pool(h, b).getCurrentMana(), mp = pipeMana(h, pipes);
            ViridianEngineering.LOGGER.info("[VE Phase1] passive: poolA={} poolB={} pipes={} total={}", ma, mb, mp, ma + mb + mp);
            h.assertTrue(ma + mb + mp == POOL_START, "mana not conserved: " + (ma + mb + mp));
            h.assertTrue(mb > 1_000, "pool B received too little: " + mb);
            h.assertTrue(mb <= 40L * 120, "passive flow exceeded 40/tick: " + mb);
            h.succeed();
        });
    }

    /** Pumped flow: 256 RPM pump in the line raises throughput toward 40 + 1.25*256 = 360 mana/tick. */
    @GameTest(template = EMPTY, timeoutTicks = 260)
    public static void manaPipePumpedFlowIsFaster(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 3), b = new BlockPos(5, 1, 3);
        List<BlockPos> pipes = List.of(new BlockPos(2, 1, 3), new BlockPos(4, 1, 3));
        h.setBlock(a, block("botania:mana_pool"));
        h.setBlock(b, block("botania:mana_pool"));
        placePump(h, new BlockPos(3, 1, 3), 256);
        for (BlockPos p : pipes) h.setBlock(p, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        connectManaPipes(h, pipes);
        h.runAfterDelay(1, () -> pool(h, a).receiveMana(POOL_START));

        h.runAtTickTime(221, () -> {
            long ma = pool(h, a).getCurrentMana(), mb = pool(h, b).getCurrentMana(), mp = pipeMana(h, pipes);
            ViridianEngineering.LOGGER.info("[VE Phase1] pumped: poolA={} poolB={} pipes={} total={}", ma, mb, mp, ma + mb + mp);
            h.assertTrue(ma + mb + mp == POOL_START, "mana not conserved: " + (ma + mb + mp));
            // 200 ticks at the passive rate would be at most 8,000.
            h.assertTrue(mb > 30_000, "pumped flow too slow: " + mb);
            h.succeed();
        });
    }

    /** Capability isolation: mana pipes expose no fluid handler and ignore fluid tanks; fluid pipes carry no mana. */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void manaAndFluidNetworksAreIsolated(GameTestHelper h) {
        BlockPos tank = new BlockPos(1, 1, 5), manaPipe = new BlockPos(2, 1, 5), fluidPipe = new BlockPos(4, 1, 5);
        h.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState());
        h.setBlock(manaPipe, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        h.setBlock(fluidPipe, VEBlocks.MANASTEEL_FLUID_PIPE.get());
        connectManaPipes(h, List.of(manaPipe));

        h.runAfterDelay(2, () -> {
            ServerLevel level = h.getLevel();
            BlockPos mp = h.absolutePos(manaPipe), fp = h.absolutePos(fluidPipe);
            h.assertTrue(level.getBlockEntity(mp) instanceof dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlockEntity pipe
                    && !pipe.isLinked(Direction.WEST), "mana pipe connected to a fluid tank");
            for (Direction d : Direction.values()) {
                h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, mp, d) == null,
                        "mana pipe exposes a fluid handler on " + d);
                h.assertTrue(level.getCapability(BotaniaCapabilities.MANA_RECEIVER, fp, d) == null,
                        "manasteel fluid pipe accepts mana on " + d);
                h.assertTrue(level.getCapability(ModCapabilities.MANA_PIPE_NODE, fp, d) == null,
                        "manasteel fluid pipe is a mana node on " + d);
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ 3.1 manasteel fluid pipes

    /** Same pump RPM (64), copper vs manasteel line, measured over ticks 50-90: manasteel moves about twice the water. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void manasteelPipeDoublesThroughput(GameTestHelper h) {
        FluidLinePositions copper = buildFluidLine(h, 1, AllBlocks.FLUID_PIPE.get());
        FluidLinePositions manasteel = buildFluidLine(h, 7, VEBlocks.MANASTEEL_FLUID_PIPE.get());

        int[] start = new int[2];
        h.runAtTickTime(50, () -> {
            start[0] = fluidIn(h, copper.dst());
            start[1] = fluidIn(h, manasteel.dst());
        });
        h.runAtTickTime(90, () -> {
            int c = fluidIn(h, copper.dst()) - start[0];
            int m = fluidIn(h, manasteel.dst()) - start[1];
            ViridianEngineering.LOGGER.info("[VE Phase1] fluid over 40 ticks: copper={} mB manasteel={} mB ratio={}",
                    c, m, c == 0 ? "n/a" : String.format("%.2f", (double) m / c));
            h.assertTrue(c > 0, "copper line moved nothing");
            h.assertTrue(m >= c * 1.6, "manasteel line not ~2x copper: copper=" + c + " manasteel=" + m);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void manasteelSightGlassKeepsFlowAndWrenchesBack(GameTestHelper h) {
        var reference = buildFluidLine(h, 1, VEBlocks.MANASTEEL_FLUID_PIPE.get());
        var glass = buildFluidLine(h, 7, VEBlocks.MANASTEEL_FLUID_PIPE.get());
        var level = h.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        List<BlockPos> windows = List.of(h.absolutePos(new BlockPos(2, 2, 7)), h.absolutePos(new BlockPos(4, 2, 7)));
        int[] start = new int[2];
        h.runAtTickTime(45, () -> {
            for (BlockPos pos : windows) {
                var state = level.getBlockState(pos);
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.WATERLOGGED, true));
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                h.assertTrue(VEBlocks.MANASTEEL_FLUID_PIPE.get().onWrenched(level.getBlockState(pos),
                        new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.SUCCESS, "Could not wrench Manasteel to glass");
                state = level.getBlockState(pos);
                h.assertTrue(state.is(VEBlocks.MANASTEEL_GLASS_FLUID_PIPE.get()) && state.getValue(GlassFluidPipeBlock.AXIS) == Direction.Axis.X
                        && state.getValue(BlockStateProperties.WATERLOGGED), "Sight glass lost its axis or waterlogging");
                var behaviour = BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE);
                h.assertTrue(behaviour != null && !behaviour.canPullFluidFrom(new FluidStack(VEFluids.LIQUID_MANA.get(), 250),
                        state, Direction.WEST), "Sight glass accepted Liquid Mana");
            }
        });
        h.runAtTickTime(55, () -> {
            start[0] = fluidIn(h, reference.dst());
            start[1] = fluidIn(h, glass.dst());
        });
        h.runAtTickTime(90, () -> {
            int regularFlow = fluidIn(h, reference.dst()) - start[0];
            int glassFlow = fluidIn(h, glass.dst()) - start[1];
            h.assertTrue(regularFlow > 0 && Math.abs(regularFlow - glassFlow) <= 128,
                    "Sight glass moved " + glassFlow + " mB while normal Manasteel moved " + regularFlow);
            for (BlockPos pos : windows) {
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                VEBlocks.MANASTEEL_GLASS_FLUID_PIPE.get().onWrenched(level.getBlockState(pos), new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                var state = level.getBlockState(pos);
                h.assertTrue(state.is(VEBlocks.MANASTEEL_FLUID_PIPE.get()) && state.getValue(BlockStateProperties.WATERLOGGED),
                        "Wrenching the sight glass back lost Manasteel or waterlogging");
            }
            h.succeed();
        });
    }

    private static FluidLinePositions buildFluidLine(GameTestHelper h, int z, Block pipeBlock) {
        // tank(1) - pipe(2) - pump(3, east) - pipe(4) - tank(5)
        BlockPos src = new BlockPos(1, 2, z), dst = new BlockPos(5, 2, z);
        h.setBlock(src, AllBlocks.FLUID_TANK.getDefaultState());
        h.setBlock(dst, AllBlocks.FLUID_TANK.getDefaultState());
        List<BlockPos> pipes = List.of(new BlockPos(2, 2, z), new BlockPos(4, 2, z));
        for (BlockPos p : pipes) h.setBlock(p, pipeBlock);
        // 64 RPM: slow enough that neither 8,000 mB source tank drains inside the measuring window.
        placePump(h, new BlockPos(3, 2, z), 64);
        connectFluidPipes(h, pipes);
        h.runAfterDelay(1, () -> {
            IFluidHandler handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(src), null);
            if (handler != null) handler.fill(new FluidStack(Fluids.WATER, 8_000), IFluidHandler.FluidAction.EXECUTE);
        });
        return new FluidLinePositions(src, dst);
    }

    private static int fluidIn(GameTestHelper h, BlockPos rel) {
        IFluidHandler handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(rel), null);
        return handler == null ? 0 : handler.getFluidInTank(0).getAmount();
    }

    // ------------------------------------------------------------------ 3.1 alfheim press

    /** Upgraded press on a depot: iron ingot + 3,000 mana -> manasteel ingot, mana fully spent. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void alfheimPressInfusesIron(GameTestHelper h) {
        BlockPos depot = new BlockPos(6, 1, 9), press = new BlockPos(6, 3, 9), motor = new BlockPos(5, 3, 9);
        h.setBlock(depot, AllBlocks.DEPOT.getDefaultState());
        h.setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        h.runAfterDelay(2, () -> {
            if (h.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m) m.generatedSpeed.setValue(128);
            IAlfheimPress alfheim = (IAlfheimPress) h.getBlockEntity(press);
            alfheim.ve$setAlfheimModule(true);
            alfheim.ve$setMana(3_000);
            ((DepotBlockEntity) h.getBlockEntity(depot)).setHeldItem(new ItemStack(Items.IRON_INGOT));
        });
        h.succeedWhen(() -> {
            ItemStack held = ((DepotBlockEntity) h.getBlockEntity(depot)).getHeldItem();
            h.assertTrue(BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals("botania:manasteel_ingot"),
                    "depot holds " + held);
            int mana = ((IAlfheimPress) h.getBlockEntity(press)).ve$getMana();
            h.assertTrue(mana == 0, "press should have spent exactly 3000 mana, has " + mana + " left");
        });
    }

    /**
     * Full chain: mana pool -> 2 Terrasteel pipes -> upgraded press -> iron ingot on a depot.
     * The ingot becomes manasteel and every point of mana is accounted for.
     * (A depot uses the same press callback as a belt, so this also covers belts.)
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void alfheimPressRunsOnPipedMana(GameTestHelper h) {
        BlockPos depot = new BlockPos(6, 1, 6), press = new BlockPos(6, 3, 6), motor = new BlockPos(5, 3, 6);
        BlockPos poolPos = new BlockPos(6, 3, 9);
        List<BlockPos> pipes = List.of(new BlockPos(6, 3, 7), new BlockPos(6, 3, 8));
        h.setBlock(depot, AllBlocks.DEPOT.getDefaultState());
        h.setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        h.setBlock(poolPos, block("botania:mana_pool"));

        h.runAfterDelay(2, () -> {
            if (h.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m) m.generatedSpeed.setValue(128);
            ((IAlfheimPress) h.getBlockEntity(press)).ve$setAlfheimModule(true);
            h.getLevel().invalidateCapabilities(h.absolutePos(press));
            pool(h, poolPos).receiveMana(POOL_START);
            for (BlockPos p : pipes) h.setBlock(p, VEBlocks.TERRASTEEL_MANA_PIPE.get());
            connectManaPipes(h, pipes);
            ((DepotBlockEntity) h.getBlockEntity(depot)).setHeldItem(new ItemStack(Items.IRON_INGOT));
        });

        h.succeedWhen(() -> {
            ItemStack held = ((DepotBlockEntity) h.getBlockEntity(depot)).getHeldItem();
            h.assertTrue(BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals("botania:manasteel_ingot")
                    , "depot holds " + held);
            long poolMana = pool(h, poolPos).getCurrentMana();
            long pressMana = ((IAlfheimPress) h.getBlockEntity(press)).ve$getMana();
            long inPipes = pipeMana(h, pipes);
            long spent = 3_000;
            ViridianEngineering.LOGGER.info("[VE Phase1] piped press: pool={} pipes={} press={} spent={} total={}",
                    poolMana, inPipes, pressMana, spent, poolMana + inPipes + pressMana + spent);
            h.assertTrue(poolMana + inPipes + pressMana + spent == POOL_START, "mana not conserved");
        });
    }

    /** Without enough mana an upgraded press leaves iron alone (no infusion, no ordinary pressing). */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void alfheimPressWaitsForMana(GameTestHelper h) {
        BlockPos depot = new BlockPos(9, 1, 9), press = new BlockPos(9, 3, 9), motor = new BlockPos(8, 3, 9);
        h.setBlock(depot, AllBlocks.DEPOT.getDefaultState());
        h.setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        h.runAfterDelay(2, () -> {
            if (h.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m) m.generatedSpeed.setValue(128);
            IAlfheimPress alfheim = (IAlfheimPress) h.getBlockEntity(press);
            alfheim.ve$setAlfheimModule(true);
            alfheim.ve$setMana(2_999);
            ((DepotBlockEntity) h.getBlockEntity(depot)).setHeldItem(new ItemStack(Items.IRON_INGOT));
        });
        h.runAtTickTime(150, () -> {
            ItemStack held = ((DepotBlockEntity) h.getBlockEntity(depot)).getHeldItem();
            h.assertTrue(held.is(Items.IRON_INGOT), "iron should be untouched, depot holds " + held);
            h.assertTrue(((IAlfheimPress) h.getBlockEntity(press)).ve$getMana() == 2_999, "mana changed");
            h.succeed();
        });
    }

    private record FluidLinePositions(BlockPos src, BlockPos dst) {}

    /**
     * User report: pumped mana crawled down a long line. Pumped flow is directional (spec 3.2): pool ->
     * 3 suction pipes -> 256 RPM pump -> 6 discharge pipes -> pool must carry close to 360 mana/tick.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void manaPipePumpedFlowCarriesDownLongLines(GameTestHelper h) {
        BlockPos a = new BlockPos(0, 1, 7), b = new BlockPos(11, 1, 7);
        List<BlockPos> pipes = new java.util.ArrayList<>();
        for (int x = 1; x <= 10; x++) if (x != 4) pipes.add(new BlockPos(x, 1, 7));
        h.setBlock(a, block("botania:mana_pool"));
        h.setBlock(b, block("botania:mana_pool"));
        placePump(h, new BlockPos(4, 1, 7), 256);
        for (BlockPos p : pipes) h.setBlock(p, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        connectManaPipes(h, pipes);
        h.runAfterDelay(1, () -> pool(h, a).receiveMana(POOL_START));
        long[] at = new long[1];
        h.runAtTickTime(81, () -> at[0] = pool(h, b).getCurrentMana());
        h.runAtTickTime(281, () -> {
            long ma = pool(h, a).getCurrentMana(), mb = pool(h, b).getCurrentMana(), mp = pipeMana(h, pipes);
            h.assertTrue(ma + mb + mp == POOL_START, "mana not conserved: " + (ma + mb + mp));
            long rate = (mb - at[0]) / 200;
            ViridianEngineering.LOGGER.info("[VE pipes] long pumped line: {} mana/tick at pool B", rate);
            h.assertTrue(rate >= 340, "long pumped line carries only " + rate + " mana/tick (expected ~360)");
            h.succeed();
        });
    }
}
