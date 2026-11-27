package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.viridianengineering.content.corporea.CorporeaNode;
import dev.viridianengineering.content.corporea.CorporeaPipeBlock;
import dev.viridianengineering.content.corporea.CorporeaPipeBlockEntity;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.drive.ArchivePylonBlock;
import dev.viridianengineering.content.pipe.VEPipeShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Light follows the wired route, including perpendicular turns and the exposed stubs on nodes. */
public final class CorporeaPulseRenderer implements BlockEntityRenderer<CorporeaPipeBlockEntity> {
    private static final int PERIOD = 2;

    public CorporeaPulseRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CorporeaPipeBlockEntity pipe, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var level = pipe.getLevel();
        if (level == null) return;
        BlockState state = pipe.getBlockState();
        var route = pipe.lightRoute();
        float phase = route.distance() % PERIOD + PipePulseRenderer.timePhase(level.getGameTime(), partialTick, PERIOD);
        float[] lengths = new float[PipePulseRenderer.DIRECTIONS.length];
        int[] flows = new int[PipePulseRenderer.DIRECTIONS.length];
        for (Direction arm : PipePulseRenderer.DIRECTIONS) {
            if (!VEPipeShape.open(state, arm)) continue;
            var neighbor = level.getBlockState(pipe.getBlockPos().relative(arm));
            if (CorporeaNode.links(state, arm, neighbor)) {
                int index = arm.get3DDataValue();
                lengths[index] = .5f + exposedStub(neighbor, arm.getOpposite());
                flows[index] = route.flowThrough(arm);
            }
        }
        PipePulseRenderer.renderArms(pose, buffers, state, lengths, flows, phase, PERIOD, 0xd9a8ff);
    }

    private static float exposedStub(BlockState neighbor, Direction side) {
        if (neighbor.getBlock() instanceof CorporeaPipeBlock) return 0;
        if (neighbor.getBlock() instanceof CorporeaPortBlock) {
            Direction front = neighbor.getValue(CorporeaPortBlock.FACING);
            if (front == side) return 0;
            return front.getOpposite() == side ? 5 / 16f : 3 / 16f;
        }
        if (neighbor.getBlock() instanceof ArchivePylonBlock && side == Direction.DOWN) return 0;
        return 1 / 16f;
    }

    @Override
    public AABB getRenderBoundingBox(CorporeaPipeBlockEntity pipe) {
        return new AABB(pipe.getBlockPos()).inflate(5 / 16.0);
    }

    @Override public int getViewDistance() { return 48; }
}
