package dev.viridianengineering.gametest;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.autocraft.CraftingJob;
import dev.viridianengineering.content.autocraft.CraftingJobSavedData;
import dev.viridianengineering.content.battery.ShellExplosionTask;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlockEntity;
import dev.viridianengineering.content.corporea.CorporeaWirelessTerminalItem;
import dev.viridianengineering.content.drive.ItemStackKey;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VECorporea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseSixRecoveryTests {
    private static final String TEMPLATE = "empty_12x6x12";

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "viridian_release_ships")
    public static void wirelessBindingFollowsAssemblyAndDisassembly(GameTestHelper h) {
        var site = new PhaseFiveGameTests.Site(h);
        var level = site.level;
        BlockPos terminal = new BlockPos(3, 1, 3);
        site.set(terminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState());
        site.set(terminal.below(), Blocks.OAK_PLANKS);
        BlockPos original = site.abs(terminal);
        var blockEntity = site.at(terminal, CorporeaTerminalBlockEntity.class);
        ItemStack wireless = new ItemStack(VECorporea.WIRELESS_TERMINAL.get());
        CorporeaWirelessTerminalItem.bind(wireless, level, blockEntity);

        var ship = PhaseFiveGameTests.assemble(site, List.of(terminal.below(), terminal));
        BlockPos aboard = ship.plot(original);
        var aboardKey = CorporeaWirelessTerminalItem.currentBinding(level, wireless);
        h.assertTrue(aboard.equals(SableCompat.resolve(level, aboardKey)),
                "Wireless binding did not follow assembly");
        var data = CraftingJobSavedData.get(level.getServer());
        var job = new CraftingJob(UUID.randomUUID(), aboardKey, ItemStackKey.of(new ItemStack(Items.DIAMOND)), 1);
        data.add(job);
        h.runAfterDelay(6, () -> {
            h.assertTrue(aboard.equals(SableCompat.resolve(level, CorporeaWirelessTerminalItem.currentBinding(level, wireless))),
                    "Wireless binding changed while the ship was in flight");
            BlockPos landing = original.east(8);
            SimAssemblyHelper.disassembleSubLevel(level, ship.ship(), aboard, landing, Rotation.NONE, false);
            h.assertTrue(landing.equals(SableCompat.resolve(level, CorporeaWirelessTerminalItem.currentBinding(level, wireless))),
                    "Wireless binding did not follow disassembly");
            h.assertTrue(landing.equals(SableCompat.resolve(level, job.terminal)) && !job.endpointLost,
                    "Disassembly cancelled the job instead of moving it");
            job.status = CraftingJob.Status.DONE;
            level.setBlockAndUpdate(landing, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(landing, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState());
            h.assertTrue(CorporeaWirelessTerminalItem.currentBinding(level, wireless) == null,
                    "A replacement terminal inherited the previous terminal's binding");
            PhaseFiveGameTests.finish(site, ship);
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "viridian_release_ships")
    public static void removedShipRefundsEscrowOnce(GameTestHelper h) {
        var site = new PhaseFiveGameTests.Site(h);
        var level = site.level;
        BlockPos terminal = new BlockPos(3, 1, 3);
        site.set(terminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState());
        site.set(terminal.below(), Blocks.OAK_PLANKS);
        var ship = PhaseFiveGameTests.assemble(site, List.of(terminal.below(), terminal));
        BlockPos aboard = ship.plot(site.abs(terminal));
        var data = CraftingJobSavedData.get(level.getServer());
        var job = new CraftingJob(UUID.randomUUID(), SableCompat.endpointKey(level, aboard, Direction.DOWN),
                ItemStackKey.of(new ItemStack(Items.DIAMOND)), 1);
        job.escrowAdd(new ItemStack(Items.GOLD_INGOT, 37));
        data.add(job);
        h.runAfterDelay(5, () -> {
            h.assertTrue(data.find(job.id) == job && !job.escrow.isEmpty(), "Live unpowered ship lost its escrow");
            Vec3 world = SableCompat.toWorld(level, Vec3.atCenterOf(aboard));
            PhaseFiveGameTests.remove(h, ship);
            h.runAfterDelay(8, () -> {
                h.assertTrue(data.find(job.id) == null && job.escrow.isEmpty(), "Deleted ship job still held escrow");
                h.assertTrue(dropped(h, world, Items.GOLD_INGOT) == 37, "Deleted ship did not refund exactly 37 gold");
                site.release();
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "viridian_release_ships")
    public static void batteryBlastSpillsTerrainAndAirshipContainers(GameTestHelper h) {
        var site = new PhaseFiveGameTests.Site(h);
        var level = site.level;
        BlockPos cargo = new BlockPos(3, 1, 3);
        BlockPos terrainCargo = cargo.east(4);
        site.set(cargo, Blocks.CHEST);
        site.set(cargo.below(), Blocks.OAK_PLANKS);
        site.set(terrainCargo, Blocks.CHEST);
        site.at(cargo, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 31));
        site.at(terrainCargo, ChestBlockEntity.class).setItem(0, new ItemStack(Items.GOLD_INGOT, 37));
        var ship = PhaseFiveGameTests.assemble(site, List.of(cargo.below(), cargo));
        BlockPos aboard = ship.plot(site.abs(cargo));
        h.runAfterDelay(5, () -> {
            BlockPos world = BlockPos.containing(SableCompat.toWorld(level, Vec3.atCenterOf(aboard)));
            var task = new ShellExplosionTask(UUID.randomUUID(), world, 6, 0);
            for (int tick = 0; tick < 50 && !task.isDone(); tick++) task.step(level);

            h.assertTrue(task.isDone() && level.getBlockState(site.abs(terrainCargo)).isAir(), "Terrain chest survived the blast");
            h.assertTrue(level.getBlockState(aboard).isAir(), "Airship chest survived the blast (plot bounds "
                    + ship.ship().getPlot().getBoundingBox() + ", world bounds " + ship.ship().boundingBox()
                    + ", chest " + aboard + ", blast " + world + ")");
            h.assertTrue(dropped(h, Vec3.atCenterOf(world), Items.DIAMOND) == 31, "Airship cargo was lost or duplicated");
            h.assertTrue(dropped(h, Vec3.atCenterOf(site.abs(terrainCargo)), Items.GOLD_INGOT) == 37,
                    "Terrain cargo was lost or duplicated");
            PhaseFiveGameTests.finish(site, ship);
            h.succeed();
        });
    }

    private static int dropped(GameTestHelper h, Vec3 center, net.minecraft.world.item.Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(5)).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
