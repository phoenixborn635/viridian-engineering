package dev.viridianengineering.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.autocraft.*;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlock;
import dev.viridianengineering.content.corporea.TransactionContext;
import dev.viridianengineering.content.drive.*;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

/**
 * Phase 3 tasks c + d and the Phase 3 verification gate (spec): "Bulk job 'make 500 Precision
 * Mechanisms' resolves sub-recipes, dispatches across an auto-discovered sequenced-assembly line,
 * survives a server restart mid-craft without losing/duplicating escrowed items, and stores outputs
 * into a 4-slot Archive Pylon holding mixed-NBT items."
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseThreeAutocraftTests {

    static final ResourceLocation PRESS_GOLD = ResourceLocation.parse("create:pressing/gold_ingot");
    static final ResourceLocation SHAFT = ResourceLocation.parse("create:crafting/kinetics/shaft");
    static final ResourceLocation COGWHEEL = ResourceLocation.parse("create:crafting/kinetics/cogwheel");
    static final ResourceLocation LARGE_COGWHEEL = ResourceLocation.parse("create:crafting/kinetics/large_cogwheel");
    static final ResourceLocation IRON_NUGGET = ResourceLocation.parse("minecraft:iron_nugget");
    static final ResourceLocation PRECISION = ResourceLocation.parse("create:sequenced_assembly/precision_mechanism");

    private static ItemStackKey key(Item item) { return ItemStackKey.of(new ItemStack(item)); }

    static CraftPattern pattern(ServerLevel level, ResourceLocation id, Set<Item> stocked) {
        var holder = level.getRecipeManager().byKey(id).orElseThrow(() -> new IllegalStateException("No recipe " + id));
        PatternFactory.Result r = PatternFactory.fromRecipe(level, holder, stocked);
        if (!r.ok()) throw new IllegalStateException(r.error().getString());
        return r.pattern();
    }

    static List<CraftPattern> precisionPatterns(ServerLevel level) {
        Set<Item> stocked = Set.of(Items.GOLD_INGOT, AllItems.ANDESITE_ALLOY.get(), Items.OAK_PLANKS, Items.IRON_INGOT);
        List<CraftPattern> list = new ArrayList<>();
        for (ResourceLocation id : List.of(PRESS_GOLD, SHAFT, COGWHEEL, LARGE_COGWHEEL, IRON_NUGGET, PRECISION))
            list.add(pattern(level, id, stocked));
        return list;
    }

    // ------------------------------------------------------------------ planner (task d)

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void plannerResolves500PrecisionMechanisms(GameTestHelper h) {
        List<CraftPattern> patterns = precisionPatterns(h.getLevel());
        Map<ItemStackKey, Long> stock = Map.of(key(Items.GOLD_INGOT), 1_000L, key(AllItems.ANDESITE_ALLOY.get()), 2_000L,
                key(Items.OAK_PLANKS), 10_000L, key(Items.IRON_INGOT), 400L, key(AllItems.PRECISION_MECHANISM.get()), 50L);
        CraftingPlanner.Plan plan = CraftingPlanner.plan(key(AllItems.PRECISION_MECHANISM.get()), 500, stock, patterns);
        h.assertTrue(plan.complete(), "Plan has missing items: " + plan.missing());
        // 0.8 chance per assembly -> 625 runs; 5 loops x (cogwheel, large cogwheel, nugget) each.
        check(h, plan.craftsOf(key(AllItems.PRECISION_MECHANISM.get())), 625, "precision mechanism runs");
        check(h, plan.craftsOf(key(AllItems.GOLDEN_SHEET.get())), 625, "golden sheet pressings");
        check(h, plan.craftsOf(key(AllBlocks.COGWHEEL.get().asItem())), 3_125, "cogwheel crafts");
        check(h, plan.craftsOf(key(AllBlocks.LARGE_COGWHEEL.get().asItem())), 3_125, "large cogwheel crafts");
        check(h, plan.craftsOf(key(AllBlocks.SHAFT.get().asItem())), 782, "shaft crafts (6,250 shafts, 8 per craft)");
        check(h, plan.craftsOf(key(Items.IRON_NUGGET)), 348, "iron nugget crafts (3,125 nuggets, 9 per craft)");
        check(h, plan.reserve().get(key(Items.GOLD_INGOT)), 625, "gold reserved");
        check(h, plan.reserve().get(key(AllItems.ANDESITE_ALLOY.get())), 1_564, "andesite alloy reserved");
        check(h, plan.reserve().get(key(Items.OAK_PLANKS)), 9_375, "planks reserved");
        check(h, plan.reserve().get(key(Items.IRON_INGOT)), 348, "iron reserved");
        h.assertTrue(!plan.reserve().containsKey(key(AllItems.PRECISION_MECHANISM.get())), "The requested item was taken from stock instead of crafted");

        Map<ItemStackKey, Long> noIron = new HashMap<>(stock);
        noIron.remove(key(Items.IRON_INGOT));
        CraftingPlanner.Plan missing = CraftingPlanner.plan(key(AllItems.PRECISION_MECHANISM.get()), 500, noIron, patterns);
        check(h, missing.missing().get(key(Items.IRON_INGOT)), 348, "missing iron reported");
        h.succeed();
    }

    private static void check(GameTestHelper h, Long actual, long expected, String what) {
        h.assertTrue(actual != null && actual == expected, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void patternsSurviveSaveAndLoad(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        CraftPattern p = pattern(h.getLevel(), PRECISION, Set.of());
        CraftPattern back = CraftPattern.load(p.save(registries), registries);
        h.assertTrue(back.id.equals(p.id) && back.loops == 5 && back.steps.equals(p.steps) && back.stepItems.size() == 3
                && Math.abs(back.chance - 0.8) < 1e-6 && back.outputKey().equals(p.outputKey())
                && back.inputsPerCraft().equals(p.inputsPerCraft()), "Sequenced pattern changed across save/load");
        h.succeed();
    }

    // ------------------------------------------------------------------ small in-world job

    private static void poweredTerminal(GameTestHelper h, BlockPos terminal) {
        h.setBlock(terminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState().setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        h.setBlock(terminal.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
    }

    private static void spin(GameTestHelper h, BlockPos motor, int rpm) {
        ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(rpm);
    }

    private static long count(Map<ItemStackKey, Long> stock, Item item) {
        return stock.getOrDefault(key(item), 0L);
    }

    /** Counts an item whatever its components (unfinished assemblies carry their progress). */
    private static long countAny(Map<ItemStackKey, Long> stock, Item item) {
        long n = 0;
        for (var e : stock.entrySet()) if (e.getKey().item() == item) n += e.getValue();
        return n;
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 200)
    public static void craftingJobRunsInTerminalAndRefunds(GameTestHelper h) {
        BlockPos terminal = new BlockPos(1, 1, 1), pylon = new BlockPos(2, 1, 1);
        poweredTerminal(h, terminal);
        h.setBlock(pylon, VEStorage.ARCHIVE_PYLON.getDefaultState().setValue(ArchivePylonBlock.FACING, Direction.EAST));
        h.runAfterDelay(2, () -> {
            ServerLevel level = h.getLevel();
            var p = (ArchivePylonBlockEntity) h.getBlockEntity(pylon);
            p.mount(VEStorage.TERRA_DRIVE_16K.asStack());
            var drives = TerraDriveSavedData.get(level.getServer());
            drives.insert(drives.find(TerraDriveItem.getId(p.driveIn(0))), new ItemStack(AllItems.ANDESITE_ALLOY.get()), 5, false);
            var key = SableCompat.endpointKey(level, h.absolutePos(terminal), Direction.DOWN);
            PatternStore.get(level.getServer()).add(key, pattern(level, SHAFT, Set.of()));
            BlockPos origin = h.absolutePos(terminal);
            h.assertTrue(CraftingScheduler.request(level, origin, key(AllBlocks.SHAFT.get().asItem()), 16).error() != null,
                    "A job started while the network was unpowered");
            spin(h, terminal.below(), 64);
        });
        h.runAfterDelay(6, () -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(terminal);
            var tooMany = CraftingScheduler.request(level, origin, key(AllBlocks.SHAFT.get().asItem()), 24);
            h.assertTrue(tooMany.error() != null, "24 shafts need 6 alloy but only 5 exist; the job should be refused");
            var ok = CraftingScheduler.request(level, origin, key(AllBlocks.SHAFT.get().asItem()), 16);
            h.assertTrue(ok.job() != null, "Job refused: " + (ok.error() == null ? "" : ok.error().getString()));
        });
        h.runAfterDelay(60, () -> {
            ServerLevel level = h.getLevel();
            var stock = CraftingScheduler.networkStock(level, h.absolutePos(terminal));
            h.assertTrue(count(stock, AllBlocks.SHAFT.get().asItem()) == 16 && count(stock, AllItems.ANDESITE_ALLOY.get()) == 1,
                    "After the job: shafts " + count(stock, AllBlocks.SHAFT.get().asItem()) + " (want 16), alloy " + count(stock, AllItems.ANDESITE_ALLOY.get()) + " (want 1)");
            h.assertTrue(CraftingJobSavedData.get(level.getServer()).jobsFor(SableCompat.endpointKey(level, origin(h, terminal), Direction.DOWN)).isEmpty(),
                    "Finished job was not cleared");
            h.succeed();
        });
    }

    private static BlockPos origin(GameTestHelper h, BlockPos rel) { return h.absolutePos(rel); }

    // ------------------------------------------------------------------ the Phase 3 gate

    /*
     * Layout (y = 1 unless noted):
     *   terminal (0,1,2) screen up, motor below; pylon (0,1,3) bays south; pipes z=2, x=1..9.
     *   Assembly line: belt x=1..6 at z=4, motor (1,1,5); machine ports (1,1,3) and (6,1,3) face the
     *   end segments; three deployers at y=3 over x=2..4 driven by a motor at (1,3,4).
     *   Press station: depot (9,1,4), press (9,3,4) with motor (10,3,4); machine port (9,1,3).
     */
    static final BlockPos T = new BlockPos(0, 1, 2), PYLON = new BlockPos(0, 1, 3);
    static final BlockPos LINE_IN_PORT = new BlockPos(1, 1, 3), LINE_OUT_PORT = new BlockPos(6, 1, 3);
    static final BlockPos BELT_START = new BlockPos(1, 1, 4), BELT_END = new BlockPos(6, 1, 4), BELT_MOTOR = new BlockPos(1, 1, 5);
    static final BlockPos DEPLOYER_MOTOR = new BlockPos(1, 3, 4);
    static final BlockPos DEPOT = new BlockPos(9, 1, 4), PRESS = new BlockPos(9, 3, 4), PRESS_MOTOR = new BlockPos(10, 3, 4), PRESS_PORT = new BlockPos(9, 1, 3);

    static final long GOLD = 800, ALLOY = 2_000, PLANKS = 12_000, IRON = 500; // margin for rolled-yield top-ups

    static void buildGate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        poweredTerminal(h, T);
        h.setBlock(PYLON, VEStorage.ARCHIVE_PYLON.getDefaultState().setValue(ArchivePylonBlock.FACING, Direction.SOUTH));
        for (int x = 1; x <= 9; x++) h.setBlock(new BlockPos(x, 1, 2), VEBlocks.CORPOREA_PIPE.getDefaultState());
        BlockState port = VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.SOUTH);
        h.setBlock(LINE_IN_PORT, port);
        h.setBlock(LINE_OUT_PORT, port);
        h.setBlock(PRESS_PORT, port);

        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(ShaftBlock.AXIS, Direction.Axis.Z);
        h.setBlock(BELT_START, shaft);
        h.setBlock(BELT_END, shaft);
        BeltConnectorItem.createBelts(level, h.absolutePos(BELT_START), h.absolutePos(BELT_END));
        h.setBlock(BELT_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));

        BlockState deployer = AllBlocks.DEPLOYER.getDefaultState().setValue(DeployerBlock.FACING, Direction.DOWN)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true);
        for (int x = 2; x <= 4; x++) h.setBlock(new BlockPos(x, 3, 4), deployer);
        h.setBlock(DEPLOYER_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));

        h.setBlock(DEPOT, AllBlocks.DEPOT.getDefaultState());
        h.setBlock(PRESS, AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(PRESS_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.WEST));
    }

    /** Mutable state of the gate test (GameTest lambdas need effectively-final holders). */
    private static final class Gate {
        int phase;
        UUID jobId;
        CraftingJob job;
        Map<ItemStackKey, Long> mixedBefore;
        long restartedAt;
        long amount;
        long cancelAfter;
    }

    /** The Phase 3 gate: the whole 500-mechanism job, with a simulated restart mid-craft. */
    @GameTest(template = "empty_12x6x12", timeoutTicks = 120_000, batch = "viridian_autocraft_gate")
    public static void gatePrecisionMechanismsSurviveRestart(GameTestHelper h) {
        runLine(h, 500, -1);
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 100)
    public static void jobCollectsItemThatReachesBeltEdgeBeforePolling(GameTestHelper h) {
        buildGate(h);
        h.runAfterDelay(2, () -> spin(h, BELT_MOTOR, 256));
        h.runAfterDelay(15, () -> {
            ServerLevel level = h.getLevel();
            var belt = BeltHelper.getControllerBE(level, h.absolutePos(BELT_START));
            float speed = belt.getDirectionAwareBeltMovementSpeed();
            h.assertTrue(speed != 0, "Test belt is stopped");
            BlockPos input = speed > 0 ? LINE_IN_PORT : LINE_OUT_PORT;
            var terminal = SableCompat.endpointKey(level, h.absolutePos(T), Direction.DOWN);
            var output = key(AllItems.PRECISION_MECHANISM.get());
            var job = new CraftingJob(UUID.randomUUID(), terminal, output, 1);
            var pattern = pattern(level, PRECISION, Set.of());
            job.apply(new CraftingPlanner.Plan(List.of(new CraftingPlanner.Step(pattern, 1)), Map.of(), Map.of()));
            var step = job.steps.getFirst();
            step.endpoint = SableCompat.endpointKey(level, h.absolutePos(input), Direction.DOWN);
            step.inFlight = 1;
            CraftingJobSavedData.get(level.getServer()).add(job);

            var item = new TransportedItemStack(output.toStack(1));
            item.beltPosition = speed > 0 ? belt.beltLength - .01f : .01f;
            item.prevBeltPosition = item.beltPosition;
            belt.getInventory().addItem(item);
            h.runAfterDelay(5, () -> {
                h.assertTrue(job.escrowed(output) == 1 && step.inFlight == 0,
                        "Belt ejected the output before the job could collect it");
                job.escrowTake(output, 1);
                job.status = CraftingJob.Status.DONE;
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 100)
    public static void finishedJobRecoversStockFromStalledBelt(GameTestHelper h) {
        buildGate(h);
        h.setBlock(BELT_MOTOR, net.minecraft.world.level.block.Blocks.AIR);
        h.runAfterDelay(2, () -> spin(h, T.below(), 64));
        h.runAfterDelay(15, () -> {
            ServerLevel level = h.getLevel();
            var belt = BeltHelper.getControllerBE(level, h.absolutePos(BELT_START));
            h.assertTrue(belt != null && belt.getSpeed() == 0, "Test belt must be stopped");
            var terminal = SableCompat.endpointKey(level, h.absolutePos(T), Direction.DOWN);
            var output = key(AllItems.PRECISION_MECHANISM.get());
            var job = new CraftingJob(UUID.randomUUID(), terminal, output, 1);
            var pattern = pattern(level, PRECISION, Set.of());
            job.apply(new CraftingPlanner.Plan(List.of(new CraftingPlanner.Step(pattern, 1)), Map.of(), Map.of()));
            var step = job.steps.getFirst();
            step.endpoint = SableCompat.endpointKey(level, h.absolutePos(LINE_IN_PORT), Direction.DOWN);
            step.inFlight = 1;
            job.status = CraftingJob.Status.FINISHING;
            job.delivered = 1;
            job.lastProgress = job.progress;
            job.lastFinishingFlight = 1;
            job.lastProgressAt = level.getGameTime() - CraftingScheduler.STALL_TICKS - 1;
            var sheet = key(AllItems.GOLDEN_SHEET.get());
            var stuck = new TransportedItemStack(sheet.toStack(1));
            stuck.beltPosition = 2.5f;
            stuck.prevBeltPosition = stuck.beltPosition;
            stuck.locked = true;
            belt.getInventory().addItem(stuck);
            belt.getInventory().tick();
            CraftingJobSavedData.get(level.getServer()).add(job);

            h.runAfterDelay(5, () -> {
                h.assertTrue(job.escrowed(sheet) == 1 && step.inFlight == 0 && step.endpoint == null,
                        "Finished job released its belt with an unrecovered sheet");
                h.assertTrue(belt.getInventory().getStackAtOffset(2) == null, "Recovered sheet was duplicated on the belt");
                job.escrowTake(sheet, 1);
                job.status = CraftingJob.Status.DONE;
                h.succeed();
            });
        });
    }

    /** Cancelling mid-job: in-flight items finish, everything left (and every unfinished assembly) is refunded. */
    @GameTest(template = "empty_12x6x12", timeoutTicks = 24_000, batch = "viridian_autocraft_cancel")
    public static void cancelMidJobRefundsEverything(GameTestHelper h) {
        runLine(h, 40, 4);
    }

    private static void runLine(GameTestHelper h, long amount, long cancelAfter) {
        buildGate(h);
        Gate gate = new Gate();
        gate.amount = amount;
        gate.cancelAfter = cancelAfter;
        h.runAfterDelay(2, () -> {
            ServerLevel level = h.getLevel();
            for (BlockPos m : List.of(T.below(), BELT_MOTOR, DEPLOYER_MOTOR, PRESS_MOTOR)) spin(h, m, 256);
            var pylon = (ArchivePylonBlockEntity) h.getBlockEntity(PYLON);
            for (var drive : List.of(VEStorage.TERRA_DRIVE_1M, VEStorage.TERRA_DRIVE_256K, VEStorage.TERRA_DRIVE_64K, VEStorage.TERRA_DRIVE_16K))
                h.assertTrue(pylon.mount(drive.asStack()) == ArchivePylonBlockEntity.MountResult.MOUNTED, "Pylon refused a drive");
            var drives = TerraDriveSavedData.get(level.getServer());
            DriveStorageRecord bulk = drives.find(TerraDriveItem.getId(pylon.driveIn(0)));
            drives.insert(bulk, new ItemStack(Items.GOLD_INGOT), GOLD, false);
            drives.insert(bulk, new ItemStack(AllItems.ANDESITE_ALLOY.get()), ALLOY, false);
            drives.insert(bulk, new ItemStack(Items.OAK_PLANKS), PLANKS, false);
            drives.insert(bulk, new ItemStack(Items.IRON_INGOT), IRON, false);
            // Mixed-component items that must come through untouched.
            DriveStorageRecord mixed = drives.find(TerraDriveItem.getId(pylon.driveIn(3)));
            for (int i = 0; i < 20; i++) {
                ItemStack named = new ItemStack(Items.PAPER);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("Ledger " + i));
                drives.insert(mixed, named, 3, false);
            }
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
            drives.insert(mixed, book, 2, false);
            gate.mixedBefore = new HashMap<>(mixed.entries());

            var terminal = SableCompat.endpointKey(level, h.absolutePos(T), Direction.DOWN);
            for (CraftPattern p : precisionPatterns(level)) PatternStore.get(level.getServer()).add(terminal, p);
        });

        h.runAfterDelay(30, () -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(T);
            // Auto-discovery: one 3-deployer line and one press station.
            var endpoints = MachineDiscovery.endpoints(level, origin);
            boolean line = endpoints.stream().anyMatch(e -> e instanceof MachineDiscovery.AssemblyLine l
                    && l.ops().equals(List.of(CraftPattern.Op.DEPLOY, CraftPattern.Op.DEPLOY, CraftPattern.Op.DEPLOY)));
            boolean press = endpoints.stream().anyMatch(e -> e instanceof MachineDiscovery.DepotMachine d && d.op() == CraftPattern.Op.PRESS);
            h.assertTrue(line && press, "Discovery found " + endpoints);
            // Belts and machine depots are endpoints, never storage.
            h.assertTrue(CorporeaNetworkManager.deposit(level, origin, new ItemStack(Items.STICK, 3), CorporeaNetworkManager.Access.AUTOMATED,
                    new TransactionContext()).isEmpty(), "Deposit failed");
            var res = CraftingScheduler.request(level, origin, key(AllItems.PRECISION_MECHANISM.get()), gate.amount);
            h.assertTrue(res.job() != null, "Job refused: " + (res.error() == null ? "" : res.error().getString()));
            gate.jobId = res.job().id;
            gate.job = res.job();
            gate.phase = 1;
        });

        h.onEachTick(() -> {
            if (gate.phase == 0) return;
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            CraftingJob job = gate.job;
            long made = job.collected.getOrDefault(key(AllItems.PRECISION_MECHANISM.get()), 0L);
            if (level.getGameTime() % 2000 == 0) {
                StringBuilder sb = new StringBuilder();
                for (var st : job.steps)
                    sb.append(st.pattern.outputKey().toStack(1).getHoverName().getString()).append(" rem=").append(st.remaining)
                            .append(" fly=").append(st.inFlight).append(" park=").append(st.parked).append(" ep=").append(st.endpoint != null).append("; ");
                ViridianEngineering.LOGGER.info("[VE gate] phase {} status {} made {} | {} | escrow {} | collected {}",
                        gate.phase, job.status, made, sb, CraftingScheduler.describe(job.escrow), job.collected);
                var belt = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(BELT_START), Direction.UP);
                var beltEnd = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(BELT_END), Direction.UP);
                StringBuilder dep = new StringBuilder();
                for (int x = 2; x <= 4; x++) {
                    var d = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(x, 3, 4)), null);
                    dep.append(d == null ? "null" : d.getStackInSlot(d.getSlots() - 1)).append(' ');
                }
                StringBuilder segs = new StringBuilder();
                for (int x = 1; x <= 6; x++) {
                    var seg = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(x, 1, 4)), Direction.UP);
                    segs.append(seg == null ? "null" : seg.getStackInSlot(0)).append(' ');
                }
                if (gate.phase == 3) ViridianEngineering.LOGGER.info("[VE gate] segments {}", segs);
                var beltBe = h.getBlockEntity(BELT_START);
                ViridianEngineering.LOGGER.info("[VE gate] belt start {} end {} deployers {} beltBE {} endpoints {}",
                        belt == null ? "null" : belt.getStackInSlot(0), beltEnd == null ? "null" : beltEnd.getStackInSlot(0), dep,
                        beltBe instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity b ? ("len=" + b.beltLength + " speed=" + b.getSpeed()) : String.valueOf(beltBe),
                        MachineDiscovery.endpoints(level, h.absolutePos(T)));
            }

            if (gate.phase == 1 && made >= 3 && job.status == CraftingJob.Status.RUNNING) {
                // ---- simulated server restart, mid-craft ----
                var registries = level.registryAccess();
                Map<ItemStackKey, Long> escrowBefore = new HashMap<>(job.escrow);
                long inFlightBefore = job.inFlight();
                Map<ItemStackKey, Long> netBefore = CraftingScheduler.networkStock(level, h.absolutePos(T));
                CompoundTag jobs = CraftingJobSavedData.get(server).save(new CompoundTag(), registries);
                CompoundTag patterns = PatternStore.get(server).save(new CompoundTag(), registries);
                CompoundTag driveTag = TerraDriveSavedData.get(server).save(new CompoundTag(), registries);
                var storage = server.overworld().getDataStorage();
                storage.set(CraftingJobSavedData.ID, CraftingJobSavedData.load(jobs, registries));
                storage.set(PatternStore.ID, PatternStore.load(patterns, registries));
                storage.set(TerraDriveSavedData.ID, TerraDriveSavedData.load(driveTag, registries));
                MachineDiscovery.clearCache();
                CorporeaNetworkManager.invalidate(level);

                CraftingJob reloaded = CraftingJobSavedData.get(server).find(gate.jobId);
                h.assertTrue(reloaded != null && reloaded != job, "Job did not come back from its SavedData");
                h.assertTrue(reloaded.escrow.equals(escrowBefore), "Escrow changed across the restart");
                h.assertTrue(reloaded.inFlight() == inFlightBefore, "In-flight count changed across the restart");
                h.assertTrue(CraftingScheduler.networkStock(level, h.absolutePos(T)).equals(netBefore), "Network contents changed across the restart");
                gate.job = reloaded;
                gate.restartedAt = level.getGameTime();
                gate.phase = 2;
                return;
            }
            if (gate.phase == 2 && gate.cancelAfter > 0 && made >= gate.cancelAfter) {
                CraftingScheduler.cancel(job);
                gate.phase = 3;
                return;
            }
            if (gate.phase >= 2 && job.status == CraftingJob.Status.DONE) {
                gate.phase = 4;
                verifyGate(h, gate);
            }
        });
    }

    private static void verifyGate(GameTestHelper h, Gate gate) {
        ServerLevel level = h.getLevel();
        CraftingJob job = gate.job;
        var stock = CraftingScheduler.networkStock(level, h.absolutePos(T));
        long pm = count(stock, AllItems.PRECISION_MECHANISM.get());
        long madePm = job.collected.getOrDefault(key(AllItems.PRECISION_MECHANISM.get()), 0L);
        boolean cancelled = gate.cancelAfter > 0;
        h.assertTrue(cancelled ? madePm >= gate.cancelAfter && madePm < gate.amount : madePm >= gate.amount,
                "Precision mechanisms made: " + madePm + " for a job of " + gate.amount + (cancelled ? " cancelled after " + gate.cancelAfter : ""));
        h.assertTrue(pm == madePm && job.delivered == madePm,
                "Precision mechanisms: made " + madePm + ", delivered " + job.delivered + ", in storage " + pm);
        h.assertTrue(job.escrow.isEmpty(), "Escrow not fully refunded: " + job.escrow);

        // Gold is conserved: every ingot is still an ingot, a sheet, an unfinished mechanism, or one line output.
        long lineOutputs = 0;
        for (var e : job.collected.entrySet())
            if (!e.getKey().equals(key(AllItems.GOLDEN_SHEET.get()))) lineOutputs += e.getValue();
        long gold = count(stock, Items.GOLD_INGOT) + count(stock, AllItems.GOLDEN_SHEET.get())
                + countAny(stock, AllItems.INCOMPLETE_PRECISION_MECHANISM.get()) + lineOutputs;
        h.assertTrue(gold == GOLD, "Gold not conserved: " + gold + " accounted for, " + GOLD + " put in (stock " + stock + ")");
        h.assertTrue(count(stock, Items.STICK) == 3, "Sticks went missing (deposited onto a machine?)");

        // Machines hold nothing of the job's afterwards.
        for (int x = 2; x <= 4; x++) {
            var deployer = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(x, 3, 4)), null);
            h.assertTrue(deployer != null && deployer.getStackInSlot(deployer.getSlots() - 1).isEmpty(), "Deployer " + x + " still holds items");
        }

        // The pylon still holds its mixed-component items exactly, and the outputs live in its drives.
        var pylon = (ArchivePylonBlockEntity) h.getBlockEntity(PYLON);
        var drives = TerraDriveSavedData.get(level.getServer());
        DriveStorageRecord mixed = drives.find(TerraDriveItem.getId(pylon.driveIn(3)));
        h.assertTrue(mixed.entries().equals(gate.mixedBefore), "Mixed-component items in the pylon changed");
        long pmInPylon = 0;
        for (int i = 0; i < ArchivePylonBlockEntity.SLOTS; i++)
            pmInPylon += drives.find(TerraDriveItem.getId(pylon.driveIn(i))).count(key(AllItems.PRECISION_MECHANISM.get()));
        h.assertTrue(pmInPylon == madePm, "Outputs not stored in the Archive Pylon: " + pmInPylon);
        ViridianEngineering.LOGGER.info("[VE gate] {}-PM job{}: {} made, {}/{} gold accounted for, restart at game tick {}, finished at {}, collected {}",
                gate.amount, cancelled ? " (cancelled)" : "", madePm, gold, GOLD, gate.restartedAt, level.getGameTime(), job.collected);
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void machinePrioritiesOrderEndpoints(GameTestHelper h) {
        BlockPos terminal = new BlockPos(1, 1, 1);
        poweredTerminal(h, terminal);
        BlockState port = VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.SOUTH);
        for (int x = 2; x <= 6; x++) h.setBlock(new BlockPos(x, 1, 1), VEBlocks.CORPOREA_PIPE.getDefaultState());
        for (int x : new int[] {3, 5}) {
            h.setBlock(new BlockPos(x, 1, 2), port);
            h.setBlock(new BlockPos(x, 1, 3), AllBlocks.DEPOT.getDefaultState());
            h.setBlock(new BlockPos(x, 3, 3), AllBlocks.MECHANICAL_PRESS.getDefaultState());
        }
        h.runAfterDelay(2, () -> spin(h, terminal.below(), 64));
        h.runAfterDelay(6, () -> {
            ServerLevel level = h.getLevel();
            ((dev.viridianengineering.content.corporea.CorporeaPortBlockEntity) h.getBlockEntity(new BlockPos(5, 1, 2))).setPriority(7);
            MachineDiscovery.clearCache();
            var endpoints = MachineDiscovery.endpoints(level, h.absolutePos(terminal));
            h.assertTrue(endpoints.size() == 2, "Expected 2 press stations, found " + endpoints);
            var first = (MachineDiscovery.DepotMachine) endpoints.get(0);
            h.assertTrue(first.depot().equals(h.absolutePos(new BlockPos(5, 1, 3))) && first.priority() == 7,
                    "Higher-priority machine port was not preferred: " + endpoints);
            // Machine depots are endpoints, not storage.
            h.assertTrue(CorporeaNetworkManager.browse(level, h.absolutePos(terminal), CorporeaNetworkManager.Access.MANUAL_WIRED,
                    new TransactionContext()).isEmpty(), "A machine depot was offered as storage");
            h.succeed();
        });
    }
}
