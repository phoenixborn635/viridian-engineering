package dev.viridianengineering.gametest;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.TransactionContext;
import dev.viridianengineering.content.drive.*;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static dev.viridianengineering.content.corporea.CorporeaNetworkManager.Access.*;

/** Phase 3 tasks a + b: Terra Drives, TerraDriveSavedData and the 4-slot Archive Pylon. */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseThreeStorageTests {

    private static final BlockPos TERMINAL = new BlockPos(1, 1, 1);
    private static final BlockPos PIPE = new BlockPos(2, 1, 1);
    private static final BlockPos PYLON = new BlockPos(3, 1, 1);
    private static final BlockPos PYLON_2 = new BlockPos(6, 1, 1);

    private static TerraDriveSavedData data(GameTestHelper h) {
        return TerraDriveSavedData.get(h.getLevel().getServer());
    }

    private static ItemStack named(String name, int count) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static long total(java.util.List<ItemStack> items) {
        return items.stream().mapToLong(ItemStack::getCount).sum();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void capacityLimitsTotalCountNotTypes(GameTestHelper h) {
        TerraDriveSavedData data = data(h);
        DriveStorageRecord small = data.getOrCreate(UUID.randomUUID(), 16_384);
        long got = data.insert(small, new ItemStack(Items.COBBLESTONE), 20_000, false);
        h.assertTrue(got == 16_384 && small.total() == 16_384, "16K drive accepted " + got + " instead of exactly 16,384");
        h.assertTrue(data.insert(small, new ItemStack(Items.DIAMOND), 1, false) == 0, "A full drive accepted another item");

        DriveStorageRecord big = data.getOrCreate(UUID.randomUUID(), 1_048_576);
        for (int i = 0; i < 500; i++) data.insert(big, named("Ledger " + i, 1), 1, false);
        h.assertTrue(big.distinctKeys() == 500 && big.total() == 500, "Distinct item types were capped: " + big.distinctKeys());
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void componentPayloadCapAndExactKeys(GameTestHelper h) {
        TerraDriveSavedData data = data(h);
        DriveStorageRecord record = data.getOrCreate(UUID.randomUUID(), 65_536);
        ItemStack bomb = named("x".repeat(20_000), 1);
        h.assertTrue(data.check(record, bomb) == TerraDriveSavedData.Rejection.COMPONENT_PAYLOAD, "Oversized component payload was not rejected");
        h.assertTrue(data.insert(record, bomb, 1, false) == 0 && record.total() == 0, "Oversized component payload was stored");

        data.insert(record, named("Alpha", 1), 10, false);
        data.insert(record, named("Beta", 1), 7, false);
        data.insert(record, new ItemStack(Items.PAPER), 3, false);
        h.assertTrue(record.distinctKeys() == 3, "Differently named items merged into " + record.distinctKeys() + " keys");
        h.assertTrue(record.count(ItemStackKey.of(named("Alpha", 1))) == 10, "Named item count wrong");
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void savedDataSurvivesSaveAndLoad(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        TerraDriveSavedData original = new TerraDriveSavedData();
        TerraDriveSavedData live = data(h);
        UUID id = UUID.randomUUID();
        // Build through the live instance (it has registries for the payload check), then copy over.
        DriveStorageRecord record = live.getOrCreate(id, 262_144);
        live.insert(record, new ItemStack(Items.IRON_INGOT), 200_000, false);
        live.insert(record, named("Deed", 1), 5, false);
        CompoundTag saved = live.save(new CompoundTag(), registries);

        TerraDriveSavedData loaded = TerraDriveSavedData.load(saved, registries);
        DriveStorageRecord back = loaded.find(id);
        h.assertTrue(back != null && back.capacity() == 262_144, "Drive record lost on reload");
        h.assertTrue(back.total() == 200_005 && back.count(ItemStackKey.of(new ItemStack(Items.IRON_INGOT))) == 200_000
                && back.count(ItemStackKey.of(named("Deed", 1))) == 5, "Drive contents changed across save/load");
        h.assertTrue(original.find(id) == null, "Test isolation broken");
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void pylonHasFourSlotsAndSingleMount(GameTestHelper h) {
        h.setBlock(PYLON, VEStorage.ARCHIVE_PYLON.getDefaultState());
        h.setBlock(PYLON_2, VEStorage.ARCHIVE_PYLON.getDefaultState());
        h.runAfterDelay(2, () -> {
            var first = (ArchivePylonBlockEntity) h.getBlockEntity(PYLON);
            var second = (ArchivePylonBlockEntity) h.getBlockEntity(PYLON_2);
            h.assertTrue(first.mount(new ItemStack(Items.DIAMOND)) == ArchivePylonBlockEntity.MountResult.NOT_A_DRIVE, "Pylon accepted a non-drive");
            for (int i = 0; i < 4; i++)
                h.assertTrue(first.mount(VEStorage.TERRA_DRIVE_16K.asStack()) == ArchivePylonBlockEntity.MountResult.MOUNTED, "Drive " + i + " refused");
            h.assertTrue(first.mount(VEStorage.TERRA_DRIVE_16K.asStack()) == ArchivePylonBlockEntity.MountResult.NO_FREE_SLOT, "Pylon took a 5th drive");

            ItemStack copy = first.driveIn(0).copy(); // same DriveUUID: a duplicated drive item
            h.assertTrue(second.mount(copy) == ArchivePylonBlockEntity.MountResult.LOCKED_ELSEWHERE, "Duplicate drive mounted in a second pylon");
            ItemStack ejected = first.eject(0);
            h.assertTrue(TerraDriveItem.getId(ejected).equals(TerraDriveItem.getId(copy)), "Ejected the wrong drive");
            h.assertTrue(second.mount(copy) == ArchivePylonBlockEntity.MountResult.MOUNTED, "Released drive could not be mounted elsewhere");
            h.assertTrue(h.getBlockState(PYLON).getValue(ArchivePylonBlock.BAY[1]) && !h.getBlockState(PYLON).getValue(ArchivePylonBlock.BAY[0]),
                    "Bay lights do not match mounted drives");
            h.succeed();
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void wiredPylonWorksUnpoweredAndConserves(GameTestHelper h) {
        h.setBlock(TERMINAL, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState());
        h.setBlock(PIPE, VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(PYLON, VEStorage.ARCHIVE_PYLON.getDefaultState());
        h.runAfterDelay(2, () -> {
            var level = h.getLevel();
            var origin = h.absolutePos(TERMINAL);
            var pylon = (ArchivePylonBlockEntity) h.getBlockEntity(PYLON);
            pylon.mount(VEStorage.TERRA_DRIVE_64K.asStack());
            pylon.mount(VEStorage.TERRA_DRIVE_16K.asStack());

            ItemStack leftover = ItemStack.EMPTY;
            for (int i = 0; i < 100; i++) { // 6,400 cobblestone in vanilla-sized stacks
                leftover = CorporeaNetworkManager.deposit(level, origin, new ItemStack(Items.COBBLESTONE, 64), MANUAL_WIRED, new TransactionContext());
                h.assertTrue(leftover.isEmpty(), "Unpowered manual deposit into the pylon was refused");
            }
            CorporeaNetworkManager.deposit(level, origin, named("Alpha", 3), MANUAL_WIRED, new TransactionContext());
            CorporeaNetworkManager.deposit(level, origin, named("Beta", 2), MANUAL_WIRED, new TransactionContext());

            var items = CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext());
            h.assertTrue(total(items) == 6_405, "Browse saw " + total(items) + " items instead of 6,405");
            h.assertTrue(CorporeaNetworkManager.browse(level, origin, AUTOMATED, new TransactionContext()).isEmpty(), "Unpowered automation reached the pylon");

            ItemStack got = CorporeaNetworkManager.withdraw(level, origin, named("Beta", 1), 64, MANUAL_WIRED, new TransactionContext());
            h.assertTrue(got.getCount() == 2 && ItemStackKey.of(got).equals(ItemStackKey.of(named("Beta", 1))), "Withdrew the wrong named item");
            long after = total(CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext()));
            h.assertTrue(after == 6_403, "Withdraw did not conserve items: " + after);
            h.succeed();
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void breakingPylonDropsDrivesKeepsData(GameTestHelper h) {
        h.setBlock(PYLON, VEStorage.ARCHIVE_PYLON.getDefaultState());
        h.runAfterDelay(2, () -> {
            var pylon = (ArchivePylonBlockEntity) h.getBlockEntity(PYLON);
            pylon.mount(VEStorage.TERRA_DRIVE_256K.asStack());
            UUID id = TerraDriveItem.getId(pylon.driveIn(0));
            DriveStorageRecord record = data(h).find(id);
            data(h).insert(record, new ItemStack(Items.GOLD_INGOT), 1_000, false);

            h.setBlock(PYLON, Blocks.AIR);
            h.assertItemEntityPresent(VEStorage.TERRA_DRIVE_256K.get(), PYLON, 2.0);
            h.assertTrue(data(h).mountOf(id) == null, "Broken pylon kept its mount lock");
            h.assertTrue(data(h).find(id).total() == 1_000, "Drive data lost when its pylon broke");
            h.succeed();
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void pipesOnlyPlugIntoSockets(GameTestHelper h) {
        BlockPos terminal = new BlockPos(2, 1, 2), pylon = new BlockPos(6, 1, 2);
        h.setBlock(terminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState()
                .setValue(dev.viridianengineering.content.corporea.CorporeaTerminalBlock.FACING, net.minecraft.core.Direction.NORTH));
        h.setBlock(pylon, VEStorage.ARCHIVE_PYLON.getDefaultState());   // faces north
        h.setBlock(terminal.north(), VEBlocks.CORPOREA_PIPE.getDefaultState()); // screen: no socket
        h.setBlock(terminal.east(), VEBlocks.CORPOREA_PIPE.getDefaultState());  // side: socket
        h.setBlock(pylon.above(), VEBlocks.CORPOREA_PIPE.getDefaultState());    // crystal: no socket
        h.setBlock(pylon.west(), VEBlocks.CORPOREA_PIPE.getDefaultState());     // side: socket
        var north = net.minecraft.world.level.block.PipeBlock.NORTH;
        var east = net.minecraft.world.level.block.PipeBlock.EAST;
        var up = net.minecraft.world.level.block.PipeBlock.UP;
        var west = net.minecraft.world.level.block.PipeBlock.WEST;
        h.assertTrue(!h.getBlockState(terminal).getValue(north) && h.getBlockState(terminal).getValue(east), "Terminal sockets wrong");
        h.assertTrue(!h.getBlockState(pylon).getValue(up) && h.getBlockState(pylon).getValue(west), "Pylon sockets wrong");
        h.assertTrue(!h.getBlockState(terminal.north()).getValue(net.minecraft.world.level.block.PipeBlock.SOUTH), "Pipe joined the terminal screen");
        var nodes = CorporeaNetworkManager.wiredNodes(h.getLevel(), h.absolutePos(terminal));
        h.assertTrue(nodes.size() == 2, "Terminal graph should be itself + the east pipe, got " + nodes.size());
        h.succeed();
    }
}
