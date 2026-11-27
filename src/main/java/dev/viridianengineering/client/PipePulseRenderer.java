package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlockEntity;
import dev.viridianengineering.content.pipe.VEPipeShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Draws both pipe tiers' light on the exposed faces of their arms. */
public final class PipePulseRenderer implements BlockEntityRenderer<TerrasteelManaPipeBlockEntity> {

    static final Direction[] DIRECTIONS = Direction.values();
    private static final int TICKS_PER_BLOCK = 16;
    private static final float PULSE_LENGTH = .28f;
    private static final float PIPE_RADIUS = 4 / 16f + .004f;
    private static final float CASING_EDGE = 5 / 16f;
    private static final float WIDTH = .046f;
    /** Create's machine flange covers the last two model pixels of the pipe arm. */
    private static final float FLANGE_START = .5f - 2 / 16f;

    public static BlockEntityRenderer<TerrasteelManaPipeBlockEntity> mana(BlockEntityRendererProvider.Context context) {
        return new PipePulseRenderer();
    }

    @Override
    public void render(TerrasteelManaPipeBlockEntity pipe, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = pipe.getLevel();
        if (level == null || !(pipe.getBlockState().getBlock() instanceof VEPipeShape.Pipe shape)) return;
        BlockState state = pipe.getBlockState();
        float[] lengths = new float[DIRECTIONS.length];
        int[] flows = new int[DIRECTIONS.length];
        for (Direction arm : DIRECTIONS) {
            if (!VEPipeShape.open(state, arm) || !shape.linked(level, pipe.getBlockPos(), state, arm)) continue;
            int index = arm.get3DDataValue();
            var attachment = VEPipeShape.attachment(level, pipe.getBlockPos(), state, arm);
            lengths[index] = attachment == AttachmentTypes.DRAIN ? FLANGE_START : .5f;
            flows[index] = pipe.flowThrough(arm);
        }

        // At a shared face the outgoing +.5 and incoming -.5 differ by one full period.
        float phase = timePhase(level.getGameTime(), partialTick, 1);
        renderArms(pose, buffers, state, lengths, flows, phase, 1, 0x7dffb0);
    }

    static float timePhase(long gameTime, float partialTick, int period) {
        return (Math.floorMod(gameTime, TICKS_PER_BLOCK * period) + partialTick) / TICKS_PER_BLOCK;
    }

    static void renderArms(PoseStack pose, MultiBufferSource buffers, BlockState state, float[] lengths,
                           int[] flows, float phase, int period, int rgb) {
        int connected = 0;
        for (float length : lengths) {
            if (length > 0) connected++;
        }
        if (connected == 0) return;

        boolean junction = VEPipeShape.isJunction(state);
        boolean elbow = connected == 2 && VEPipeShape.straightAxis(state) == null;
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (Direction normal : DIRECTIONS) {
            boolean innerTurn = VEPipeShape.open(state, normal);
            // The outside of a two-arm bend follows the two rear faces around their common edge.
            boolean outerTurn = elbow && lengths[normal.getOpposite().get3DDataValue()] > 0;
            float fold = innerTurn ? (junction ? CASING_EDGE : PIPE_RADIUS) : outerTurn ? -PIPE_RADIUS : 0;
            float start = junction ? CASING_EDGE : innerTurn || outerTurn ? fold : WIDTH;
            Direction centerStrip = null;
            boolean centerHasFlow = false;
            for (Direction arm : DIRECTIONS) {
                float end = lengths[arm.get3DDataValue()];
                if (end == 0 || normal.getAxis() == arm.getAxis()) continue;
                int flow = flows[arm.get3DDataValue()];
                line(vertices, matrix, arm, normal, start, end, .24f, 0, rgb);
                centerStrip = arm;
                centerHasFlow |= flow != 0;
                if (flow == 0) continue;

                // The virtual arc length at every join is shared by both sides, even when the
                // visible ribbon folds around an elbow or disappears behind the junction casing.
                for (int n = -1; n <= 2; n++) {
                    float a = (phase - n * period - PULSE_LENGTH) * flow;
                    float b = (phase - n * period) * flow;
                    float lo = Math.max(start, onSurface(Math.min(a, b), fold));
                    float hi = Math.min(end, onSurface(Math.max(a, b), fold));
                    line(vertices, matrix, arm, normal, lo, hi, .92f, .002f, rgb);
                }
            }
            // Each face owns a single center tile, so crossing ribbons share its surface.
            if (!junction && !innerTurn && !outerTurn && centerStrip != null) {
                quad(vertices, matrix, centerStrip, normal, -WIDTH, WIDTH, PIPE_RADIUS, .24f, rgb);
                float centerPhase = phase % period;
                if (centerHasFlow && (centerPhase < PULSE_LENGTH + WIDTH || centerPhase >= period - WIDTH))
                    quad(vertices, matrix, centerStrip, normal, -WIDTH, WIDTH, PIPE_RADIUS + .002f, .92f, rgb);
            }
        }
    }

    private static float onSurface(float travel, float fold) {
        return travel >= .5f ? travel : fold + travel * (1 - 2 * fold);
    }

    private static void line(VertexConsumer vertices, Matrix4f matrix, Direction arm, Direction normal,
                             float lo, float hi, float alpha, float lift, int rgb) {
        if (hi > lo) quad(vertices, matrix, arm, normal, lo, hi, PIPE_RADIUS + lift, alpha, rgb);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, Direction arm, Direction normal,
                             float lo, float hi, float radius, float alpha, int rgb) {
        // normal × arm points across the ribbon and gives the quad an outward winding.
        float x = (normal.getStepY() * arm.getStepZ() - normal.getStepZ() * arm.getStepY()) * WIDTH;
        float y = (normal.getStepZ() * arm.getStepX() - normal.getStepX() * arm.getStepZ()) * WIDTH;
        float z = (normal.getStepX() * arm.getStepY() - normal.getStepY() * arm.getStepX()) * WIDTH;
        float lx = .5f + arm.getStepX() * lo + normal.getStepX() * radius;
        float ly = .5f + arm.getStepY() * lo + normal.getStepY() * radius;
        float lz = .5f + arm.getStepZ() * lo + normal.getStepZ() * radius;
        float hx = .5f + arm.getStepX() * hi + normal.getStepX() * radius;
        float hy = .5f + arm.getStepY() * hi + normal.getStepY() * radius;
        float hz = .5f + arm.getStepZ() * hi + normal.getStepZ() * radius;
        vertex(vertices, matrix, lx - x, ly - y, lz - z, alpha, rgb);
        vertex(vertices, matrix, hx - x, hy - y, hz - z, alpha, rgb);
        vertex(vertices, matrix, hx + x, hy + y, hz + z, alpha, rgb);
        vertex(vertices, matrix, lx + x, ly + y, lz + z, alpha, rgb);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z, float alpha, int rgb) {
        vertices.addVertex(matrix, x, y, z).setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
