package dev.viridianengineering.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.battery.*;
import dev.viridianengineering.content.cannon.GaiaCannonBlockEntity;
import dev.viridianengineering.content.elementium.RequestInterfaceBlockEntity;
import dev.viridianengineering.content.elementium.SmartFunnelBlockEntity;
import dev.viridianengineering.content.elementium.SmartTunnelBlockEntity;
import dev.viridianengineering.content.reactor.ReactorControllerBlockEntity;
import dev.viridianengineering.content.reactor.ReactorShaftPortBlockEntity;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;
import java.util.UUID;

/**
 * Phase 4 gate: Elementium logistics, the Mana Reactor's spec rates, the Mana Battery's LOCKED
 * detonation engine (0% safe, 100% always, no drops, survives a restart, radius-200 blast without
 * stalling the server) and the Gaia Arcane Cannon (beam timings; the full charged shot leaves a
 * Protection II Netherite target at ~2.3 hearts).
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseFourGameTests {

    private static final String EMPTY = "empty_12x6x12";

    // ====================================================================== elementium

    /** Funnel moves only requested items, as many as requested; the tunnel diverts requested items and passes the rest. */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "viridian_elementium")
    public static void elementiumFollowsRequests(GameTestHelper h) {
        BlockPos src = new BlockPos(2, 1, 2), funnel = new BlockPos(3, 1, 2), dst = new BlockPos(4, 1, 2);
        BlockPos iface = new BlockPos(8, 1, 8);
        BlockPos tunnel = new BlockPos(2, 1, 8), straight = new BlockPos(3, 1, 8), diverted = new BlockPos(2, 1, 7);
        h.setBlock(src, Blocks.CHEST);
        h.setBlock(dst, Blocks.CHEST);
        h.setBlock(straight, Blocks.CHEST);
        h.setBlock(diverted, Blocks.CHEST);
        h.setBlock(funnel, VEBlocks.ELEMENTIUM_SMART_FUNNEL.getDefaultState().setValue(DirectionalBlock.FACING, Direction.EAST));
        h.setBlock(tunnel, VEBlocks.ELEMENTIUM_SMART_TUNNEL.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        h.setBlock(iface, VEBlocks.ELEMENTIUM_REQUEST_INTERFACE.getDefaultState());

        IItemHandler source = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(src), null);
        ItemHandlerHelper.insertItem(source, new ItemStack(Items.IRON_INGOT, 20), false);
        ItemHandlerHelper.insertItem(source, new ItemStack(Items.GOLD_INGOT, 20), false);
        SmartFunnelBlockEntity f = (SmartFunnelBlockEntity) h.getBlockEntity(funnel);
        SmartTunnelBlockEntity t = (SmartTunnelBlockEntity) h.getBlockEntity(tunnel);
        RequestInterfaceBlockEntity r = (RequestInterfaceBlockEntity) h.getBlockEntity(iface);
        f.mana.set(10_000);
        t.mana.set(10_000);
        r.mana.set(10_000);
        r.addRequest(new ItemStack(Items.GOLD_INGOT), 5);
        r.addRequest(new ItemStack(Items.DIAMOND), 2);

        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(count(h, dst, Items.GOLD_INGOT) == 5, "Funnel moved " + count(h, dst, Items.GOLD_INGOT) + " gold, not the 5 requested");
                    h.assertTrue(count(h, dst, Items.IRON_INGOT) == 0, "Funnel moved unrequested iron while requests were open");
                    h.assertTrue(f.mana.getStoredMana() == 10_000 - 5L * VEConfig.ELEMENTIUM_MANA_PER_ITEM.get(), "Funnel mana use wrong: " + f.mana.getStoredMana());
                    h.assertTrue(r.outstanding(new ItemStack(Items.GOLD_INGOT)) == 0, "Gold request not counted down");
                    // tunnel: 3 diamonds and 4 stone come in from the back
                    IItemHandler in = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(tunnel), Direction.WEST);
                    h.assertTrue(in != null, "Tunnel exposes no input on its back");
                    h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(tunnel), Direction.EAST) == null,
                            "Tunnel accepts items through its output");
                    h.assertTrue(in.insertItem(0, new ItemStack(Items.DIAMOND, 3), false).isEmpty(), "Tunnel refused diamonds");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    IItemHandler in = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(tunnel), Direction.WEST);
                    h.assertTrue(in.insertItem(0, new ItemStack(Items.STONE, 4), false).isEmpty(), "Tunnel refused stone");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    h.assertTrue(count(h, diverted, Items.DIAMOND) == 2, "Tunnel diverted " + count(h, diverted, Items.DIAMOND) + " diamonds, not 2");
                    h.assertTrue(count(h, straight, Items.DIAMOND) == 1, "Unrequested 3rd diamond did not pass straight through");
                    h.assertTrue(count(h, straight, Items.STONE) == 4 && count(h, diverted, Items.STONE) == 0, "Stone was not passed straight through");
                    h.assertFalse(r.hasRequests(), "Requests still open: " + r.requests());
                    // with no open requests (and no filter) the funnel moves everything
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    h.assertTrue(count(h, dst, Items.IRON_INGOT) == 20 && count(h, dst, Items.GOLD_INGOT) == 20,
                            "With no requests the funnel did not move the rest");
                    long manaBefore = f.mana.getStoredMana();
                    f.mana.set(0);
                    ItemHandlerHelper.insertItem(source, new ItemStack(Items.COAL, 8), false);
                    f.transfer();
                    h.assertTrue(count(h, dst, Items.COAL) == 0, "Funnel moved items with no mana");
                    f.mana.set(manaBefore);
                })
                .thenSucceed();
    }

    private static int count(GameTestHelper h, BlockPos pos, net.minecraft.world.item.Item item) {
        IItemHandler inv = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), null);
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(item)) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    // ====================================================================== reactor

    static final BlockPos CORE = new BlockPos(7, 2, 6);
    static final BlockPos CTRL = CORE.offset(0, 0, -3), LAVA = CORE.offset(-3, -1, 0), MANA = CORE.offset(3, 0, 0),
            SHAFT = CORE.offset(0, -1, 3);

    /**
     * Builds the 7x7 bowl around {@link #CORE}: outer wall (2 high) with the controller in the middle of
     * the north side, a lava port low on the west, a mana port high on the east and a shaft port low on
     * the south (motor behind it); the inner 5x5 ring, the pedestal and the core.
     */
    private static void buildReactor(GameTestHelper h, int rpm) {
        BlockPos c = CORE;
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    var slot = ReactorControllerBlockEntity.slotAt(dx, dy, dz);
                    if (slot == ReactorControllerBlockEntity.Slot.OUTER || slot == ReactorControllerBlockEntity.Slot.INNER
                            || slot == ReactorControllerBlockEntity.Slot.PEDESTAL)
                        h.setBlock(c.offset(dx, dy, dz), VEBlocks.TERRASTEEL_REACTOR_CASING.getDefaultState());
                }
        h.setBlock(c, VEBlocks.TERRASTEEL_MANA_REACTOR_CORE.getDefaultState());
        h.setBlock(LAVA, VEBlocks.TERRASTEEL_LAVA_PORT.getDefaultState());
        h.setBlock(MANA, VEBlocks.TERRASTEEL_MANA_PORT.getDefaultState());
        h.setBlock(SHAFT, VEBlocks.TERRASTEEL_SHAFT_PORT.getDefaultState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        h.setBlock(SHAFT.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        h.setBlock(CTRL, VEBlocks.TERRASTEEL_MANA_REACTOR_CONTROLLER.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        if (h.getBlockEntity(SHAFT.south()) instanceof CreativeMotorBlockEntity m) m.generatedSpeed.setValue(rpm);
    }

    private static boolean formed(GameTestHelper h, BlockPos rel) {
        BlockState s = h.getLevel().getBlockState(h.absolutePos(rel));
        return s.hasProperty(dev.viridianengineering.content.FormedPartBlock.FORMED) && s.getValue(dev.viridianengineering.content.FormedPartBlock.FORMED);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void reactorRunsAtSpecRates(GameTestHelper h) {
        buildReactor(h, 256);
        long[] start = new long[3];
        h.startSequence()
                .thenWaitUntil(() -> {
                    ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) h.getBlockEntity(CTRL);
                    h.assertTrue(r.isFormed(), "Reactor did not form");
                    h.assertTrue(formed(h, CORE.offset(-3, 0, -3)) && formed(h, CORE.offset(2, -1, 0)) && formed(h, CORE.below())
                            && formed(h, CORE), "Formed casings/core did not switch to the formed model");
                    IFluidHandler lava = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(LAVA), Direction.WEST);
                    if (r.lava.getFluidAmount() == 0) h.assertTrue(lava.fill(new FluidStack(Fluids.LAVA, 4_000), IFluidHandler.FluidAction.EXECUTE) == 4_000, "Lava port refused lava");
                    h.assertTrue(lava.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) == 0, "Lava port accepted water");
                    h.assertTrue(r.isRunning(), "Reactor not running at 256 RPM with lava");
                })
                .thenExecute(() -> {
                    ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) h.getBlockEntity(CTRL);
                    start[0] = r.lava.getFluidAmount();
                    start[1] = r.mana.getStoredMana();
                    start[2] = h.getTick();
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) h.getBlockEntity(CTRL);
                    long ticks = h.getTick() - start[2];
                    long burned = start[0] - r.lava.getFluidAmount(), made = r.mana.getStoredMana() - start[1];
                    // 256 RPM: 5 mB/t and 125 mana/t
                    h.assertTrue(Math.abs(burned - 5 * ticks) <= 5, "Burned " + burned + " mB in " + ticks + " ticks, expected " + 5 * ticks);
                    h.assertTrue(Math.abs(made - 125 * ticks) <= 125, "Made " + made + " mana in " + ticks + " ticks, expected " + 125 * ticks);
                    ReactorShaftPortBlockEntity port = (ReactorShaftPortBlockEntity) h.getBlockEntity(SHAFT);
                    float su = port.calculateStressApplied() * Math.abs(port.getSpeed());
                    h.assertTrue(Math.abs(su - 16 * 256) < 0.5f, "Shaft draws " + su + " SU, expected 4096");
                    h.assertTrue(Math.abs(r.manaRate() - 125f) < 0.01f && Math.abs(r.lavaRate() - 5f) < 0.01f, "Goggle rates wrong");
                    var out = h.getLevel().getCapability(dev.viridianengineering.registry.ModCapabilities.MANA_PIPE_NODE,
                            h.absolutePos(MANA), Direction.EAST);
                    long have = r.mana.getStoredMana();
                    h.assertTrue(out.extractMana(1_000, false) == 1_000 && r.mana.getStoredMana() == have - 1_000, "Mana port did not drain the buffer");
                    h.assertTrue(out.insertMana(1_000, false) == 0, "Reactor accepted mana");
                    // break a wall casing: the structure unforms at once and the casings show again
                    h.setBlock(CORE.offset(-3, 0, -3), Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) h.getBlockEntity(CTRL);
                    h.assertFalse(r.isFormed(), "Reactor still formed with a hole in its wall");
                    h.assertFalse(formed(h, CORE.offset(2, -1, 0)) || formed(h, CORE), "Casings stayed invisible after unforming");
                    ReactorShaftPortBlockEntity port = (ReactorShaftPortBlockEntity) h.getBlockEntity(SHAFT);
                    h.assertTrue(port.calculateStressApplied() == 0, "Unformed reactor still draws stress");
                })
                .thenSucceed();
    }

    /** In-game setup: Create fluid tank -> pipe -> Mechanical Pump -> Manasteel pipe -> lava port in the wall. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void reactorTakesPumpedLava(GameTestHelper h) {
        buildReactor(h, 256);
        BlockPos tank = new BlockPos(0, 1, 6), pump = new BlockPos(2, 1, 6);
        h.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState());
        List<BlockPos> pipes = List.of(new BlockPos(1, 1, 6), new BlockPos(3, 1, 6));
        for (BlockPos p : pipes) h.setBlock(p, VEBlocks.MANASTEEL_FLUID_PIPE.get());
        h.setBlock(pump, VEBlocks.MANASTEEL_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING, Direction.EAST));
        h.setBlock(pump.north(), AllBlocks.COGWHEEL.getDefaultState().setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS, Direction.Axis.X));
        h.setBlock(pump.north().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        for (BlockPos rel : pipes) {
            BlockPos abs = h.absolutePos(rel);
            BlockState st = h.getLevel().getBlockState(abs);
            if (st.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock pipe)
                h.getLevel().setBlock(abs, pipe.updateBlockState(st, Direction.EAST, null, h.getLevel(), abs), 3);
        }
        h.runAfterDelay(2, () -> {
            if (h.getBlockEntity(pump.north().west()) instanceof CreativeMotorBlockEntity m) m.generatedSpeed.setValue(128);
            h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(tank), null)
                    .fill(new FluidStack(Fluids.LAVA, 8_000), IFluidHandler.FluidAction.EXECUTE);
        });
        h.succeedWhen(() -> {
            ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) h.getBlockEntity(CTRL);
            h.assertTrue(r.isFormed(), "not formed");
            h.assertTrue(r.mana.getStoredMana() > 0, "No mana yet; reactor lava " + r.lava.getFluidAmount() + " mB");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void reactorIdlesBelowMinimumRpm(GameTestHelper h) {
        buildReactor(h, 48);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((ReactorControllerBlockEntity) h.getBlockEntity(CTRL)).isFormed(), "not formed"))
                .thenExecute(() -> ((ReactorControllerBlockEntity) h.getBlockEntity(CTRL)).lava.fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE))
                .thenIdle(60)
                .thenExecute(() -> {
                    ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) h.getBlockEntity(CTRL);
                    h.assertTrue(r.lava.getFluidAmount() == 1_000 && r.mana.getStoredMana() == 0, "Reactor ran at 48 RPM");
                })
                .thenSucceed();
    }

    // ====================================================================== battery

    static final BlockPos BATTERY = new BlockPos(2, 1, 1);

    /** Controller at (2,1,1) facing north; base centre (2,1,2); {@code layers} full layers. */
    static BatteryControllerBlockEntity buildBattery(ServerLevel level, BlockPos controllerAbs, int layers) {
        BlockPos c = controllerAbs.south();
        for (int y = 0; y < layers; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    level.setBlock(c.offset(dx, y, dz), VEBlocks.TERRASTEEL_MANA_BATTERY_CASING.getDefaultState(), 3);
        level.setBlock(controllerAbs, VEBlocks.TERRASTEEL_MANA_BATTERY_CONTROLLER.getDefaultState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        BatteryControllerBlockEntity be = (BatteryControllerBlockEntity) level.getBlockEntity(controllerAbs);
        be.validate();
        return be;
    }

    private static FakePlayer miner(ServerLevel level) {
        FakePlayer p = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5e0d4c1a-0000-4000-8000-0000000000b4"), "[VE battery]"));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "viridian_battery")
    public static void batteryLayersCapacityAndIo(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BatteryControllerBlockEntity b = buildBattery(level, h.absolutePos(BATTERY), 3);
        h.assertTrue(b.validLayers() == 3 && b.isFormed(), "3 layers counted as " + b.validLayers());
        h.assertTrue(b.maxCapacity() == 7_500_000L, "Capacity " + b.maxCapacity());
        // the port is the only I/O face: 1,000/t passive
        level.setBlock(h.absolutePos(new BlockPos(3, 2, 2)), VEBlocks.TERRASTEEL_MANA_BATTERY_PORT.getDefaultState(), 3);
        b.validate();
        h.assertTrue(b.validLayers() == 3 && b.ports().size() == 1, "Port not counted as part of the battery");
        h.assertTrue(level.getCapability(dev.viridianengineering.registry.ModCapabilities.MANA_PIPE_NODE, h.absolutePos(new BlockPos(3, 3, 3)), Direction.UP) == null,
                "A plain casing still does I/O");
        var node = level.getCapability(dev.viridianengineering.registry.ModCapabilities.MANA_PIPE_NODE, h.absolutePos(new BlockPos(3, 2, 2)), Direction.EAST);
        h.assertTrue(node.insertMana(5_000, false) == 1_000, "Passive I/O not capped at 1,000/t");
        h.assertTrue(node.insertMana(5_000, false) == 0, "I/O cap not per tick");
        // a gap in layer 2 cuts it to the base only (unformed), clamping mana
        b.mana.set(7_000_000);
        level.setBlock(h.absolutePos(new BlockPos(1, 2, 2)), Blocks.AIR.defaultBlockState(), 3);
        b.validate();
        h.assertTrue(b.validLayers() == 1 && !b.isFormed(), "Layers after a gap: " + b.validLayers());
        h.assertTrue(b.mana.getStoredMana() == 2_500_000L, "Mana not clamped to the remaining capacity: " + b.mana.getStoredMana());
        h.assertTrue(node.getManaCapacity() == 0, "Unformed battery still does I/O");
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "viridian_battery")
    public static void batteryEmptyBreaksSafely(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        RandomSource rnd = RandomSource.create(42);
        for (int i = 0; i < 10_000; i++) {
            h.assertFalse(BatteryDetonation.roll(0.0, rnd), "0% charge rolled a detonation");
            h.assertTrue(BatteryDetonation.roll(1.0, rnd), "100% charge failed to roll a detonation");
        }
        BatteryControllerBlockEntity b = buildBattery(level, h.absolutePos(BATTERY), 2);
        BlockPos casing = h.absolutePos(new BlockPos(3, 2, 3));
        FakePlayer miner = miner(level);
        BlockState before = level.getBlockState(casing);
        var expected = net.minecraft.world.level.block.Block.getDrops(before, level, casing, null, miner, miner.getMainHandItem());
        boolean harvest = before.canHarvestBlock(level, casing, miner);
        boolean broken = miner.gameMode.destroyBlock(casing);
        h.assertTrue(broken && level.getBlockState(casing).isAir(), "Empty battery block was not broken");
        h.assertFalse(b.isUnstable(), "Empty battery turned unstable");
        h.assertTrue(b.validLayers() == 0 || !b.isFormed(), "Battery still formed after the safe break");
        h.assertTrue(BatteryFailureSavedData.get(level).entries().stream().noneMatch(e -> e.controllerPos().equals(b.getBlockPos())),
                "A failure entry was registered for a safe break");
        h.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(casing).inflate(2),
                e -> e.getItem().is(VEBlocks.TERRASTEEL_MANA_BATTERY_CASING.asItem())).isEmpty(), "Safe break dropped nothing (loot "
                + expected + ", harvestable " + harvest + ", tool " + miner.getMainHandItem() + ", mode " + miner.gameMode.getGameModeForPlayer()
                + ", items nearby " + level.getEntitiesOfClass(ItemEntity.class, new AABB(casing).inflate(8)).size() + ")");
        h.succeed();
    }

    /** 100% always fails, with no drops, holds across a simulated restart and detonates on time. */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "viridian_battery")
    public static void batteryFullAlwaysDetonates(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        boolean terrain = VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.get();
        VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.set(false); // keep the shared test world intact
        BatteryControllerBlockEntity b = buildBattery(level, h.absolutePos(BATTERY), 2);
        b.mana.set(b.maxCapacity());
        BlockPos casing = h.absolutePos(new BlockPos(3, 2, 3));
        List<BlockPos> members = b.members();
        Zombie bystander = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(9, 1, 9));
        bystander.setNoGravity(true);
        bystander.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no sunburn

        boolean broken = miner(level).gameMode.destroyBlock(casing);
        h.assertFalse(broken, "Break of a full battery was not cancelled");
        h.assertTrue(level.getBlockState(casing).is(VEBlocks.TERRASTEEL_MANA_BATTERY_CASING.get()), "Block vanished");
        h.assertTrue(b.isUnstable(), "Full battery did not turn unstable");
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(casing).inflate(3)).isEmpty(), "Unstable path dropped items");
        BatteryFailureEntry entry = BatteryFailureSavedData.get(level).entries().stream()
                .filter(e -> e.eventId().equals(b.failureId())).findFirst().orElse(null);
        h.assertTrue(entry != null, "No BatteryFailureEntry registered");
        h.assertTrue(entry.detonationGameTick() == level.getGameTime() + 200 && entry.validLayers() == 2
                && Math.abs(entry.fillFraction() - 1.0) < 1e-9, "Entry fields wrong: " + entry);
        // further breaks and I/O are refused
        h.assertFalse(miner(level).gameMode.destroyBlock(h.absolutePos(new BlockPos(1, 1, 1))), "Unstable battery could be broken again");
        h.assertTrue(b.mana.extractMana(500, false) == 0, "Unstable battery still does I/O");

        long deadline = entry.detonationGameTick();
        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    // ---- simulated server restart mid-countdown: SavedData and the controller reload from NBT ----
                    var registries = level.registryAccess();
                    CompoundTag saved = BatteryFailureSavedData.get(level).save(new CompoundTag(), registries);
                    level.getDataStorage().set(BatteryFailureSavedData.ID, BatteryFailureSavedData.load(saved, registries));
                    BatteryControllerBlockEntity ctl = (BatteryControllerBlockEntity) level.getBlockEntity(h.absolutePos(BATTERY));
                    CompoundTag beTag = ctl.saveWithFullMetadata(registries);
                    ctl.loadWithComponents(beTag, registries);
                    var again = BatteryFailureSavedData.get(level).entry(entry.eventId());
                    h.assertTrue(again.isPresent() && again.get().equals(entry), "Entry changed or was lost across the restart");
                    h.assertTrue(ctl.isUnstable() && ctl.detonationTick() == deadline, "Controller forgot its failure across the restart");
                })
                .thenWaitUntil(() -> h.assertTrue(level.getGameTime() > deadline, "waiting for the deadline"))
                .thenIdle(2)
                .thenExecute(() -> {
                    VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.set(terrain);
                    for (BlockPos p : members)
                        h.assertTrue(level.getBlockState(p).isAir(), "Battery block survived the detonation at " + p);
                    h.assertTrue(BatteryFailureSavedData.get(level).entry(entry.eventId()).isEmpty(), "Entry not cleared after detonation");
                    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BATTERY)).inflate(6)).isEmpty(),
                            "Detonation dropped items");
                    // radius 200 at 100%: the bystander ~11 blocks away is hurt
                    h.assertTrue(BatteryDetonation.radiusFor(1.0) == 200 && BatteryDetonation.radiusFor(0.0) == 16, "Radius formula wrong");
                    h.assertTrue(!bystander.isAlive() || bystander.getHealth() < bystander.getMaxHealth(), "Bystander took no blast damage");
                })
                .thenSucceed();
    }

    /** The full detonation path at 100% with terrain damage, in a force-loaded Nether area: radius 200, budgeted, no stall. */
    @GameTest(template = EMPTY, timeoutTicks = 2_400, batch = "viridian_zz_battery_blast")
    public static void batteryBlastRadius200WithoutStall(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether");
        // fresh, never-generated terrain every run (an earlier run's blast leaves its spot empty)
        java.util.Random rnd = new java.util.Random();
        BlockPos ctrl = new BlockPos(200_000 + rnd.nextInt(2_000) * 512, 64, 200_000 + rnd.nextInt(2_000) * 512);
        ChunkPos cc = new ChunkPos(ctrl);
        int r = 6; // 13x13 chunks force-loaded (+-104 blocks), the rest of the sphere is unloaded and skipped
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) nether.setChunkForced(cc.x + x, cc.z + z, true);
        boolean terrain = VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.get();
        UUID[] id = new UUID[1];
        ShellExplosionTask[] task = new ShellExplosionTask[1];
        BlockPos[] probes = {ctrl.offset(40, 0, 0), ctrl.offset(0, 0, -90), ctrl.offset(60, -50, 60)};

        h.startSequence()
                .thenWaitUntil(() -> {
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                        h.assertTrue(nether.getChunkSource().getChunkNow(cc.x + x, cc.z + z) != null, "loading chunks");
                })
                .thenExecute(() -> {
                    VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.set(true);
                    for (BlockPos p : probes) nether.setBlock(p, Blocks.NETHERRACK.defaultBlockState(), 3);
                    BatteryControllerBlockEntity b = buildBattery(nether, ctrl, 8);
                    h.assertTrue(b.validLayers() == 8 && b.maxCapacity() == 20_000_000L, "8-layer battery: " + b.validLayers());
                    b.mana.set(b.maxCapacity());
                    h.assertTrue(BatteryDetonation.handleBreak(nether, ctrl.south().above(), RandomSource.create()) == BatteryDetonation.Outcome.TRIGGERED,
                            "Full battery did not trigger");
                    BatteryFailureEntry e = BatteryFailureSavedData.get(nether).entry(b.failureId()).orElseThrow();
                    id[0] = e.eventId();
                    // skip the 200-tick wait (covered by batteryFullAlwaysDetonates): detonate now, then let the level tick the blast
                    BatteryFailureSavedData.get(nether).remove(e.eventId());
                    BatteryDetonation.detonate(nether, e, ctrl);
                    ShellExplosionTask t = BatteryDetonation.blastFor(nether, e.eventId());
                    h.assertTrue(t != null && t.radius == 200, "Blast task missing or wrong radius");
                    task[0] = t;
                })
                .thenWaitUntil(() -> h.assertTrue(BatteryDetonation.blastFor(nether, id[0]) == null, "blast running"))
                .thenExecute(() -> {
                    VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.set(terrain);
                    for (BlockPos p : probes) h.assertTrue(nether.getBlockState(p).isAir(), "Block inside the radius survived at " + p);
                    h.assertTrue(nether.getBlockState(ctrl).isAir(), "Controller survived");
                    ShellExplosionTask t = task[0];
                    ViridianEngineering.LOGGER.info("[VE gate] radius-200 blast: {} blocks removed, slowest step {} ms", t.removedTotal(), t.worstStepNanos() / 1_000_000);
                    h.assertTrue(t.removedTotal() > 1_000_000, "Blast removed only " + t.removedTotal() + " blocks: terrain was not there?");
                    h.assertTrue(t.worstStepNanos() < 2_000_000_000L, "Slowest blast step " + t.worstStepNanos() / 1_000_000 + " ms");
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) nether.setChunkForced(cc.x + x, cc.z + z, false);
                })
                .thenSucceed();
    }

    // ====================================================================== cannon

    private static ItemStack prot2(ServerLevel level, net.minecraft.world.item.Item item) {
        ItemStack s = new ItemStack(item);
        Holder<Enchantment> prot = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        s.enchant(prot, 2);
        return s;
    }

    private static void armour(ServerLevel level, LivingEntity e) {
        e.setItemSlot(EquipmentSlot.HEAD, prot2(level, Items.NETHERITE_HELMET));
        e.setItemSlot(EquipmentSlot.CHEST, prot2(level, Items.NETHERITE_CHESTPLATE));
        e.setItemSlot(EquipmentSlot.LEGS, prot2(level, Items.NETHERITE_LEGGINGS));
        e.setItemSlot(EquipmentSlot.FEET, prot2(level, Items.NETHERITE_BOOTS));
    }

    /** Spec gate: a full charge (42.0) leaves a Protection II Netherite target on 20 - 15.3 = 4.7 HP (~2.3 hearts). */
    @GameTest(template = EMPTY, timeoutTicks = 300, batch = "viridian_cannon")
    public static void cannonChargedShotGate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos cannon = new BlockPos(1, 2, 5);
        h.setBlock(cannon, VEBlocks.GAIA_ARCANE_CANNON.getDefaultState().setValue(DirectionalBlock.FACING, Direction.EAST));
        GaiaCannonBlockEntity be = (GaiaCannonBlockEntity) h.getBlockEntity(cannon);
        be.setMode(GaiaCannonBlockEntity.Mode.PROJECTILE);
        be.mana.set(50_000);
        for (int x = 0; x < 12; x++) h.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);

        Zombie target = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, 2, 5));
        target.setBaby(false);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0); // only the netherite counts, like a player
        armour(level, target);
        target.setHealth(20f);

        // Same hit on a player (FakePlayers are normally invulnerable; this one is not).
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[VE target]")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        armour(level, player);
        try { // a fresh ServerPlayer ignores damage for its first 60 ticks
            var f = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(player, 0);
        } catch (ReflectiveOperationException e) { h.fail("spawnInvulnerableTime: " + e); }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            player.getItemBySlot(slot).forEachModifier(slot, (attr, mod) -> player.getAttribute(attr).addOrReplacePermanentModifier(mod));
        player.setHealth(20f);
        h.assertTrue(Math.abs(GaiaCannonBlockEntity.damageFor(1f) - 42f) < 1e-4, "Full-charge damage is not 42");
        h.assertTrue(Math.abs(GaiaCannonBlockEntity.damageFor(0.625f) - 20f) < 1e-4, "Mid-charge formula wrong");
        h.assertTrue(GaiaCannonBlockEntity.damageFor(0.2f) < 0, "Charge below 25% did not vent");
        player.hurt(VEDamageTypes.source(level, VEDamageTypes.ARCANE_BOLT, null, null), 42f);
        float playerLeft = player.getHealth();
        h.assertTrue(Math.abs(playerLeft - 4.69f) < 0.06f, "Protection II Netherite player has " + playerLeft + " HP, expected ~4.69 (2.3 hearts)");

        BlockPos power = be.redstoneInput(); // absolute; only the redstone port reads signals
        h.startSequence()
                .thenExecute(() -> level.setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(70)
                .thenExecute(() -> {
                    h.assertTrue(be.charge() >= 1f, "Cannon not fully charged after 70 ticks: " + be.charge());
                    h.assertTrue(be.mana.getStoredMana() == 50_000 - 15_000, "Charging used " + (50_000 - be.mana.getStoredMana()) + " mana, not 15,000");
                    level.setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertTrue(target.getHealth() < 20f, "waiting for the bolt"))
                .thenExecute(() -> {
                    float left = target.getHealth();
                    ViridianEngineering.LOGGER.info("[VE gate] charged shot: target {} HP (took {}), player {} HP", left, 20f - left, playerLeft);
                    h.assertTrue(Math.abs(left - 4.69f) < 0.06f, "Target has " + left + " HP after the charged shot, expected ~4.69 (took " + (20 - left) + ")");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "viridian_cannon")
    public static void cannonBeamTimings(GameTestHelper h) {
        BlockPos cannon = new BlockPos(1, 2, 5);
        h.setBlock(cannon, VEBlocks.GAIA_ARCANE_CANNON.getDefaultState().setValue(DirectionalBlock.FACING, Direction.EAST));
        GaiaCannonBlockEntity be = (GaiaCannonBlockEntity) h.getBlockEntity(cannon);
        be.setMode(GaiaCannonBlockEntity.Mode.BEAM);
        be.mana.set(50_000);
        for (int x = 0; x < 12; x++) h.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);
        Zombie target = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(9, 2, 5));
        target.setBaby(false);
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no sunburn
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        target.setHealth(500f); // no knockback resistance: the beam must not push it out of the ray
        h.getLevel().setBlockAndUpdate(be.redstoneInput(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(be.cooldown() > 0, "beam still firing"))
                .thenExecute(() -> {
                    h.assertTrue(be.mana.getStoredMana() == 50_000 - 200L * 200, "Beam used " + (50_000 - be.mana.getStoredMana()) + " mana over its 200 ticks");
                    h.assertTrue(be.beamLength() == 0, "Beam still drawn during cooldown");
                    h.assertTrue(be.cooldown() >= 78, "Cooldown not 80 ticks: " + be.cooldown());
                    float taken = 500f - target.getHealth();
                    // the leather helmet absorbs a little; 20 hits of 7.0 = 140 before armour
                    h.assertTrue(taken > 120f && taken <= 140.01f, "Beam dealt " + taken + " over 200 ticks, expected 20 hits of 7");
                    h.assertTrue(Math.abs(target.getX() - h.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 2, 5.5)).x) < 0.3,
                            "beam knocked the target back to x=" + target.getX());
                    h.assertTrue(target.invulnerableTime == 0, "beam granted invulnerability frames: " + target.invulnerableTime);
                })
                .thenSucceed();
    }

    /**
     * User report: a lever no longer fired the cannon. Places a floor-mounted cannon the way the item
     * does (centre + 26 parts), checks it faces up, that a lever on the redstone-port corner powers it
     * (the beam fires straight up) and that the mana-port corner takes mana from pipes.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "viridian_cannon")
    public static void cannonPortsOnPlacedCannon(GameTestHelper h) {
        BlockPos centre = new BlockPos(5, 2, 5);
        BlockState st = VEBlocks.GAIA_ARCANE_CANNON.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP);
        h.setBlock(centre, st);
        BlockPos abs = h.absolutePos(centre);
        VEBlocks.GAIA_ARCANE_CANNON.get().setPlacedBy(h.getLevel(), abs, st, null, ItemStack.EMPTY);
        for (BlockPos p : dev.viridianengineering.content.cannon.GaiaCannonBlock.parts(abs))
            h.assertTrue(h.getLevel().getBlockState(p).is(VEBlocks.GAIA_ARCANE_CANNON_PART.get()), "missing part at " + p);
        GaiaCannonBlockEntity be = (GaiaCannonBlockEntity) h.getBlockEntity(centre);
        be.setMode(GaiaCannonBlockEntity.Mode.BEAM);
        be.mana.set(50_000);

        // mana port: the (+x, foot, 0) part, east face only (model, facing up)
        var cap = dev.viridianengineering.registry.ModCapabilities.MANA_PIPE_NODE;
        BlockPos manaPort = abs.offset(1, -1, 0);
        h.assertTrue(h.getLevel().getCapability(cap, manaPort, Direction.EAST) != null, "mana port face exposes no mana node");
        h.assertTrue(h.getLevel().getCapability(cap, manaPort, Direction.UP) == null, "mana port takes pipes on its top face");
        h.assertTrue(h.getLevel().getCapability(cap, abs.offset(1, -1, 1), Direction.EAST) == null, "a non-port part takes pipes");
        h.assertTrue(h.getLevel().getCapability(cap, abs.offset(-1, -1, 0), Direction.WEST) == null, "the redstone port takes pipes");

        // a powered lever on any other part does nothing
        BlockPos wrongLever = centre.offset(2, 0, 0);
        h.setBlock(wrongLever, Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true));
        h.runAtTickTime(5, () -> h.assertTrue(be.beamLength() == 0, "a lever on a non-port part fired the cannon"));

        // redstone port: the (-x, foot, 0) part; a powered lever on its west face
        BlockPos lever = centre.offset(-2, -1, 0);
        h.assertTrue(be.redstoneInput().equals(h.absolutePos(lever)), "redstone input at " + be.redstoneInput() + ", lever at " + h.absolutePos(lever));
        h.runAtTickTime(6, () -> h.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true)));
        h.succeedWhen(() -> {
            h.assertTrue(be.beamLength() > 0, "lever on the redstone port did not fire the beam");
            h.assertTrue(be.muzzle().y > abs.getY() + 1.5, "floor-mounted cannon does not fire upwards");
        });
    }

    /** User report: mana from a pool through Terrasteel pipes and a Mana Pump never reached the cannon. */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "viridian_cannon")
    public static void cannonFedByPumpedManaPipes(GameTestHelper h) {
        BlockPos centre = new BlockPos(3, 2, 5);
        BlockState st = VEBlocks.GAIA_ARCANE_CANNON.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP);
        h.setBlock(centre, st);
        BlockPos abs = h.absolutePos(centre);
        VEBlocks.GAIA_ARCANE_CANNON.get().setPlacedBy(h.getLevel(), abs, st, null, ItemStack.EMPTY);
        GaiaCannonBlockEntity be = (GaiaCannonBlockEntity) h.getBlockEntity(centre);

        // pool (9) -> pipes (8, 7) -> Mana Pump (6, pushing west) -> pipe (5) -> mana port (4, 1, 5)
        BlockPos poolPos = new BlockPos(9, 1, 5);
        h.setBlock(poolPos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("botania:mana_pool")).defaultBlockState());
        BlockPos pump = new BlockPos(6, 1, 5);
        h.setBlock(pump, VEBlocks.MANA_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING, Direction.WEST));
        h.setBlock(pump.north(), AllBlocks.COGWHEEL.getDefaultState().setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS, Direction.Axis.X));
        h.setBlock(pump.north().east(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.WEST));
        java.util.List<BlockPos> pipes = java.util.List.of(new BlockPos(5, 1, 5), new BlockPos(7, 1, 5), new BlockPos(8, 1, 5));
        for (BlockPos p : pipes) h.setBlock(p, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        for (BlockPos p : pipes) {
            BlockPos a = h.absolutePos(p);
            h.getLevel().setBlock(a, dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlock.updateConnections(
                    h.getLevel(), a, h.getLevel().getBlockState(a)), 3);
        }
        h.runAfterDelay(2, () -> {
            if (h.getBlockEntity(pump.north().east()) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity m)
                m.generatedSpeed.setValue(128);
            ((velrondevs.botania.api.mana.ManaPool) h.getBlockEntity(poolPos)).receiveMana(500_000);
        });
        h.runAtTickTime(120, () -> {
            BlockState last = h.getBlockState(new BlockPos(5, 1, 5));
            h.assertTrue(last.getValue(net.minecraft.world.level.block.PipeBlock.WEST), "last pipe not connected to the mana port");
            h.assertTrue(be.mana.getStoredMana() > 5_000, "cannon received only " + be.mana.getStoredMana() + " mana");
            h.succeed();
        });
    }

    /** User report: pipes took nothing from Botania's creative pool (it always reads full). */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "viridian_cannon")
    public static void creativePoolFeedsManaPipes(GameTestHelper h) {
        net.minecraft.world.level.block.Block creative = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("botania:creative_pool"));
        h.assertTrue(creative != Blocks.AIR, "botania:creative_pool is not registered");
        BlockPos src = new BlockPos(1, 1, 1), sink = new BlockPos(4, 1, 1);
        h.setBlock(src, creative);
        h.setBlock(sink, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("botania:mana_pool")));
        java.util.List<BlockPos> pipes = java.util.List.of(new BlockPos(2, 1, 1), new BlockPos(3, 1, 1));
        for (BlockPos p : pipes) h.setBlock(p, VEBlocks.TERRASTEEL_MANA_PIPE.get());
        for (BlockPos p : pipes) {
            BlockPos a = h.absolutePos(p);
            h.getLevel().setBlock(a, dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlock.updateConnections(
                    h.getLevel(), a, h.getLevel().getBlockState(a)), 3);
        }
        h.runAtTickTime(60, () -> {
            int got = ((velrondevs.botania.api.mana.ManaPool) h.getBlockEntity(sink)).getCurrentMana();
            h.assertTrue(got > 500, "pool fed from the creative pool holds only " + got);
            h.succeed();
        });
    }

    /** User request: the bowl scales. A 9x9 bowl (radius 4) forms and runs at twice the base rates. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void reactorScalesWithBowlSize(GameTestHelper h) {
        int r = 4;
        BlockPos c = new BlockPos(5, 2, 5);
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    var slot = ReactorControllerBlockEntity.slotAt(dx, dy, dz, r);
                    if (slot == ReactorControllerBlockEntity.Slot.OUTER || slot == ReactorControllerBlockEntity.Slot.INNER
                            || slot == ReactorControllerBlockEntity.Slot.PEDESTAL)
                        h.setBlock(c.offset(dx, dy, dz), VEBlocks.TERRASTEEL_REACTOR_CASING.getDefaultState());
                }
        h.setBlock(c, VEBlocks.TERRASTEEL_MANA_REACTOR_CORE.getDefaultState());
        BlockPos lavaPort = c.offset(-r, -1, 0), manaPort = c.offset(r, 0, 0), shaft = c.offset(0, -1, r), ctrl = c.offset(0, 0, -r);
        h.setBlock(lavaPort, VEBlocks.TERRASTEEL_LAVA_PORT.getDefaultState());
        h.setBlock(manaPort, VEBlocks.TERRASTEEL_MANA_PORT.getDefaultState());
        h.setBlock(shaft, VEBlocks.TERRASTEEL_SHAFT_PORT.getDefaultState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        h.setBlock(shaft.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        h.setBlock(ctrl, VEBlocks.TERRASTEEL_MANA_REACTOR_CONTROLLER.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        if (h.getBlockEntity(shaft.south()) instanceof CreativeMotorBlockEntity m) m.generatedSpeed.setValue(256);
        long[] start = new long[3];
        h.startSequence()
                .thenWaitUntil(() -> {
                    ReactorControllerBlockEntity rc = (ReactorControllerBlockEntity) h.getBlockEntity(ctrl);
                    h.assertTrue(rc.isFormed() && rc.radius() == 4 && rc.factor() == 2, "9x9 reactor did not form (radius " + rc.radius() + ")");
                    if (rc.lava.getFluidAmount() == 0)
                        h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(lavaPort), Direction.WEST)
                                .fill(new FluidStack(Fluids.LAVA, 8_000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(rc.isRunning(), "9x9 reactor not running");
                })
                .thenExecute(() -> {
                    ReactorControllerBlockEntity rc = (ReactorControllerBlockEntity) h.getBlockEntity(ctrl);
                    start[0] = rc.lava.getFluidAmount();
                    start[1] = rc.mana.getStoredMana();
                    start[2] = h.getTick();
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    ReactorControllerBlockEntity rc = (ReactorControllerBlockEntity) h.getBlockEntity(ctrl);
                    long ticks = h.getTick() - start[2];
                    long burned = start[0] - rc.lava.getFluidAmount(), made = rc.mana.getStoredMana() - start[1];
                    h.assertTrue(Math.abs(burned - 10 * ticks) <= 10, "9x9 burned " + burned + " mB in " + ticks + " ticks, expected " + 10 * ticks);
                    h.assertTrue(Math.abs(made - 250 * ticks) <= 250, "9x9 made " + made + " mana in " + ticks + " ticks, expected " + 250 * ticks);
                    ReactorShaftPortBlockEntity port = (ReactorShaftPortBlockEntity) h.getBlockEntity(shaft);
                    float su = port.calculateStressApplied() * Math.abs(port.getSpeed());
                    h.assertTrue(Math.abs(su - 32 * 256) < 0.5f, "9x9 shaft draws " + su + " SU, expected 8192");
                    h.assertTrue(rc.mana.getManaCapacity() == 200_000, "9x9 buffer is " + rc.mana.getManaCapacity());
                })
                .thenSucceed();
    }
}
