package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.viridianengineering.content.battery.BatteryControllerBlockEntity;
import dev.viridianengineering.network.VENetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The 200-tick battery failure cinematic (spec 3.4 / section 6): glowing fractures spread over the
 * column, shards of casing lift off and orbit (levitation), and in the last stretch the core collapses
 * into a white point before the detonation. Driven by the controller's synced UNSTABLE state, or by
 * {@link VENetwork.BatteryFailureStart} when the packet arrives first.
 */
public final class BatteryCinematics {

    private BatteryCinematics() {}

    private record Known(int layers, float fill, long deadline) {}

    private static final Map<BlockPos, Known> KNOWN = new HashMap<>();

    /** The ground fractures of each failing battery (by controller), built once from the terrain. */
    private static final Map<BlockPos, GroundCracks> CRACKS = new HashMap<>();

    /** Hands the detonation the cracks of the battery that just blew up near {@code center} (or null). */
    static GroundCracks takeCracks(net.minecraft.world.phys.Vec3 center) {
        BlockPos best = null;
        double bestD = 24 * 24;
        for (var e : CRACKS.entrySet()) {
            GroundCracks g = e.getValue();
            double dx = g.cx - center.x, dz = g.cz - center.z, d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        return best == null ? null : CRACKS.remove(best);
    }

    public static void start(VENetwork.BatteryFailureStart packet) {
        KNOWN.put(packet.controller(), new Known(packet.validLayers(), packet.fillFraction(), packet.detonationTick()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.blockPosition().closerThan(packet.controller(), 256))
            mc.player.displayClientMessage(Component.translatable("viridian_engineering.battery.unstable_warning",
                    Math.round(packet.fillFraction() * 100)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    static final int CRACK = 0x7dffb0, CORE = 0xf4fff8, SHARD = 0x9fe8c0;

    public static class Renderer extends SafeBlockEntityRenderer<BatteryControllerBlockEntity> {

        public Renderer(BlockEntityRendererProvider.Context context) {}

        @Override
        protected void renderSafe(BatteryControllerBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (be.getLevel() == null) return;
            Known k = KNOWN.get(be.getBlockPos());
            boolean unstable = be.isUnstable() || (k != null && be.getLevel().getGameTime() < k.deadline);
            if (!unstable) { CRACKS.remove(be.getBlockPos()); return; }
            long deadline = be.isUnstable() ? be.detonationTick() : k.deadline;
            int layers = Math.max(1, be.isUnstable() ? be.failureLayers() : k.layers);
            float time = be.getLevel().getGameTime() + pt;
            float total = 200f;
            float p = Mth.clamp(1f - (deadline - time) / total, 0f, 1f);

            BlockPos base = be.baseCenter();
            BlockPos origin = be.getBlockPos();
            float cx = base.getX() - origin.getX() + 0.5f, cz = base.getZ() - origin.getZ() + 0.5f;
            float y0 = base.getY() - origin.getY(), height = layers;
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = ms.last().pose();

            fractures(vc, m, be.getBlockPos().asLong(), cx, y0, cz, height, p, time);
            shards(vc, ms, be.getBlockPos().asLong(), cx, y0, cz, height, p, time);

            // the fractures run off the column and out across the ground, further the fuller it is
            float fill = be.isUnstable() ? (float) be.fillFraction() : (k != null ? k.fill : 1f);
            GroundCracks g = CRACKS.computeIfAbsent(be.getBlockPos(), pos -> GroundCracks.build(be.getLevel(),
                    base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 1.55f, 12f + 30f * fill, pos.asLong(), 2, 6));
            float spread = Mth.clamp((p - 0.04f) / 0.86f, 0f, 1f);
            float reveal = g.maxDistance * spread * spread * (3 - 2 * spread);
            float flicker = 0.75f + 0.25f * Mth.sin(time * 0.9f + 1.7f);
            VertexConsumer glow = buffers.getBuffer(VERenderTypes.GLOW);
            g.render(glow, ms.last().pose(), cx, (float) (g.cy - origin.getY()), cz, reveal, (0.45f + 0.55f * p) * flicker,
                    p > 0.8f ? CORE : 0xc8ffe0, CRACK, 1f + 0.6f * p);

            core(buffers, ms, cx, y0 + height / 2f, cz, height, p, time);
            particles(be, g, reveal, cx, y0, cz, height, p);
        }

        /** Mana wisps venting from the ground cracks, and in the last stretch streaming into the core. */
        private static void particles(BatteryControllerBlockEntity be, GroundCracks g, float reveal, float cx, float y0,
                                      float cz, float h, float p) {
            if (!FxParticles.once(be)) return;
            var level = be.getLevel();
            RandomSource r = level.random;
            int vents = (int) (1 + 5 * p);
            for (int i = 0; i < vents; i++) {
                var pt = g.randomPoint(r, reveal);
                if (pt != null) FxParticles.risingWisp(level, FxParticles.world(be, g.cx - be.getBlockPos().getX() + pt.x,
                        g.cy - be.getBlockPos().getY() + pt.y, g.cz - be.getBlockPos().getZ() + pt.z), r.nextInt(3) == 0 ? CRACK : ManaFx.MANA);
            }
            if (p > 0.7f) {
                var core = FxParticles.world(be, cx, y0 + h / 2f, cz);
                for (int i = 0; i < 4; i++) {
                    double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u), d = 3 + r.nextDouble() * 3;
                    var from = core.add(s * Math.cos(th) * d, u * d, s * Math.sin(th) * d);
                    FxParticles.wisp(level, from, core.subtract(from).scale(0.09), r.nextBoolean() ? CORE : ManaFx.MANA,
                            0.25f + r.nextFloat() * 0.3f, 0.5f);
                }
            }
        }

        /** Light-filled cracks crawling over the four sides of the 3-wide column, more of them as p grows. */
        private static void fractures(VertexConsumer vc, Matrix4f m, long seed, float cx, float y0, float cz, float h, float p, float time) {
            RandomSource r = RandomSource.create(seed);
            int count = (int) (12 + 140 * p);
            float flicker = 0.7f + 0.3f * (float) Math.sin(time * 0.9f);
            for (int i = 0; i < count; i++) {
                int side = r.nextInt(4);
                float u = r.nextFloat() * 3f - 1.5f, v = r.nextFloat() * h;
                float len = 0.3f + r.nextFloat() * (0.4f + 1.2f * p);
                float ang = r.nextFloat() * Mth.TWO_PI;
                float du = Mth.cos(ang) * len, dv = Mth.sin(ang) * len, w = 0.03f + 0.03f * p;
                float off = 1.5f + 0.01f + 0.002f * (i % 5);
                Vector3f a = onSide(side, cx, y0, cz, off, u, v), b = onSide(side, cx, y0, cz, off, u + du, v + dv);
                Vector3f n = onSide(side, 0, 0, 0, 0, -dv / len * w, du / len * w); // perpendicular in the face plane
                FxDraw.quad(vc, m, new Vector3f(a).sub(n), new Vector3f(b).sub(n), new Vector3f(b).add(n), new Vector3f(a).add(n),
                        CRACK, (0.35f + 0.65f * p) * flicker);
            }
        }

        /** A point on one side face of the column: side 0..3 = north, east, south, west; u across, v up. */
        private static Vector3f onSide(int side, float cx, float y0, float cz, float off, float u, float v) {
            return switch (side) {
                case 0 -> new Vector3f(cx + u, y0 + v, cz - off);
                case 1 -> new Vector3f(cx + off, y0 + v, cz + u);
                case 2 -> new Vector3f(cx - u, y0 + v, cz + off);
                default -> new Vector3f(cx - off, y0 + v, cz - u);
            };
        }

        /** Casing shards that lift off the column and orbit it, rising faster as the end nears. */
        private static void shards(VertexConsumer vc, PoseStack ms, long seed, float cx, float y0, float cz, float h, float p, float time) {
            RandomSource r = RandomSource.create(seed ^ 0x5DEECE66DL);
            int count = (int) (40 * Mth.clamp((p - 0.1f) / 0.6f, 0f, 1f));
            for (int i = 0; i < count; i++) {
                float phase = r.nextFloat() * Mth.TWO_PI, speed = 0.02f + r.nextFloat() * 0.04f;
                float radius = 1.9f + r.nextFloat() * 1.6f * p;
                float start = r.nextFloat() * h, rise = p * p * (1.5f + r.nextFloat() * 3f);
                float ang = phase + time * speed * (1 + 2 * p);
                // in the collapse the shards are pulled back into the core
                float pull = Mth.clamp((p - 0.8f) / 0.2f, 0f, 1f);
                float x = cx + Mth.cos(ang) * radius * (1 - pull), z = cz + Mth.sin(ang) * radius * (1 - pull);
                float y = Mth.lerp(pull, y0 + start + rise + 0.2f * Mth.sin(time * 0.1f + phase), y0 + h / 2f);
                ms.pushPose();
                ms.translate(x, y, z);
                ms.mulPose(Axis.YP.rotation(time * 0.05f + phase));
                ms.mulPose(Axis.XP.rotation(time * 0.07f + phase));
                float s = (0.06f + r.nextFloat() * 0.12f) * (1 - 0.8f * pull);
                FxDraw.box(vc, ms.last().pose(), -s, -s * 0.4f, -s, s, s * 0.4f, s, SHARD, 0.75f);
                ms.popPose();
            }
        }

        /** The core: a mana sphere that swells while the battery fails, then collapses to a blinding point. */
        private static void core(MultiBufferSource buffers, PoseStack ms, float x, float y, float z, float h, float p, float time) {
            float swell = 0.4f + 1.4f * Mth.clamp(p / 0.75f, 0f, 1f);
            float collapse = Mth.clamp((p - 0.75f) / 0.25f, 0f, 1f);
            float radius = Mth.lerp(collapse * collapse, swell, 0.08f);
            float pulse = 1f + 0.08f * (float) Math.sin(time * (0.3f + p));
            VertexConsumer halo = ManaFx.buffer(buffers);
            ManaFx.orb(ms, halo, x, y, z, radius * 2.6f * pulse, ManaFx.MANA, 0.2f + 0.25f * p);
            // the core itself is a real sphere: a boiling mana shell round a white heart
            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            Matrix4f m = ms.last().pose();
            org.joml.Vector3f eye = FxMesh.camera(ms);
            FxMesh.sphere(vc, m, eye, x, y, z, radius * 0.55f * pulse, CORE, ManaFx.MANA_CORE, 0.6f, 0.25f, 0.08f, time * 2, 12);
            FxMesh.sphere(vc, m, eye, x, y, z, radius * pulse, ManaFx.MANA, CRACK, 0.12f + 0.12f * p, 4f, 0.06f + 0.12f * p, time, 16);
            FxMesh.sphere(vc, m, eye, x, y, z, radius * 1.5f * pulse, ManaFx.MANA, CORE, 0.04f + 0.05f * p, 8f, 0.03f, time * 0.7f, 16);
            org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0), c = new org.joml.Vector3f(x, y, z);
            FxMesh.ring(vc, m, c, up, radius * 2.0f, 0.12f + 0.1f * p, CRACK, 0.5f * p, 8, time * 0.15f);
            FxMesh.ring(vc, m, c, new org.joml.Vector3f(0.4f, 1f, 0.2f).normalize(), radius * 1.75f, 0.1f, CORE, 0.4f * p, 6, -time * 0.2f);
            if (collapse > 0.9f)
                FxMesh.sphere(vc, m, eye, x, y, z, 1f + 30f * (collapse - 0.9f), 0xffffff, CORE, 0.8f, 0.5f, 0f, time, 16);
        }

        @Override public boolean shouldRenderOffScreen(BatteryControllerBlockEntity be) { return true; }

        @Override public int getViewDistance() { return 160; }
    }
}
