package dev.viridianengineering.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.airship.LevitationPylonBlockEntity;
import dev.viridianengineering.content.autocraft.CraftPattern;
import dev.viridianengineering.content.autocraft.CraftingJob;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import dev.viridianengineering.content.autocraft.PatternFactory;
import dev.viridianengineering.content.autocraft.PatternStore;
import dev.viridianengineering.content.corporea.ArcaneSparkEntity;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlock;
import dev.viridianengineering.content.corporea.TransactionContext;
import dev.viridianengineering.content.gear.GearData;
import dev.viridianengineering.content.gear.SylvanArmorItem;
import dev.viridianengineering.content.gear.SylvanEvents;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VECorporea;
import dev.viridianengineering.registry.VEGear;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.registry.BotaniaItems;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseSixIntegrationTests {
    private static final String TEMPLATE = "empty_12x6x12";
    private static final BlockPos TERMINAL = new BlockPos(1, 1, 1);
    private static final BlockPos STATION = new BlockPos(5, 1, 1);

    private static void setupNetwork(GameTestHelper h, BlockPos target) {
        h.setBlock(TERMINAL, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState().setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        h.setBlock(TERMINAL.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        for (int x = TERMINAL.getX() + 1; x < target.getX() - 1; x++) {
            h.setBlock(new BlockPos(x, 1, 1), VEBlocks.CORPOREA_PIPE.get());
        }
        h.setBlock(target.west(), VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.EAST));
        h.setBlock(TERMINAL.south(), VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.SOUTH));
        h.setBlock(TERMINAL.south(2), Blocks.BARREL);
        h.runAfterDelay(2, () -> spin(h, TERMINAL.below(), 64));
    }

    private static void spin(GameTestHelper h, BlockPos pos, int rpm) {
        ((CreativeMotorBlockEntity) h.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    private static void verifyCraft(GameTestHelper h, ResourceLocation recipeId) {
        verifyCraft(h, TERMINAL, recipeId, () -> {});
    }

    private static void verifyCraft(GameTestHelper h, BlockPos origin, ResourceLocation recipeId, Runnable verifyMachine) {
        var level = h.getLevel();
        BlockPos terminal = h.absolutePos(origin);
        var holder = level.getRecipeManager().byKey(recipeId).orElseThrow();
        var result = PatternFactory.fromRecipe(level, holder, java.util.Set.of());
        h.assertTrue(result.ok(), "Pattern rejected: " + result.error());
        CraftPattern pattern = result.pattern();
        pattern.inputsPerCraft().forEach((ingredient, count) -> h.assertTrue(CorporeaNetworkManager.deposit(level, terminal,
                ingredient.toStack(count.intValue()), CorporeaNetworkManager.Access.MANUAL_WIRED, new TransactionContext()).isEmpty(),
                "Stocking failed for " + ingredient));
        var key = SableCompat.endpointKey(level, terminal, Direction.DOWN);
        PatternStore.get(level.getServer()).add(key, pattern);
        var request = CraftingScheduler.request(level, terminal, pattern.outputKey(), pattern.output.getCount());
        h.assertTrue(request.job() != null, "Request rejected: " + request.error());
        h.succeedWhen(() -> {
            Map<dev.viridianengineering.content.drive.ItemStackKey, Long> stock = CraftingScheduler.networkStock(level, terminal);
            h.assertTrue(stock.getOrDefault(pattern.outputKey(), 0L) == pattern.output.getCount(),
                    "Station has not delivered " + pattern.output + " (" + request.job().status + ", stock " + stock + ")");
            h.assertTrue(request.job().status == dev.viridianengineering.content.autocraft.CraftingJob.Status.DONE,
                    "Station job did not finish");
            verifyMachine.run();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void mixerBasinAutocraftsAndReturnsOutput(GameTestHelper h) {
        setupMixer(h);
        h.runAfterDelay(15, () -> verifyCraft(h, ResourceLocation.parse("create:mixing/andesite_alloy")));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void breakingStationPortRefundsDispatchedAndReservedIngredients(GameTestHelper h) {
        setupMixer(h);
        h.runAfterDelay(15, () -> {
            var level = h.getLevel();
            BlockPos terminal = h.absolutePos(TERMINAL);
            var recipe = level.getRecipeManager().byKey(ResourceLocation.parse("create:mixing/andesite_alloy")).orElseThrow();
            var result = PatternFactory.fromRecipe(level, recipe, java.util.Set.of());
            h.assertTrue(result.ok(), "Mixer pattern rejected");
            CraftPattern pattern = result.pattern();
            pattern.inputsPerCraft().forEach((ingredient, count) ->
                    h.assertTrue(CorporeaNetworkManager.deposit(level, terminal, ingredient.toStack(Math.toIntExact(count * 2)),
                            CorporeaNetworkManager.Access.MANUAL_WIRED, new TransactionContext()).isEmpty(),
                            "Could not stock " + ingredient));
            PatternStore.get(level.getServer()).add(SableCompat.endpointKey(level, terminal, Direction.DOWN), pattern);
            CraftingJob job = CraftingScheduler.request(level, terminal, pattern.outputKey(), pattern.output.getCount() * 2L).job();
            h.assertTrue(job != null, "Mixer job was rejected");

            var basin = (BasinBlockEntity) h.getBlockEntity(STATION);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(job.inFlight() == 1 && !basin.getInputInventory().isEmpty(),
                            "Mixer has not received the first batch"))
                    .thenExecute(() -> h.setBlock(STATION.west(), Blocks.AIR))
                    .thenWaitUntil(() -> h.assertTrue(job.status == CraftingJob.Status.DONE, "Mixer job has not refunded"))
                    .thenExecute(() -> {
                        h.assertTrue(job.cancelled && job.escrow.isEmpty(), "Broken port did not cancel and settle the job");
                        h.assertTrue(basin.getInputInventory().isEmpty(), "Dispatched ingredients remained in the basin");
                        var stock = CraftingScheduler.networkStock(level, terminal);
                        pattern.inputsPerCraft().forEach((ingredient, count) ->
                                h.assertTrue(stock.getOrDefault(ingredient, 0L) == count * 2,
                                        "Missing or duplicate refund for " + ingredient));
                    })
                    .thenSucceed();
        });
    }

    private static void setupMixer(GameTestHelper h) {
        setupNetwork(h, STATION);
        h.setBlock(STATION, AllBlocks.BASIN.get());
        h.setBlock(STATION.above(2), AllBlocks.MECHANICAL_MIXER.get());
        BlockPos cog = STATION.east().above(2);
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(CogWheelBlock.AXIS, Direction.Axis.Y));
        h.setBlock(cog.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        h.runAfterDelay(2, () -> spin(h, cog.above(), 256));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void sawAutocraftsASelectedStonecuttingRecipe(GameTestHelper h) {
        setupNetwork(h, STATION);
        var saw = AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(SawBlock.FACING, Direction.UP)
                .setValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE, true);
        h.setBlock(STATION, saw);
        Direction drive = AllBlocks.MECHANICAL_SAW.get().getRotationAxis(saw) == Direction.Axis.X ? Direction.EAST : Direction.NORTH;
        h.setBlock(STATION.relative(drive), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, drive.getOpposite()));
        h.runAfterDelay(2, () -> spin(h, STATION.relative(drive), 256));
        h.runAfterDelay(15, () -> {
            var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING).stream()
                    .filter(holder -> holder.value().getResultItem(h.getLevel().registryAccess()).is(Items.STONE_STAIRS)).findFirst().orElseThrow();
            verifyCraft(h, recipe.id());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void spoutAutocraftsUsingItsPlumbedFluid(GameTestHelper h) {
        setupNetwork(h, STATION);
        h.setBlock(STATION, AllBlocks.DEPOT.get());
        h.setBlock(STATION.above(2), AllBlocks.SPOUT.get());
        h.runAfterDelay(5, () -> {
            var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(STATION.above(2)), Direction.UP);
            h.assertTrue(fluid != null && fluid.fill(new FluidStack(Fluids.LAVA, 250), IFluidHandler.FluidAction.EXECUTE) == 250,
                    "Spout did not accept lava");
            verifyCraft(h, ResourceLocation.parse("create:filling/blaze_cake"));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1_200)
    public static void manaMechanismAutocraftsThroughItsSpoutLoops(GameTestHelper h) {
        PhaseThreeAutocraftTests.buildGate(h);
        BlockPos press = new BlockPos(3, 3, 4);
        BlockPos spout = new BlockPos(5, 3, 4);
        h.setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.press.MechanicalPressBlock.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(spout, AllBlocks.SPOUT.get());
        h.runAfterDelay(2, () -> {
            spin(h, PhaseThreeAutocraftTests.T.below(), 256);
            spin(h, PhaseThreeAutocraftTests.BELT_MOTOR, 256);
            spin(h, PhaseThreeAutocraftTests.DEPLOYER_MOTOR, 256);
            var storage = (dev.viridianengineering.content.drive.ArchivePylonBlockEntity) h.getBlockEntity(PhaseThreeAutocraftTests.PYLON);
            storage.mount(VEStorage.TERRA_DRIVE_16K.asStack());
        });
        var remaining = new int[] {1_250};
        h.onEachTick(() -> {
            if (remaining[0] <= 0) return;
            var tank = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(spout), Direction.UP);
            if (tank != null) remaining[0] -= tank.fill(new FluidStack(dev.viridianengineering.registry.VEFluids.LIQUID_MANA.get(),
                    remaining[0]), IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(20, () -> verifyCraft(h, PhaseThreeAutocraftTests.T,
                ViridianEngineering.asResource("sequenced_assembly/mana_mechanism"), () -> {
                    var tank = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(spout), Direction.UP);
                    h.assertTrue(remaining[0] == 0 && tank.getFluidInTank(0).isEmpty(),
                            "Five loops did not consume exactly 1,250 mB of Liquid Mana");
                }));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void mechanicalCrafterAutocraftsAGrid(GameTestHelper h) {
        BlockPos root = STATION, middle = root.above();
        setupNetwork(h, root);
        Map<BlockPos, Direction> faces = Map.of(root, Direction.DOWN, middle, Direction.DOWN,
                middle.above(), Direction.DOWN, middle.west(), Direction.EAST, middle.east(), Direction.WEST);
        for (var entry : faces.entrySet()) {
            var state = AllBlocks.MECHANICAL_CRAFTER.getDefaultState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.SOUTH);
            for (var pointing : net.createmod.catnip.math.Pointing.values()) {
                var candidate = state.setValue(MechanicalCrafterBlock.POINTING, pointing);
                if (MechanicalCrafterBlock.getTargetDirection(candidate) == entry.getValue()) h.setBlock(entry.getKey(), candidate);
            }
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(entry.getKey())).is(AllBlocks.MECHANICAL_CRAFTER.get()),
                    "Failed to place Crafter aimed " + entry.getValue() + " at " + entry.getKey());
        }
        h.onEachTick(() -> {
            for (BlockPos pos : faces.keySet()) if (h.getLevel().getBlockEntity(h.absolutePos(pos)) instanceof MechanicalCrafterBlockEntity crafter)
                crafter.setSpeed(256);
        });
        h.runAfterDelay(15, () -> verifyCraft(h, ViridianEngineering.asResource("mechanical_crafting/gaia_thruster")));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminalCancelTargetsOnlyTheSelectedJob(GameTestHelper h) {
        var level = h.getLevel();
        var terminal = SableCompat.endpointKey(level, h.absolutePos(TERMINAL), Direction.DOWN);
        var other = SableCompat.endpointKey(level, h.absolutePos(STATION), Direction.DOWN);
        var data = dev.viridianengineering.content.autocraft.CraftingJobSavedData.get(level.getServer());
        var target = dev.viridianengineering.content.drive.ItemStackKey.of(new ItemStack(Items.DIAMOND));
        var first = new dev.viridianengineering.content.autocraft.CraftingJob(UUID.randomUUID(), terminal, target, 1);
        var selected = new dev.viridianengineering.content.autocraft.CraftingJob(UUID.randomUUID(), terminal, target, 2);
        var foreign = new dev.viridianengineering.content.autocraft.CraftingJob(UUID.randomUUID(), other, target, 3);
        data.add(first);
        data.add(selected);
        data.add(foreign);
        h.assertTrue(!data.cancelAt(terminal, foreign.id) && !foreign.cancelled, "Terminal cancelled a foreign job");
        h.assertTrue(data.cancelAt(terminal, selected.id) && selected.cancelled && !first.cancelled, "Wrong job cancelled");
        for (var job : List.of(first, selected, foreign))
            job.status = dev.viridianengineering.content.autocraft.CraftingJob.Status.DONE;
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void doubleChestAndBarrelAreWiredSurvivalStorage(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(TERMINAL, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState().setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        BlockPos left = new BlockPos(4, 1, 3), right = left.east();
        var chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        h.setBlock(left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        var port = VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.SOUTH);
        for (int x = 2; x <= 6; x++) h.setBlock(new BlockPos(x, 1, 1), VEBlocks.CORPOREA_PIPE.get());
        h.setBlock(left.north(), port);
        h.setBlock(right.north(), port);
        BlockPos barrel = right.east();
        h.setBlock(barrel, Blocks.BARREL);
        h.setBlock(barrel.north(), port);
        h.runAfterDelay(4, () -> {
            var a = (net.minecraft.world.Container) h.getBlockEntity(left);
            var b = (net.minecraft.world.Container) h.getBlockEntity(right);
            var c = (net.minecraft.world.Container) h.getBlockEntity(barrel);
            a.setItem(0, new ItemStack(Items.DIAMOND, 11));
            b.setItem(0, new ItemStack(Items.DIAMOND, 17));
            c.setItem(0, new ItemStack(Items.DIAMOND, 23));
            var stock = CorporeaNetworkManager.browse(level, h.absolutePos(TERMINAL),
                    CorporeaNetworkManager.Access.MANUAL_WIRED, new TransactionContext());
            h.assertTrue(stock.stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 51,
                    "Double chest or barrel was missing or counted twice: " + stock);
            var taken = CorporeaNetworkManager.withdraw(level, h.absolutePos(TERMINAL), new ItemStack(Items.DIAMOND), 51,
                    CorporeaNetworkManager.Access.MANUAL_WIRED, new TransactionContext());
            h.assertTrue(taken.getCount() == 51 && a.isEmpty() && b.isEmpty() && c.isEmpty(), "Wired storage did not withdraw exactly once");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "viridian_release_relay")
    public static void relaySparkAttachesToPylonAndConservesHeldMana(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, VEStorage.ARCHIVE_PYLON.get());
        h.assertTrue(ArcaneSparkEntity.supports(level, h.absolutePos(pos)), "Archive Pylon cannot hold its Relay Spark");
        var spark = VECorporea.SPARK_ENTITY.get().create(level);
        spark.attach(h.absolutePos(pos));
        level.addFreshEntity(spark);
        var tablet = new ItemStack(BotaniaItems.manaTablet);
        var storage = tablet.getCapability(BotaniaCapabilities.MANA_ITEM);
        storage.addMana(12_345);
        h.runAfterDelay(4, () -> {
            h.assertTrue(ArcaneSparkEntity.at(level, h.absolutePos(pos)) == spark, "Pylon spark detached");
            h.assertTrue(spark.chargeFrom(tablet) == 10_000 && spark.getCurrentMana() == 10_000
                    && storage.getMana() == 2_345 && spark.range() == 48.0, "Held Tablet mana was lost or duplicated");
            spark.discard();
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void bootThrustersLimitAirBurstsAndResetOnLanding(GameTestHelper h) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "[VE Air Bursts]"));
        var boots = VEGear.SYLVAN_BOOTS.asStack();
        var chest = VEGear.SYLVAN_CHESTPLATE.asStack();
        SylvanArmorItem.fit(boots, GearData.Module.BOOT_THRUSTERS);
        SylvanArmorItem.fit(chest, GearData.Module.BRACER_TANKS);
        chest.set(AllDataComponents.BACKTANK_AIR, 100);
        p.setItemSlot(EquipmentSlot.CHEST, chest);
        p.setItemSlot(EquipmentSlot.FEET, boots);
        p.setOnGround(false);
        h.runAfterDelay(2, () -> h.assertTrue(SylvanEvents.deckDash(p, 1, 0, true), "First air burst failed"));
        h.runAfterDelay(11, () -> h.assertTrue(!SylvanEvents.deckDash(p, 1, 0, false), "Burst accepted after nine ticks"));
        h.runAfterDelay(12, () -> h.assertTrue(SylvanEvents.deckDash(p, 1, 0, false), "Second burst refused after ten ticks"));
        h.runAfterDelay(22, () -> {
            h.assertTrue(!SylvanEvents.deckDash(p, 1, 0, true), "Third air burst bypassed the cap");
            p.setOnGround(true);
            SylvanEvents.serverTick(p, h.getLevel().getGameTime());
            p.setOnGround(false);
            h.assertTrue(SylvanEvents.deckDash(p, 1, 0, true), "Landing did not reset the air burst count");
            h.assertTrue(com.simibubi.create.content.equipment.armor.BacktankUtil.getAir(chest) == 25,
                    "Refused bursts used air or three successful bursts did not cost 75 air");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = "viridian_release_pylon")
    public static void pylonFollowsAnalogRedstone(GameTestHelper h) {
        var site = new PhaseFiveGameTests.Site(h);
        var ship = PhaseFiveGameTests.assemble(site, PhaseFiveGameTests.deckWithPylon(site, 10_000));
        var pylon = PhaseFiveGameTests.be(site, ship, new BlockPos(5, 3, 5), LevitationPylonBlockEntity.class);
        int[] strengths = {0, 1, 7, 8, 15, 0};
        var modes = new LevitationPylonBlockEntity.Mode[] {
                LevitationPylonBlockEntity.Mode.HOVER, LevitationPylonBlockEntity.Mode.CLIMB,
                LevitationPylonBlockEntity.Mode.CLIMB, LevitationPylonBlockEntity.Mode.DESCEND,
                LevitationPylonBlockEntity.Mode.DESCEND, LevitationPylonBlockEntity.Mode.HOVER};
        for (int i = 0; i < strengths.length; i++) {
            int strength = strengths[i];
            var expected = modes[i];
            int tick = 12 + i * 6;
            h.runAfterDelay(tick, () -> PhaseFiveGameTests.setPylonSignal(site, ship, strength));
            h.runAfterDelay(tick + 4, () -> {
                h.assertTrue(pylon.signal() == strength, "Expected redstone " + strength + ", was " + pylon.signal());
                h.assertTrue(pylon.mode() == expected, "Signal " + strength + " produced " + pylon.mode());
            });
        }
        h.runAfterDelay(50, () -> {
            PhaseFiveGameTests.finish(site, ship);
            h.succeed();
        });
    }
}
