package dev.viridianengineering.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalMenu;
import dev.viridianengineering.content.corporea.CorporeaWirelessTerminalItem;
import dev.viridianengineering.content.corporea.TerminalView;
import dev.viridianengineering.content.corporea.TransactionContext;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VECorporea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.viridianengineering.content.corporea.CorporeaNetworkManager.Access.MANUAL_WIRED;

/**
 * Phase 2 completion: terminal view paging/search/sort, manual crafting with network refill
 * (works unpowered, decision 2A), and the wireless terminal's power gate.
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseTwoTerminalTests {

    private static final String EMPTY = "empty_12x6x12";
    private static final BlockPos TERMINAL = new BlockPos(2, 1, 2), PIPE = new BlockPos(3, 1, 2),
            PORT = new BlockPos(4, 1, 2), CHEST = new BlockPos(5, 1, 2), MOTOR = new BlockPos(2, 0, 2);

    private PhaseTwoTerminalTests() {}

    private static void network(GameTestHelper h, boolean withMotor) {
        h.setBlock(TERMINAL, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState().setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        h.setBlock(PIPE, VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(PORT, VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.EAST));
        h.setBlock(CHEST, Blocks.CHEST);
        if (withMotor)
            h.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
    }

    private static void motor(GameTestHelper h, int rpm) {
        ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(rpm);
    }

    /** FakePlayer never joins the level, so other mods' join packets can't break the test. */
    private static net.neoforged.neoforge.common.util.FakePlayer fakePlayer(GameTestHelper h, String name) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(), profile);
        player.getInventory().clearContent();
        return player;
    }

    private static int count(GameTestHelper h, Item item) {
        return CorporeaNetworkManager.browse(h.getLevel(), h.absolutePos(TERMINAL), MANUAL_WIRED, new TransactionContext())
                .stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** 100 item types -> 2 pages of 64; merged counts exceed a stack; search and sort work. */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void terminalViewPagesSearchesAndSorts(GameTestHelper h) {
        List<ItemStack> stacks = new ArrayList<>();
        BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).limit(100).forEach(i -> stacks.add(new ItemStack(i)));
        stacks.add(new ItemStack(Items.IRON_INGOT, 64));
        stacks.add(new ItemStack(Items.IRON_INGOT, 64));
        stacks.add(new ItemStack(Items.IRON_INGOT, 64));

        List<TerminalView.Entry> all = TerminalView.aggregate(stacks);
        long iron = all.stream().filter(e -> e.template().is(Items.IRON_INGOT)).mapToLong(TerminalView.Entry::count).sum();
        h.assertTrue(iron >= 192, "aggregate did not merge stacks past 64: " + iron);
        h.assertTrue(all.stream().filter(e -> e.template().is(Items.IRON_INGOT)).count() == 1, "iron split into several entries");

        List<TerminalView.Entry> view = TerminalView.filterAndSort(all, "", TerminalView.Sort.NAME);
        int types = view.size();
        h.assertTrue(TerminalView.pageCount(types) == (types + 63) / 64 && TerminalView.page(view, 0).size() == 64,
                "page size must be 64");
        h.assertTrue(TerminalView.page(view, 1).size() == types - 64, "second page wrong size");

        List<TerminalView.Entry> byCount = TerminalView.filterAndSort(all, "", TerminalView.Sort.COUNT);
        h.assertTrue(byCount.get(0).template().is(Items.IRON_INGOT), "count sort must put the largest total first");

        List<TerminalView.Entry> ironSearch = TerminalView.filterAndSort(all, "iron ingot", TerminalView.Sort.NAME);
        h.assertTrue(ironSearch.stream().allMatch(e -> e.template().getHoverName().getString().toLowerCase().contains("iron")),
                "search returned non-matching items");
        h.assertTrue(TerminalView.filterAndSort(all, "@minecraft", TerminalView.Sort.NAME).size() == types,
                "@mod search should match every vanilla entry");
        h.assertTrue(TerminalView.formatCount(1_234).equals("1.2k") && TerminalView.formatCount(45_000).equals("45k"),
                "count formatting");
        h.succeed();
    }

    /**
     * Unpowered wired terminal (decision 2A): 2 planks in a vertical line make sticks; shift-clicking
     * the result keeps crafting while the grid refills from the chest, until the planks run out.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void terminalCraftingRefillsFromNetwork(GameTestHelper h) {
        network(h, false);
        h.runAfterDelay(2, () -> {
            var level = h.getLevel();
            CorporeaNetworkManager.deposit(level, h.absolutePos(TERMINAL), new ItemStack(Items.OAK_PLANKS, 6), MANUAL_WIRED, new TransactionContext());
            h.assertTrue(count(h, Items.OAK_PLANKS) == 6, "setup: planks not stored");

            var player = fakePlayer(h, "ve_craft");
            BlockPos t = h.absolutePos(TERMINAL);
            player.setPos(t.getX() + .5, t.getY() + 1, t.getZ() + 1.5);
            var menu = new CorporeaTerminalMenu(1, player.getInventory(), t, false);
            h.assertTrue(menu.stillValid(player), "menu not valid next to the terminal");

            // grid slots 1 (top-left) and 4 (middle-left): one plank each -> 4 sticks per craft
            menu.slots.get(1).set(new ItemStack(Items.OAK_PLANKS));
            menu.slots.get(4).set(new ItemStack(Items.OAK_PLANKS));
            menu.slotsChanged(null);
            h.assertTrue(menu.slots.get(CorporeaTerminalMenu.RESULT).getItem().is(Items.STICK), "no stick recipe in result slot");

            menu.quickMoveStack(player, CorporeaTerminalMenu.RESULT);

            int sticks = player.getInventory().countItem(Items.STICK);
            ViridianEngineering.LOGGER.info("[VE Phase2] terminal crafting: sticks={} planks left in network={}", sticks, count(h, Items.OAK_PLANKS));
            // 2 planks in the grid + 6 refilled from the network = 4 crafts = 16 sticks
            h.assertTrue(sticks == 16, "expected 16 sticks from 8 planks, got " + sticks);
            h.assertTrue(count(h, Items.OAK_PLANKS) == 0, "network should be out of planks");
            h.succeed();
        });
    }

    /** Wireless terminal: refuses while unbound or unpowered, opens at 32 RPM, pauses again below it. */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void wirelessTerminalNeedsPower(GameTestHelper h) {
        network(h, true);
        var level = h.getLevel();
        ItemStack wireless = new ItemStack(VECorporea.WIRELESS_TERMINAL.get());
        h.assertTrue(CorporeaWirelessTerminalItem.unavailableReason(level, CorporeaWirelessTerminalItem.boundTerminal(wireless)) != null,
                "unbound terminal must refuse");
        var key = SableCompat.endpointKey(level, h.absolutePos(TERMINAL), Direction.DOWN);
        CorporeaWirelessTerminalItem.bind(wireless, key);
        h.assertTrue(key.equals(CorporeaWirelessTerminalItem.boundTerminal(wireless)), "binding did not round-trip");

        h.runAfterDelay(5, () -> h.assertTrue(CorporeaWirelessTerminalItem.unavailableReason(level, key) != null,
                "unpowered network must refuse wireless access"));
        h.runAfterDelay(6, () -> motor(h, 32));
        h.runAfterDelay(30, () -> {
            h.assertTrue(CorporeaWirelessTerminalItem.unavailableReason(level, key) == null, "32 RPM network should open wirelessly");
            var player = fakePlayer(h, "ve_wireless");
            player.setItemInHand(InteractionHand.MAIN_HAND, wireless.copy());
            player.setPos(h.absolutePos(new BlockPos(10, 1, 10)).getCenter()); // far from the terminal
            var menu = new CorporeaTerminalMenu(2, player.getInventory(), h.absolutePos(TERMINAL), true);
            h.assertTrue(menu.stillValid(player), "wireless menu should stay valid away from the terminal while held");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.assertTrue(!menu.stillValid(player), "wireless menu must close when the terminal item is put away");
            motor(h, 31);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(CorporeaWirelessTerminalItem.unavailableReason(level, key) != null, "31 RPM must pause wireless access");
            h.succeed();
        });
    }
}
