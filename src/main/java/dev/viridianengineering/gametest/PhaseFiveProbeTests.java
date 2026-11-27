package dev.viridianengineering.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.viridianengineering.ViridianEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Phase 5 smoke test: a GameTest can assemble a Sable ship and its physics runs (an unsupported ship falls). */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseFiveProbeTests {

    @GameTest(template = "empty_12x6x12", timeoutTicks = 200, batch = "viridian_probe")
    public static void probeAssembleAndFall(GameTestHelper h) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) {
            h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            blocks.add(h.absolutePos(new BlockPos(x, 4, z)));
        }
        ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), blocks.get(0), blocks, BoundingBox3i.from(blocks));
        h.assertTrue(ship != null, "assembleBlocks returned null");
        double y0 = ship.logicalPose().position().y();
        ViridianEngineering.LOGGER.info("[probe] assembled {} mass={} com={} gravity={} y0={} worldBlockNowAir={}",
                ship.getUniqueId(), ship.getMassTracker().getMass(), ship.getMassTracker().getCenterOfMass(),
                DimensionPhysicsData.getGravity(h.getLevel()), y0, h.getBlockState(new BlockPos(5, 4, 5)).isAir());
        h.runAfterDelay(20, () -> ViridianEngineering.LOGGER.info("[probe] t=20 y={}", ship.logicalPose().position().y()));
        h.runAfterDelay(60, () -> {
            double y = ship.logicalPose().position().y();
            ViridianEngineering.LOGGER.info("[probe] t=60 y={} removed={}", y, ship.isRemoved());
            h.assertTrue(y < y0 - 1, "An assembled ship with nothing holding it up did not fall (" + y0 + " -> " + y + ")");
            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer c = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());
            c.removeSubLevel(ship.getPlot().plotPos.x - 10000, ship.getPlot().plotPos.z - 10000, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            h.succeed();
        });
    }
}
