package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.viridianengineering.content.cannon.ArcaneBoltEntity;
import dev.viridianengineering.content.cannon.GaiaCannonBlockEntity;
import dev.viridianengineering.content.reactor.ReactorShaftPortBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/** Renderers for the reactor shaft port, the cannon (beam and charge orb) and the arcane bolt. */
public final class PhaseFourRenderers {

    private PhaseFourRenderers() {}

    static final int GAIA_CORE = 0xfff2c4, GAIA_HALO = 0xff5fb8;

    // ------------------------------------------------------------------ shaft port

    public static class ShaftPort extends KineticBlockEntityRenderer<ReactorShaftPortBlockEntity> {
        public ShaftPort(BlockEntityRendererProvider.Context context) { super(context); }

        @Override
        protected SuperByteBuffer getRotatedModel(ReactorShaftPortBlockEntity be, BlockState state) {
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, state.getValue(BlockStateProperties.FACING));
        }
    }

    public static SimpleBlockEntityVisualizer.Factory<ReactorShaftPortBlockEntity> shaftVisual() {
        return (context, be, partialTick) -> new OrientedRotatingVisual<>(context, be, partialTick,
                Direction.SOUTH, be.getBlockState().getValue(BlockStateProperties.FACING), Models.partial(AllPartialModels.SHAFT_HALF));
    }

    // ------------------------------------------------------------------ cannon

    public static class Cannon extends SafeBlockEntityRenderer<GaiaCannonBlockEntity> {
        /** Distance (blocks) from the centre block's middle to the core's tip, along the facing. */
        static final double CORE_TIP = 9.5 / 16;

        public Cannon(BlockEntityRendererProvider.Context context) {}

        /** Solid mana laser and charging orb, both built from Botania's mana-wisp glow. */
        @Override
        protected void renderSafe(GaiaCannonBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTicks;
            Direction f = be.facing();
            renderFocus(be, f, time, ms, buffers, light);
            net.minecraft.world.phys.Vec3 dir = net.minecraft.world.phys.Vec3.atLowerCornerOf(f.getNormal());
            // the server ray starts just outside the 3x3x3 (be.muzzle()); visually it all leaves the core's tip
            net.minecraft.world.phys.Vec3 tip = new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5).add(dir.scale(CORE_TIP));
            net.minecraft.world.phys.Vec3 rayStart = be.muzzle().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos()));
            org.joml.Vector3f eyeF = FxMesh.camera(ms);
            net.minecraft.world.phys.Vec3 cam = new net.minecraft.world.phys.Vec3(eyeF.x, eyeF.y, eyeF.z);
            org.joml.Vector3f axis = new org.joml.Vector3f(f.getStepX(), f.getStepY(), f.getStepZ());
            org.joml.Vector3f tipF = new org.joml.Vector3f((float) tip.x, (float) tip.y, (float) tip.z);
            float len = be.beamLength();
            if (len > 0) {
                net.minecraft.world.phys.Vec3 end = rayStart.add(dir.scale(len));
                beam(be, ms, buffers, time, tip, end, cam, axis, tipF, eyeF);
            }
            float c = be.charge();
            if (c > 0) charge(be, ms, buffers, time, c, tipF, axis, eyeF);
            release(be, ms, buffers, time, c, tipF, axis, eyeF);
        }

        // -------------------------------------------------------------- beam mode

        private static void beam(GaiaCannonBlockEntity be, PoseStack ms, MultiBufferSource buffers, float time,
                                 net.minecraft.world.phys.Vec3 tip, net.minecraft.world.phys.Vec3 end, net.minecraft.world.phys.Vec3 cam,
                                 org.joml.Vector3f axis, org.joml.Vector3f tipF, org.joml.Vector3f eye) {
            float pulse = 0.88f + 0.12f * (float) Math.sin(time * 1.1f);
            float L = (float) tip.distanceTo(end);
            org.joml.Vector3f endF = new org.joml.Vector3f((float) end.x, (float) end.y, (float) end.z);
            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            Matrix4f m = ms.last().pose();
            // soft glow round the muzzle and the impact
            ManaFx.orb(ms, vc, tip.x, tip.y, tip.z, 1.4f * pulse, ManaFx.GAIA, 0.4f);
            ManaFx.orb(ms, vc, end.x, end.y, end.z, 1.6f * pulse, ManaFx.GAIA, 0.45f);
            int rings = Math.max(4, (int) (L / 1.5f));
            // nested tubes: soft pink sheath, mana-blue body, white-hot core
            FxMesh.cone(vc, m, eye, tipF, axis, L, 0.6f * pulse, 0.55f * pulse, GAIA_HALO, ManaFx.GAIA, 0.22f, 0.18f, 0.1f, 0.12f, time * 3f, 16, rings);
            FxMesh.cone(vc, m, eye, tipF, axis, L, 0.3f, 0.27f, ManaFx.MANA, ManaFx.MANA, 0.4f, 0.32f, 0.25f, 0.06f, time * 4f, 12, rings);
            FxMesh.cone(vc, m, eye, tipF, axis, L, 0.12f, 0.11f, 0xffffff, ManaFx.MANA_CORE, 0.8f, 0.7f, 0.5f, 0f, time, 10, rings);
            // twin helices winding down the beam
            FxMesh.helix(vc, m, tipF, axis, L, 0.62f, L / 3.2f, time * 0.45f, 0.11f, GAIA_CORE, 0.85f);
            FxMesh.helix(vc, m, tipF, axis, L, 0.62f, L / 3.2f, time * 0.45f + Mth.PI, 0.11f, ManaFx.GAIA, 0.85f);
            // pulse rings travelling out along it
            for (int k = 0; k < 5; k++) {
                float s = (time * 0.035f + k / 5f) % 1f;
                org.joml.Vector3f c = new org.joml.Vector3f(tipF).add(new org.joml.Vector3f(axis).mul(s * L));
                FxMesh.ring(vc, m, c, axis, 0.95f - 0.35f * s, 0.22f, ManaFx.MANA_CORE, 0.75f * (1f - s), 0, 0);
            }
            // three rotating rune rings at the muzzle
            float[] at = {0.35f, 0.9f, 1.55f}, rad = {1.15f, 0.9f, 0.65f};
            for (int k = 0; k < 3; k++) {
                org.joml.Vector3f c = new org.joml.Vector3f(tipF).add(new org.joml.Vector3f(axis).mul(at[k]));
                float turn = time * (k % 2 == 0 ? 0.12f : -0.18f) * (1 + k);
                FxMesh.ring(vc, m, c, axis, rad[k], 0.16f, k == 1 ? GAIA_CORE : ManaFx.GAIA, 0.85f, 6 + 2 * k, turn);
                FxMesh.ring(vc, m, c, axis, rad[k] * 0.82f, 0.06f, 0xffffff, 0.5f, 0, 0);
            }
            FxMesh.sphere(vc, m, eye, tipF.x, tipF.y, tipF.z, 0.55f * pulse, 0xffffff, ManaFx.GAIA, 0.85f, 1.4f, 0.14f, time * 2, 10);
            // impact: a boiling sphere, a splash ring facing back up the beam, a flat ring
            org.joml.Vector3f back = new org.joml.Vector3f(axis).negate();
            FxMesh.sphere(vc, m, eye, endF.x, endF.y, endF.z, 0.5f * pulse, 0xffffff, GAIA_CORE, 0.95f, 0.4f, 0.15f, time * 3, 10);
            FxMesh.sphere(vc, m, eye, endF.x, endF.y, endF.z, 1.0f * pulse, ManaFx.GAIA, GAIA_HALO, 0.4f, 2.8f, 0.2f, time * 2, 12);
            float splash = (time * 0.12f) % 1f;
            FxMesh.ring(vc, m, endF, back, 0.6f + 1.6f * splash, 0.35f, GAIA_CORE, 0.8f * (1f - splash), 0, 0);
            FxMesh.ring(vc, m, endF, back, 1.3f, 0.2f, ManaFx.GAIA, 0.6f, 5, -time * 0.3f);

            if (FxParticles.once(be)) {
                var level = be.getLevel();
                var r = level.random;
                net.minecraft.world.phys.Vec3 hit = FxParticles.world(be, end.x, end.y, end.z);
                net.minecraft.world.phys.Vec3 backW = FxParticles.worldDir(be, new net.minecraft.world.phys.Vec3(back.x, back.y, back.z));
                for (int i = 0; i < 4; i++) {
                    net.minecraft.world.phys.Vec3 v = backW.scale(0.1 + r.nextDouble() * 0.2)
                            .add((r.nextDouble() - .5) * 0.35, (r.nextDouble() - .5) * 0.35, (r.nextDouble() - .5) * 0.35);
                    FxParticles.wisp(level, hit, v, r.nextBoolean() ? ManaFx.GAIA : GAIA_CORE, 0.25f + r.nextFloat() * 0.35f, 0.6f);
                }
                FxParticles.sparkle(level, hit.add((r.nextDouble() - .5), (r.nextDouble() - .5), (r.nextDouble() - .5)), GAIA_CORE, 1.5f, 5);
                // motes drifting along the beam
                net.minecraft.world.phys.Vec3 along = FxParticles.world(be, tip.x, tip.y, tip.z).lerp(hit, r.nextDouble());
                FxParticles.wisp(level, along, net.minecraft.world.phys.Vec3.ZERO, ManaFx.MANA_CORE, 0.2f + r.nextFloat() * 0.2f, 0.5f);
            }
        }

        // -------------------------------------------------------------- charged-shot mode

        private static void charge(GaiaCannonBlockEntity be, PoseStack ms, MultiBufferSource buffers, float time, float c,
                                   org.joml.Vector3f tip, org.joml.Vector3f axis, org.joml.Vector3f eye) {
            float r = 0.25f + 0.9f * c, wobble = 1f + 0.06f * (float) Math.sin(time * 0.9f);
            boolean full = c >= 0.99f;
            float throb = full ? 1f + 0.1f * (float) Math.sin(time * 1.6f) : 1f;
            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            Matrix4f m = ms.last().pose();
            ManaFx.orb(ms, vc, tip.x, tip.y, tip.z, r * 2.4f * wobble * throb, ManaFx.GAIA, 0.15f + 0.2f * c);
            // the orb: white heart, mana body with a pink rim, a thin outer shell
            FxMesh.sphere(vc, m, eye, tip.x, tip.y, tip.z, r * 0.45f * throb, 0xffffff, ManaFx.MANA_CORE, 0.7f, 0.3f, 0.1f, time * 2.5f, 12);
            FxMesh.sphere(vc, m, eye, tip.x, tip.y, tip.z, r * wobble * throb, ManaFx.MANA, ManaFx.GAIA, 0.18f, 3.5f,
                    0.08f + 0.1f * c, time * 1.5f, 16);
            FxMesh.sphere(vc, m, eye, tip.x, tip.y, tip.z, r * 1.45f * throb, ManaFx.GAIA, GAIA_HALO, 0.05f + 0.06f * c, 7f, 0.04f, time, 16);
            // gyroscope rings, spinning up with the charge
            float spin = time * (0.04f + 0.2f * c);
            for (int k = 0; k < 3; k++) {
                float a = spin * (1 + 0.4f * k) + k * 2.1f;
                org.joml.Vector3f ax = new org.joml.Vector3f(Mth.cos(a), Mth.sin(a * 0.7f + k), Mth.sin(a)).normalize();
                FxMesh.ring(vc, m, tip, ax, r * (1.7f + 0.2f * k), 0.07f + 0.07f * c, k == 1 ? GAIA_CORE : ManaFx.GAIA,
                        0.4f + 0.5f * c, 6 + 2 * k, time * 0.3f);
            }
            // a rune ring in front of the tip, facing the line of fire
            FxMesh.ring(vc, m, new org.joml.Vector3f(tip).add(new org.joml.Vector3f(axis).mul(r * 1.6f + 0.3f)), axis,
                    0.6f + 0.6f * c, 0.15f, GAIA_CORE, 0.7f * c, 8, time * 0.2f);
            // lightning arcs crawling off the orb, re-struck every two ticks
            long strike = (long) (time / 2f);
            int arcs = 1 + (int) (4 * c);
            for (int k = 0; k < arcs; k++) {
                net.minecraft.util.RandomSource rs = net.minecraft.util.RandomSource.create(strike * 31 + k + be.getBlockPos().asLong());
                org.joml.Vector3f d = new org.joml.Vector3f(rs.nextFloat() - .5f, rs.nextFloat() - .5f, rs.nextFloat() - .5f).normalize();
                org.joml.Vector3f from = new org.joml.Vector3f(tip).add(new org.joml.Vector3f(d).mul(r * 0.8f));
                org.joml.Vector3f to = new org.joml.Vector3f(tip).add(new org.joml.Vector3f(d).mul(r * 2f + 0.6f + c));
                FxMesh.arc(vc, m, eye, from, to, rs.nextLong(), 0.35f, 0.045f, 0xffffff, ManaFx.GAIA, 0.6f + 0.4f * c);
            }

            if (FxParticles.once(be)) {
                var level = be.getLevel();
                var rnd = level.random;
                net.minecraft.world.phys.Vec3 centre = FxParticles.world(be, tip.x, tip.y, tip.z);
                int n = 1 + (int) (4 * c);
                for (int i = 0; i < n; i++) {
                    double u = rnd.nextDouble() * 2 - 1, th = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u), d = 2.2 + rnd.nextDouble();
                    net.minecraft.world.phys.Vec3 from = centre.add(s * Math.cos(th) * d, u * d, s * Math.sin(th) * d);
                    FxParticles.wisp(level, from, centre.subtract(from).scale(0.12), rnd.nextBoolean() ? ManaFx.GAIA : ManaFx.MANA,
                            0.2f + rnd.nextFloat() * 0.25f, 0.45f);
                }
            }
        }

        /** Last charge seen per cannon, and when it last fired, for the muzzle shockwave on release. */
        private static final java.util.Map<net.minecraft.core.BlockPos, float[]> SHOTS = new java.util.HashMap<>();

        /** On the falling edge (a charged shot leaves): a muzzle flash sphere and two shock rings. */
        private static void release(GaiaCannonBlockEntity be, PoseStack ms, MultiBufferSource buffers, float time, float c,
                                    org.joml.Vector3f tip, org.joml.Vector3f axis, org.joml.Vector3f eye) {
            float[] s = SHOTS.computeIfAbsent(be.getBlockPos().immutable(), p -> new float[]{0, -100, 0});
            if (s[0] >= 0.25f && c <= 0.001f) { s[1] = time; s[2] = s[0]; }
            s[0] = c;
            float t = time - s[1];
            if (t < 0 || t > 12) return;
            float k = t / 12f, fade = 1f - k, size = 0.5f + s[2];
            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            Matrix4f m = ms.last().pose();
            FxMesh.sphere(vc, m, eye, tip.x, tip.y, tip.z, size * (0.6f + 1.8f * ManaExplosions.easeOut(k)), 0xffffff, ManaFx.GAIA,
                    fade * fade, 2f, 0.1f, time * 3, 12);
            for (int i = 0; i < 2; i++) {
                org.joml.Vector3f c0 = new org.joml.Vector3f(tip).add(new org.joml.Vector3f(axis).mul(0.4f + i * 1.1f * k));
                FxMesh.ring(vc, m, c0, axis, size * (1f + (3f - i) * ManaExplosions.easeOut(k)), 0.3f, i == 0 ? GAIA_CORE : ManaFx.GAIA,
                        0.9f * fade, 0, 0);
            }
        }

        /**
         * The gaia core turning in its claws and three shards orbiting it. Idle: slow. Charging or
         * firing: the core spins up and the shards race round. Turned to the cannon's facing.
         */
        private void renderFocus(GaiaCannonBlockEntity be, Direction f, float time, PoseStack ms, MultiBufferSource buffers, int light) {
            BlockState state = be.getBlockState();
            float activity = Math.max(be.charge(), be.beamLength() > 0 ? 1f : 0f);
            float spin = time * (0.5f + 6f * activity);
            org.joml.Quaternionf toFacing = f.getRotation(); // turns the up-authored parts to the facing
            int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            float bob = 0.04f * (float) Math.sin(time * 0.08f);

            CachedBuffers.partial(VEPartials.crystal(VEPartials.CANNON_CORE), state)
                    .center().rotate(toFacing).translate(0, bob, 0).rotateYDegrees(spin).uncenter()
                    .light(full).renderInto(ms, FormedRenderers.crystal(buffers));
            CachedBuffers.partial(VEPartials.crystal(VEPartials.CANNON_SHARDS), state)
                    .center().rotate(toFacing).translate(0, 0.05f * (float) Math.sin(time * 0.11f), 0)
                    .rotateYDegrees(-spin * 1.6f).uncenter()
                    .light(full).renderInto(ms, FormedRenderers.crystal(buffers));
        }

        @Override public boolean shouldRenderOffScreen(GaiaCannonBlockEntity be) { return true; }

        @Override public int getViewDistance() { return 128; }
    }

    // ------------------------------------------------------------------ bolt

    public static class Bolt extends EntityRenderer<ArcaneBoltEntity> {
        public Bolt(EntityRendererProvider.Context context) { super(context); }

        /**
         * A 3D mana comet: a boiling orb (white heart, mana body, pink rim-lit shell) trailing a tapered
         * pink tail with a white core, and a dashed ring spinning round its line of flight.
         */
        @Override
        public void render(ArcaneBoltEntity e, float yaw, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light) {
            float time = e.tickCount + partialTicks;
            float c = e.charge(), r = 0.45f + 0.55f * c, wobble = 1f + 0.08f * (float) Math.sin(time * 1.3f);
            net.minecraft.world.phys.Vec3 mv = e.getDeltaMovement();
            org.joml.Vector3f fwd = mv.lengthSqr() < 1e-6 ? new org.joml.Vector3f(0, 1, 0)
                    : new org.joml.Vector3f((float) mv.x, (float) mv.y, (float) mv.z).normalize();
            org.joml.Vector3f back = new org.joml.Vector3f(fwd).negate(), at = new org.joml.Vector3f(0, 0.25f, 0);

            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            Matrix4f m = ms.last().pose();
            org.joml.Vector3f eye = FxMesh.camera(ms);
            ManaFx.orb(ms, vc, 0, 0.25, 0, r * 2.2f * wobble, ManaFx.GAIA, 0.3f);
            float tail = 2.5f + 3.5f * c;
            FxMesh.cone(vc, m, eye, at, back, tail, r * 0.95f, 0.02f, ManaFx.GAIA, GAIA_HALO, 0.4f, 0f, 0.15f, 0.15f, time * 4, 14, 8);
            FxMesh.cone(vc, m, eye, at, back, tail * 0.6f, r * 0.4f, 0.01f, 0xffffff, ManaFx.MANA_CORE, 0.7f, 0f, 0.4f, 0.05f, time * 4, 10, 6);
            FxMesh.sphere(vc, m, eye, at.x, at.y, at.z, r * 0.45f, 0xffffff, ManaFx.MANA_CORE, 0.7f, 0.3f, 0.1f, time * 2.5f, 10);
            FxMesh.sphere(vc, m, eye, at.x, at.y, at.z, r * wobble, ManaFx.MANA, ManaFx.GAIA, 0.2f, 3.5f, 0.14f, time * 2, 14);
            FxMesh.sphere(vc, m, eye, at.x, at.y, at.z, r * 1.45f, ManaFx.GAIA, GAIA_HALO, 0.06f, 7f, 0.05f, time, 14);
            FxMesh.ring(vc, m, at, fwd, r * 1.7f, 0.1f, GAIA_CORE, 0.7f, 6, time * 0.5f);
            super.render(e, yaw, partialTicks, ms, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ArcaneBoltEntity e) {
            return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;
        }
    }
}
