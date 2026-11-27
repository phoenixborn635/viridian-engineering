package dev.viridianengineering.gametest;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.battery.ShellExplosionTask;
import dev.viridianengineering.content.cannon.ArcaneBoltEntity;
import dev.viridianengineering.content.cannon.GaiaCannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReleaseProtectionTests {
    private static final String TEMPLATE = "empty_12x6x12";
    private static final String BATCH = "viridian_release_protection";

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void chargedBoltBreaksTerrainButHonorsProtection(GameTestHelper h) {
        BlockPos impact = new BlockPos(6, 2, 6);
        BlockPos shielded = impact.west().north();
        h.setBlock(impact, Blocks.GLASS);
        h.setBlock(shielded, Blocks.GLASS);
        withProtection(h, shielded, () -> {
            ArcaneBoltEntity bolt = ArcaneBoltEntity.create(h.getLevel(), Vec3.atCenterOf(h.absolutePos(impact.west())),
                    new Vec3(1, 0, 0), GaiaCannonBlockEntity.damageFor(1f), 1f);
            h.getLevel().addFreshEntity(bolt);
            bolt.tick();
            h.assertTrue(bolt.isRemoved(), "The charged shot did not impact");
            h.assertBlockNotPresent(Blocks.GLASS, impact);
        });
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void batteryShellHonorsProtectionBeforeRemovingTerrain(GameTestHelper h) {
        BlockPos center = new BlockPos(6, 2, 6);
        BlockPos shielded = center.east();
        h.setBlock(center.west(), Blocks.GLASS);
        h.setBlock(shielded, Blocks.GLASS);
        withProtection(h, shielded, () -> {
            var shell = new ShellExplosionTask(UUID.randomUUID(), h.absolutePos(center), 2, 0);
            for (int i = 0; i < 8 && !shell.isDone(); i++) shell.step(h.getLevel());
            h.assertTrue(shell.isDone(), "The blast did not finish");
            h.assertBlockNotPresent(Blocks.GLASS, center.west());
        });
        h.succeed();
    }

    private static void withProtection(GameTestHelper h, BlockPos protectedRelativePos, Runnable blast) {
        BlockPos protectedPos = h.absolutePos(protectedRelativePos);
        AtomicBoolean offered = new AtomicBoolean();
        Consumer<ExplosionEvent.Detonate> protection = event -> {
            if (event.getLevel() == h.getLevel() && event.getAffectedBlocks().remove(protectedPos))
                offered.set(true);
        };
        NeoForge.EVENT_BUS.addListener(protection);
        try {
            blast.run();
            h.assertTrue(offered.get(), "The explosion did not offer the protected block to NeoForge");
            h.assertBlockPresent(Blocks.GLASS, protectedRelativePos);
        } finally {
            NeoForge.EVENT_BUS.unregister(protection);
        }
    }
}
