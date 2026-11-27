package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.viridianengineering.content.FormedPartBlock;
import dev.viridianengineering.content.battery.BatteryControllerBlockEntity;
import dev.viridianengineering.content.reactor.ReactorControllerBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Formed-multiblock renderers (Immersive-Engineering style): while a structure is formed its casing
 * blocks render nothing and the controller draws the whole structure from shaped piece models,
 * each placed and turned by its role. Ports and controllers stay as their own blocks.
 */
public final class FormedRenderers {

    private FormedRenderers() {}

    /** Y rotation (degrees, for rotateYDegrees) that turns a NORTH-facing piece to face {@code d}. */
    static float yaw(Direction d) {
        return switch (d) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    /** Corner pieces are modelled as the north-west corner; this turns them to the corner (sx, sz). */
    static float cornerYaw(int sx, int sz) {
        if (sx < 0 && sz < 0) return 0;
        if (sx > 0 && sz < 0) return -90;
        if (sx > 0) return 180;
        return 90;
    }

    static int light(Level level, BlockPos pos) {
        int best = 0;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.getBlockState(n).isSolidRender(level, n)) best = Math.max(best, LevelRenderer.getLightColor(level, n));
        }
        return best;
    }

    static VertexConsumer solid(MultiBufferSource buffers) { return buffers.getBuffer(RenderType.cutoutMipped()); }

    /** Crystals are slightly see-through (like the Archive Pylon gem): translucent layer, full bright. */
    static VertexConsumer crystal(MultiBufferSource buffers) { return buffers.getBuffer(RenderType.translucent()); }

    /**
     * Draws {@code partial} (and its crystal half) at {@code pos} (relative to {@code origin}) turned by
     * {@code yaw}. Each draw fetches its own buffer right before use: asking a buffer source for one
     * render type can end the batch of another, so a held consumer may already be closed.
     */
    static void piece(PartialModel partial, Level level, BlockPos origin, BlockPos pos, float yaw, BlockState state,
                      PoseStack ms, MultiBufferSource buffers, int lightOverride) {
        BlockPos rel = pos.subtract(origin);
        CachedBuffers.partial(partial, state)
                .translate(rel.getX(), rel.getY(), rel.getZ())
                .center().rotateYDegrees(yaw).uncenter()
                .light(lightOverride >= 0 ? lightOverride : light(level, pos))
                .renderInto(ms, solid(buffers));
        PartialModel gem = VEPartials.crystal(partial);
        if (gem != null)
            CachedBuffers.partial(gem, state)
                    .translate(rel.getX(), rel.getY(), rel.getZ())
                    .center().rotateYDegrees(yaw).uncenter()
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ms, crystal(buffers));
    }

    static boolean formedAt(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.hasProperty(FormedPartBlock.FORMED) && s.getValue(FormedPartBlock.FORMED);
    }

    // ================================================================== reactor

    public static class Reactor extends SafeBlockEntityRenderer<ReactorControllerBlockEntity> {
        public Reactor(BlockEntityRendererProvider.Context c) {}

        @Override
        protected void renderSafe(ReactorControllerBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            Level level = be.getLevel();
            if (level == null) return;
            if (!be.isFormed()) { ReactorFlow.forget(be.getBlockPos()); return; }
            BlockPos origin = be.getBlockPos(), c = be.coreCenter();
            BlockState state = be.getBlockState();
            int r = be.radius();
            for (int dy = -1; dy <= 0; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos p = c.offset(dx, dy, dz);
                        if (!formedAt(level, p)) continue; // ports, the controller and air draw themselves
                        switch (ReactorControllerBlockEntity.slotAt(dx, dy, dz, r)) {
                            case OUTER -> {
                                boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                                PartialModel m = corner ? (dy == 0 ? VEPartials.REACTOR_CORNER_HIGH : VEPartials.REACTOR_CORNER_LOW)
                                        : (dy == 0 ? VEPartials.REACTOR_WALL_HIGH : VEPartials.REACTOR_WALL_LOW);
                                float yaw = corner ? cornerYaw(dx, dz) : yaw(outward(dx, dz, r));
                                piece(m, level, origin, p, yaw, state, ms, buffers, -1);
                            }
                            case INNER -> {
                                boolean corner = Math.abs(dx) == r - 1 && Math.abs(dz) == r - 1;
                                // inner pieces face the pit: modelled with the pit to the SOUTH (and EAST for the corner)
                                float yaw = corner ? cornerYaw(dx, dz) : yaw(outward(dx, dz, r - 1));
                                piece(corner ? VEPartials.REACTOR_INNER_CORNER : VEPartials.REACTOR_INNER, level, origin, p, yaw, state, ms, buffers, -1);
                            }
                            case PEDESTAL -> piece(VEPartials.REACTOR_PEDESTAL, level, origin, p, 0, state, ms, buffers, -1);
                            default -> {}
                        }
                    }

            // the floating core: bobs and turns. While running the frame round it spins up hard (smoothly,
            // and winds down again when it stops); the core turns with it, slower.
            float time = level.getGameTime() + pt;
            float angle = ReactorFlow.spin(origin, be.isRunning(), time);
            BlockPos rel = c.subtract(origin);
            ms.pushPose();
            ms.translate(rel.getX(), rel.getY() + 0.15 * Math.sin(time * 0.08), rel.getZ());
            CachedBuffers.partial(VEPartials.crystal(VEPartials.REACTOR_CORE), state)
                    .center().rotateYDegrees(angle * 0.45f).uncenter()
                    .light(LightTexture.FULL_BRIGHT).renderInto(ms, crystal(buffers));
            CachedBuffers.partial(VEPartials.REACTOR_RING, state)
                    .center().rotateYDegrees(-angle).rotateXDegrees(12).uncenter()
                    .light(LightTexture.FULL_BRIGHT).renderInto(ms, solid(buffers));
            CachedBuffers.partial(VEPartials.crystal(VEPartials.REACTOR_RING), state)
                    .center().rotateYDegrees(-angle).rotateXDegrees(12).uncenter()
                    .light(LightTexture.FULL_BRIGHT).renderInto(ms, crystal(buffers));
            ms.popPose();
            // mana streaming from the four corner spires into a collection ring round the core
            boolean newTick = FxParticles.once(be);
            ReactorFlow.render(origin, new org.joml.Vector3f(rel.getX() + .5f, rel.getY() + .5f, rel.getZ() + .5f), r,
                    be.isRunning(), newTick, time, level.random, ms, buffers);
            if (newTick) wisps(be, rel, r);
        }

        /**
         * Mana wisps rising out of the bowl like off a Botania mana pool: off the floating core, the
         * glowing channel of the inner ring and the pit floor. A thin trickle while formed and idle,
         * a steady stream while it runs (more for bigger bowls).
         */
        private static void wisps(ReactorControllerBlockEntity be, BlockPos rel, int r) {
            Level level = be.getLevel();
            var rnd = level.random;
            boolean run = be.isRunning();
            int count = run ? 2 + be.factor() : (rnd.nextFloat() < 0.25f ? 1 : 0);
            double cx = rel.getX() + 0.5, cy = rel.getY(), cz = rel.getZ() + 0.5;
            for (int i = 0; i < count; i++) {
                double x, y, z;
                int pick = rnd.nextInt(3);
                if (pick == 0) { // off the core
                    x = cx + (rnd.nextDouble() - .5) * 0.8; y = cy + 0.3 + rnd.nextDouble() * 0.5; z = cz + (rnd.nextDouble() - .5) * 0.8;
                } else if (pick == 1) { // along the inner ring's mana channel
                    double a = rnd.nextDouble() * Math.PI * 2, rad = (r - 1) + (rnd.nextDouble() - .5) * 0.4;
                    double s = Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a))); // square ring
                    x = cx + Math.cos(a) / s * rad; y = cy - 0.1; z = cz + Math.sin(a) / s * rad;
                } else { // out of the pit
                    double a = rnd.nextDouble() * Math.PI * 2, rad = 1 + rnd.nextDouble() * Math.max(0.5, r - 2.5);
                    x = cx + Math.cos(a) * rad; y = cy - 0.9; z = cz + Math.sin(a) * rad;
                }
                FxParticles.risingWisp(level, FxParticles.world(be, x, y, z),
                        rnd.nextInt(4) == 0 ? ManaFx.TERRA : rnd.nextInt(3) == 0 ? ManaFx.MANA_CORE : ManaFx.MANA);
            }
        }

        /** For a position on ring {@code r}, the side it faces away from the centre. */
        static Direction outward(int dx, int dz, int r) {
            if (dz == -r) return Direction.NORTH;
            if (dz == r) return Direction.SOUTH;
            if (dx == r) return Direction.EAST;
            return Direction.WEST;
        }

        @Override public boolean shouldRenderOffScreen(ReactorControllerBlockEntity be) { return true; }
    }

    // ================================================================== battery

    public static class Battery extends BatteryCinematics.Renderer {
        public Battery(BlockEntityRendererProvider.Context c) { super(c); }

        @Override
        protected void renderSafe(BatteryControllerBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            Level level = be.getLevel();
            if (level != null && be.isFormed()) {
                BlockPos origin = be.getBlockPos(), base = be.baseCenter();
                BlockState state = be.getBlockState();
                int layers = be.validLayers();
                for (int y = 0; y < layers; y++)
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos p = base.offset(dx, y, dz);
                            if (!formedAt(level, p)) continue;
                            if (dx == 0 && dz == 0) continue; // hidden inside
                            boolean corner = dx != 0 && dz != 0;
                            piece(corner ? VEPartials.BATTERY_CORNER : VEPartials.BATTERY_SIDE, level, origin, p,
                                    corner ? cornerYaw(dx, dz) : yaw(Reactor.outward(dx, dz, 1)), state, ms, buffers, -1);
                        }
                BlockPos top = base.above(layers - 1);
                piece(VEPartials.BATTERY_CAP, level, origin, top, 0, state, ms, buffers, light(level, top.above()));
                // the stored mana, glowing behind the windows of every side panel
                BlockPos rel = base.subtract(origin);
                ManaFx.batteryLevel(ms, buffers, rel.getX(), rel.getY(), rel.getZ(), layers, be.fillFraction(),
                        level.getGameTime() + pt);
            }
            super.renderSafe(be, pt, ms, buffers, light, overlay);
        }
    }
}
