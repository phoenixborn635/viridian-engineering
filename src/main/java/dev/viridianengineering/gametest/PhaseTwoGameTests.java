package dev.viridianengineering.gametest;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.corporea.*;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.viridianengineering.content.corporea.CorporeaNetworkManager.Access.*;

/** Wired-core regressions only. The full Phase 2 client/server bridge gate is still pending. */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseTwoGameTests {
    private static final BlockPos TERMINAL = new BlockPos(1, 1, 1);
    private static final BlockPos PIPE = new BlockPos(2, 1, 1);
    private static final BlockPos PORT = new BlockPos(3, 1, 1);
    private static final BlockPos CHEST = new BlockPos(4, 1, 1);

    private static void setup(GameTestHelper h) {
        h.setBlock(TERMINAL, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState());
        h.setBlock(PIPE, VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(PORT, VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.EAST));
        h.setBlock(CHEST, Blocks.CHEST);
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void unpoweredManualTransfersConserve(GameTestHelper h) {
        setup(h);
        h.runAfterDelay(2, () -> {
            var level = h.getLevel(); var origin = h.absolutePos(TERMINAL);
            ItemStack offered = new ItemStack(Items.IRON_INGOT, 48);
            h.assertTrue(CorporeaNetworkManager.deposit(level, origin, offered, MANUAL_WIRED, new TransactionContext()).isEmpty(), "Manual deposit refused without power");
            h.assertTrue(offered.getCount() == 48, "Caller-owned stack mutated");
            h.assertTrue(CorporeaNetworkManager.withdraw(level, origin, offered, 16, AUTOMATED, new TransactionContext()).isEmpty(), "Unpowered automation extracted items");
            ItemStack got = CorporeaNetworkManager.withdraw(level, origin, offered, 16, MANUAL_WIRED, new TransactionContext());
            int remaining = CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext()).stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(got.getCount() == 16 && remaining == 32, "Manual transfer failed item conservation");
            h.succeed();
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void breakingPipeInvalidatesCachedReach(GameTestHelper h) {
        setup(h);
        h.runAfterDelay(2, () -> {
            var level = h.getLevel(); var origin = h.absolutePos(TERMINAL);
            CorporeaNetworkManager.deposit(level, origin, new ItemStack(Items.DIAMOND, 7), MANUAL_WIRED, new TransactionContext());
            h.setBlock(PIPE, Blocks.AIR);
            h.assertTrue(CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext()).isEmpty(), "Broken pipe retained cached access");
            h.setBlock(PIPE, VEBlocks.CORPOREA_PIPE.getDefaultState());
            int count = CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext()).stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(count == 7, "Reconnected pipe lost inventory access");
            h.succeed();
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 40)
    public static void bridgeHasNoItemCapabilityAndRejectsReentry(GameTestHelper h) {
        h.setBlock(PORT, VEBlocks.CORPOREA_BRIDGE_PORT.getDefaultState());
        var level = h.getLevel(); var pos = h.absolutePos(PORT);
        for (Direction side : Direction.values())
            h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) == null, "Bridge exposed a duplicate-indexable item capability");
        TransactionContext tx = new TransactionContext();
        var key = SableCompat.endpointKey(level, pos, Direction.DOWN);
        h.assertTrue(tx.enterBridge(key) && !tx.enterBridge(key), "Transaction failed to stop bridge reentry");
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 80)
    public static void controllerThresholdAndStress(GameTestHelper h) {
        setup(h);
        h.setBlock(TERMINAL, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState()
                .setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        BlockPos motor = TERMINAL.below();
        h.setBlock(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.UP));
        h.runAfterDelay(2, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(32));
        h.runAfterDelay(20, () -> {
            var controller = (CorporeaTerminalBlockEntity) h.getBlockEntity(TERMINAL);
            h.assertTrue(controller.isPowered(), "32 RPM did not activate controller");
            h.assertTrue(controller.calculateStressApplied() * Math.abs(controller.getSpeed()) == 256, "32 RPM must consume 256 SU");
            var offered = new ItemStack(Items.GOLD_INGOT, 9);
            h.assertTrue(CorporeaNetworkManager.deposit(h.getLevel(), h.absolutePos(TERMINAL), offered, AUTOMATED, new TransactionContext()).isEmpty(), "Powered automation refused deposit");
            ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(31);
        });
        h.runAfterDelay(40, () -> {
            var level = h.getLevel(); var origin = h.absolutePos(TERMINAL);
            h.assertTrue(!CorporeaNetworkManager.isPowered(level, origin), "31 RPM did not disable automation");
            h.assertTrue(CorporeaNetworkManager.withdraw(level, origin, new ItemStack(Items.GOLD_INGOT), 9, AUTOMATED, new TransactionContext()).isEmpty(), "Automation remained active below threshold");
            h.assertTrue(CorporeaNetworkManager.withdraw(level, origin, new ItemStack(Items.GOLD_INGOT), 9, MANUAL_WIRED, new TransactionContext()).getCount() == 9, "Power loss disabled manual withdrawal");
            h.succeed();
        });
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 60)
    public static void duplicateVaultPortsCountOnce(GameTestHelper h) {
        setup(h);
        h.setBlock(CHEST, com.simibubi.create.AllBlocks.ITEM_VAULT.getDefaultState());
        h.setBlock(PORT.above(), VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(CHEST.above(), VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState()
                .setValue(CorporeaPortBlock.FACING, Direction.DOWN));
        h.runAfterDelay(20, () -> {
            var level = h.getLevel(); var origin = h.absolutePos(TERMINAL);
            h.assertTrue(CorporeaNetworkManager.deposit(level, origin, new ItemStack(Items.IRON_INGOT, 37), MANUAL_WIRED, new TransactionContext()).isEmpty(), "Vault rejected deposit");
            int total = CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext()).stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(total == 37, "Two ports counted the same Vault more than once");
            var withdrawn = CorporeaNetworkManager.withdraw(level, origin, new ItemStack(Items.IRON_INGOT), 64, MANUAL_WIRED, new TransactionContext());
            h.assertTrue(withdrawn.getCount() == 37 && CorporeaNetworkManager.browse(level, origin, MANUAL_WIRED, new TransactionContext()).isEmpty(), "Vault withdrawal duplicated or lost items");
            h.succeed();
        });
    }
}
