package dev.viridianengineering.gametest;

import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaPipeBlockEntity;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlock;
import dev.viridianengineering.content.drive.ArchivePylonBlock;
import dev.viridianengineering.content.pipe.VEPipeShape;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Corporea shares the pipe shape rules and uses its own fittings and light routing. */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeShapeTests {

    private static AttachmentTypes at(GameTestHelper h, BlockPos rel, Direction side) {
        BlockPos pos = h.absolutePos(rel);
        return VEPipeShape.attachment(h.getLevel(), pos, h.getLevel().getBlockState(pos), side);
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void terrasteelBendsUseFlushConnections(GameTestHelper h) {
        BlockPos elbow = new BlockPos(3, 1, 3);
        var pipe = VEBlocks.TERRASTEEL_MANA_PIPE.getDefaultState();
        h.setBlock(elbow, pipe);
        h.setBlock(elbow.west(), pipe);
        h.setBlock(elbow.above(), pipe);

        h.assertTrue(at(h, elbow, Direction.WEST) == AttachmentTypes.CONNECTION
                        && at(h, elbow, Direction.UP) == AttachmentTypes.CONNECTION,
                "Terrasteel elbows should have flush pipe joins on both arms");
        h.assertTrue(at(h, elbow.above(), Direction.UP) == AttachmentTypes.RIM,
                "An exposed Terrasteel pipe end should keep its rim");
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void corporeaPipesUseFlushConnections(GameTestHelper h) {
        // port (2,1,2) facing west into nothing; pipes east of it: (3,1,2) (4,1,2), then a bend up to (4,2,2)
        h.setBlock(new BlockPos(2, 1, 2), VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.WEST));
        BlockPos a = new BlockPos(3, 1, 2), b = new BlockPos(4, 1, 2), c = new BlockPos(4, 2, 2), lone = new BlockPos(8, 1, 8), end = new BlockPos(8, 1, 2);
        for (BlockPos p : new BlockPos[] {a, b, c, lone, end}) h.setBlock(p, VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(end.west(), VEBlocks.CORPOREA_PIPE.getDefaultState());

        h.assertTrue(at(h, a, Direction.WEST) == AttachmentTypes.CONNECTION, "Pipe into a port should let the port own the fitting, got " + at(h, a, Direction.WEST));
        h.assertTrue(at(h, a, Direction.EAST) == AttachmentTypes.CONNECTION, "Straight pipe-to-pipe should be a plain connection");
        h.assertTrue(at(h, b, Direction.WEST) == AttachmentTypes.CONNECTION && at(h, b, Direction.UP) == AttachmentTypes.CONNECTION,
                "A bend should use seamless plain connections");
        h.assertTrue(at(h, c, Direction.DOWN) == AttachmentTypes.CONNECTION && at(h, c, Direction.UP) == AttachmentTypes.RIM,
                "A pipe with one link is open on the far side and ends in a rim");
        var loneState = h.getBlockState(lone);
        h.assertTrue(VEPipeShape.open(loneState, Direction.UP) && VEPipeShape.open(loneState, Direction.DOWN)
                && at(h, lone, Direction.UP) == AttachmentTypes.RIM && !VEPipeShape.open(loneState, Direction.EAST), "A lone pipe stands upright with rims");
        h.assertTrue(at(h, end, Direction.EAST) == AttachmentTypes.RIM, "Open end of a run should be a rim");
        h.assertTrue(VEPipeShape.isJunction(h.getBlockState(b)) == false, "A bend is not a junction");
        h.succeed();
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 30)
    public static void corporeaLightFollowsNearestWiredTerminalThroughBendsAndPorts(GameTestHelper h) {
        BlockPos terminal = new BlockPos(1, 1, 2);
        BlockPos near = terminal.east(), elbow = near.east(), high = elbow.above(), port = high.east();
        BlockPos far = port.east(), last = far.east(), secondTerminal = last.east();
        h.setBlock(terminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState()
                .setValue(CorporeaTerminalBlock.FACING, Direction.NORTH));
        h.setBlock(port, VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING, Direction.NORTH));
        h.setBlock(last.south(), VEStorage.ARCHIVE_PYLON.getDefaultState().setValue(ArchivePylonBlock.FACING, Direction.SOUTH));
        for (BlockPos pipe : new BlockPos[] {near, elbow, high, far, last})
            h.setBlock(pipe, VEBlocks.CORPOREA_PIPE.getDefaultState());

        h.runAfterDelay(2, () -> {
            CorporeaNetworkManager.wiredNodes(h.getLevel(), h.absolutePos(elbow));
            assertLight(h, near, 1, Direction.WEST, Direction.EAST);
            assertLight(h, elbow, 2, Direction.WEST, Direction.UP);
            assertLight(h, high, 3, Direction.DOWN, Direction.EAST);
            assertLight(h, far, 5, Direction.WEST, Direction.EAST);
            assertLight(h, last, 6, Direction.WEST, Direction.SOUTH);
            h.assertTrue(at(h, near, Direction.WEST) == AttachmentTypes.CONNECTION
                            && at(h, last, Direction.SOUTH) == AttachmentTypes.CONNECTION,
                    "Terminal and pylon joints should let the endpoint own its socket");

            h.setBlock(secondTerminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState()
                    .setValue(CorporeaTerminalBlock.FACING, Direction.NORTH));
            CorporeaNetworkManager.wiredNodes(h.getLevel(), h.absolutePos(far));
            assertLight(h, far, 2, Direction.EAST, Direction.WEST);
            assertLight(h, last, 1, Direction.EAST, Direction.SOUTH);
            h.assertTrue(light(h, high).flowThrough(Direction.EAST) == 0,
                    "A joint equally far from each terminal has no downhill light");

            h.setBlock(terminal, Blocks.AIR);
            h.setBlock(secondTerminal, Blocks.AIR);
            CorporeaNetworkManager.wiredNodes(h.getLevel(), h.absolutePos(elbow));
            h.assertTrue(light(h, elbow).equals(CorporeaPipeBlockEntity.LightRoute.DISCONNECTED),
                    "Light should stop after the last terminal is removed");
            h.succeed();
        });
    }

    private static CorporeaPipeBlockEntity.LightRoute light(GameTestHelper h, BlockPos pipe) {
        return ((CorporeaPipeBlockEntity) h.getBlockEntity(pipe)).lightRoute();
    }

    private static void assertLight(GameTestHelper h, BlockPos pipe, int distance, Direction out, Direction in) {
        var light = light(h, pipe);
        h.assertTrue(light.distance() == distance && light.flowThrough(out) == 1 && light.flowThrough(in) == -1,
                "Unexpected light route at " + pipe + ": " + light);
    }
}
