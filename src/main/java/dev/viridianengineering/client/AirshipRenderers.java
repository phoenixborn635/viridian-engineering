package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.airship.BifrostProjectorBlockEntity;
import dev.viridianengineering.content.airship.FlugelNavigationCoreBlockEntity;
import dev.viridianengineering.content.airship.GaiaThrusterBlockEntity;
import dev.viridianengineering.content.airship.LevitationPylonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Renderers of the Phase 5 airship blocks. */
public final class AirshipRenderers {

    private AirshipRenderers() {}

    public static final ResourceLocation MAGIC_CIRCLE = ViridianEngineering.asResource("textures/entity/levitation_magic_circle.png");
    public static final ResourceLocation MAGIC_CIRCLE_INNER = ViridianEngineering.asResource("textures/entity/levitation_magic_circle_inner.png");

    static float time(net.minecraft.world.level.block.entity.BlockEntity be, float pt) {
        return (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + pt;
    }

    // ------------------------------------------------------------------ levitation pylon

    /**
     * The pylon's floating gem (the shared Botania-style pylon crystal) in the upper half's cradle,
     * and, while it lifts a ship, a slowly turning botanical rune circle under the ship's keel.
     */
    public static class Pylon extends SafeBlockEntityRenderer<LevitationPylonBlockEntity> {
        private final VEPylonModel model;

        public Pylon(BlockEntityRendererProvider.Context context) {
            model = new VEPylonModel(context.bakeLayer(VEPylonModel.LEVITATION_LAYER));
        }

        @Override
        protected void renderSafe(LevitationPylonBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            LevitationPylonBlockEntity.Mode mode = be.mode();
            boolean on = mode != LevitationPylonBlockEntity.Mode.IDLE;
            boolean active = on && mode != LevitationPylonBlockEntity.Mode.HOVER;
            float t = time(be, pt);
            float spin = t * (on ? (active ? 1.4f : 0.8f) : 0.25f);
            ms.pushPose();
            // gem: bottom tip 1 px above the cradle in the upper half (cradle top at y = 1 + 10/16)
            ms.translate(0, 1 + 11 / 16f + 0.05f * Mth.sin(t * 0.08f), 0);
            model.renderAnimated(ms, buffers.getBuffer(RenderType.entityTranslucent(VEPylonModel.LEVITATION_TEXTURE)),
                    on ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, spin);
            ms.popPose();
            if (on) {
                VertexConsumer glow = ManaFx.buffer(buffers);
                float pulse = 0.75f + 0.25f * Mth.sin(t * 0.15f);
                ManaFx.orb(ms, glow, 0.5, 1 + 15 / 16f, 0.5, 0.6f * pulse, ManaFx.MANA, active ? 0.8f : 0.5f);
                circle(be, t, active, ms, buffers);
            }
            wisps(be, on, active);
        }

        static final int MANASTEEL = 0x4fb4ff;

        /**
         * Mana wisps drifting up off the gem the way they rise from a Botania mana pool: a few while it
         * holds mana, more while it hovers a ship, a steady stream while it climbs, descends or steers.
         * While lifting, more wisps rise off the rim of the rune circle under the keel.
         */
        private void wisps(LevitationPylonBlockEntity be, boolean on, boolean active) {
            if (!FxParticles.once(be)) return;
            var level = be.getLevel();
            var r = level.random;
            float chance = active ? 1f : on ? 0.55f : be.mana.getStoredMana() > 0 ? 0.12f : 0f;
            int count = active ? 2 : 1;
            for (int i = 0; i < count; i++)
                if (r.nextFloat() < chance)
                    FxParticles.risingWisp(level, FxParticles.world(be, 0.2 + r.nextFloat() * 0.6, 1.55 + r.nextFloat() * 0.5,
                            0.2 + r.nextFloat() * 0.6), r.nextInt(4) == 0 ? ManaFx.MANA_CORE : MANASTEEL);
            if (on) {
                SubLevel ship = Sable.HELPER.getContaining(be);
                if (ship == null) return;
                double y = ship.getPlot().getBoundingBox().minY() - be.getBlockPos().getY() - 0.6;
                for (int i = 0; i < (active ? 2 : 1); i++) {
                    double a = r.nextDouble() * Math.PI * 2, rad = 2.2 + r.nextDouble() * 0.5;
                    FxParticles.risingWisp(level, FxParticles.world(be, 0.5 + Math.cos(a) * rad, y, 0.5 + Math.sin(a) * rad), MANASTEEL);
                }
            }
        }

        /** Rune circle 0.6 blocks under the lowest block of the ship, centred under the pylon. */
        private void circle(LevitationPylonBlockEntity be, float t, boolean active, PoseStack ms, MultiBufferSource buffers) {
            SubLevel ship = Sable.HELPER.getContaining(be);
            if (ship == null) return;
            int minY = ship.getPlot().getBoundingBox().minY();
            float y = minY - be.getBlockPos().getY() - 0.6f;
            float alpha = active ? 0.9f : 0.6f;
            ms.pushPose();
            ms.translate(0.5, y, 0.5);
            ms.pushPose();
            ms.mulPose(Axis.YP.rotationDegrees(t * (active ? 1.2f : 0.5f)));
            disc(ms, buffers.getBuffer(RenderType.entityTranslucentEmissive(MAGIC_CIRCLE)), 2.75f, alpha);
            ms.popPose();
            ms.translate(0, -0.02, 0);
            ms.mulPose(Axis.YP.rotationDegrees(-t * (active ? 2.0f : 0.8f)));
            disc(ms, buffers.getBuffer(RenderType.entityTranslucentEmissive(MAGIC_CIRCLE_INNER)), 1.6f, alpha);
            ms.popPose();
        }

        /** A horizontal textured square, both sides, full-bright. */
        static void disc(PoseStack ms, VertexConsumer vc, float r, float a) {
            PoseStack.Pose p = ms.last();
            float[][] q = {{-r, -r, 0, 0}, {-r, r, 0, 1}, {r, r, 1, 1}, {r, -r, 1, 0}};
            for (int side = 0; side < 2; side++) {
                for (int i = 0; i < 4; i++) {
                    float[] c = q[side == 0 ? i : 3 - i];
                    vc.addVertex(p, c[0], 0, c[1]).setColor(1f, 1f, 1f, a).setUv(c[2], c[3])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                            .setNormal(p, 0, side == 0 ? 1 : -1, 0);
                }
            }
        }

        @Override
        public boolean shouldRenderOffScreen(LevitationPylonBlockEntity be) { return true; }
    }

    // ------------------------------------------------------------------ thruster

    /**
     * The Gaia thruster's exhaust as real 3D geometry, all scaling with the throttle: a white-gold core
     * jet inside a pink plume inside a faint mana-blue sheath (tapered, turbulent cones), bright shock
     * diamonds along the jet, a glowing collar at the bell's mouth, ripple rings running down the
     * plume, and Botania wisps streaming out behind (left in the world, so a moving ship leaves a trail).
     */
    public static class Thruster extends SafeBlockEntityRenderer<GaiaThrusterBlockEntity> {
        public Thruster(BlockEntityRendererProvider.Context context) {}

        @Override
        protected void renderSafe(GaiaThrusterBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            float th = (float) be.throttle();
            if (th <= 0.01f) return;
            float t = time(be, pt);
            Direction n = be.nozzle();
            org.joml.Vector3f axis = new org.joml.Vector3f(n.getStepX(), n.getStepY(), n.getStepZ());
            // the bell's mouth is the far face of the 3x3x3 (1.5 blocks out from the centre)
            org.joml.Vector3f mouth = new org.joml.Vector3f(0.5f, 0.5f, 0.5f).add(new org.joml.Vector3f(axis).mul(1.52f));
            org.joml.Vector3f start = new org.joml.Vector3f(mouth).sub(new org.joml.Vector3f(axis).mul(0.4f));
            float flick = 0.92f + 0.06f * Mth.sin(t * 1.7f) + 0.04f * Mth.sin(t * 3.9f);
            float len = (1.6f + 5.5f * th) * flick;
            int gold = PhaseFourRenderers.GAIA_CORE, halo = PhaseFourRenderers.GAIA_HALO;

            VertexConsumer soft = ManaFx.buffer(buffers);
            ManaFx.orb(ms, soft, mouth.x, mouth.y, mouth.z, 1.8f * flick, ManaFx.GAIA, 0.35f * th);

            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            var m = ms.last().pose();
            org.joml.Vector3f eye = FxMesh.camera(ms);
            FxMesh.cone(vc, m, eye, start, axis, len * 1.2f, 1.1f, 0.55f, ManaFx.GAIA, ManaFx.MANA, 0.16f * th, 0f, 0.1f, 0.14f, t * 2.2f, 20, 12);
            FxMesh.cone(vc, m, eye, start, axis, len, 0.85f, 0.18f, ManaFx.GAIA, halo, 0.38f * th, 0f, 0.15f, 0.09f, t * 2.6f, 20, 12);
            FxMesh.cone(vc, m, eye, start, axis, len * 0.55f, 0.45f, 0.04f, 0xffffff, gold, 0.7f * th, 0.1f * th, 0.3f, 0.05f, t * 3f, 14, 8);
            // shock diamonds
            for (int k = 1; k <= 3; k++) {
                org.joml.Vector3f d = new org.joml.Vector3f(start).add(new org.joml.Vector3f(axis).mul(len * 0.16f * k));
                FxMesh.sphere(vc, m, eye, d.x, d.y, d.z, (0.3f - 0.06f * k) * (0.7f + 0.3f * th) * flick, 0xffffff, gold,
                        0.75f * th * (1f - 0.22f * k), 0.3f, 0.12f, t * 3 + k, 8);
            }
            // collar at the mouth and ripples running down the plume
            FxMesh.ring(vc, m, mouth, axis, 1.0f, 0.45f, gold, 0.8f * th, 0, 0);
            FxMesh.ring(vc, m, new org.joml.Vector3f(mouth).sub(new org.joml.Vector3f(axis).mul(0.05f)), axis, 1.2f, 0.18f,
                    ManaFx.GAIA, 0.6f * th, 8, t * 0.25f);
            for (int k = 0; k < 3; k++) {
                float off = ((t * (0.04f + 0.06f * th)) + k / 3f) % 1f;
                org.joml.Vector3f c = new org.joml.Vector3f(start).add(new org.joml.Vector3f(axis).mul(off * len));
                FxMesh.ring(vc, m, c, axis, Mth.lerp(off, 0.95f, 0.35f), 0.2f, halo, 0.55f * th * (1f - off), 0, 0);
            }

            if (FxParticles.once(be)) {
                var level = be.getLevel();
                var r = level.random;
                Vec3 out = FxParticles.worldDir(be, new Vec3(axis.x, axis.y, axis.z));
                int count = 1 + (int) (3 * th);
                for (int i = 0; i < count; i++) {
                    Vec3 at = FxParticles.world(be, mouth.x + (r.nextFloat() - .5f) * 1.2, mouth.y + (r.nextFloat() - .5f) * 1.2,
                            mouth.z + (r.nextFloat() - .5f) * 1.2);
                    Vec3 v = out.scale(0.25 + 0.55 * th).add((r.nextFloat() - .5f) * 0.06, (r.nextFloat() - .5f) * 0.06, (r.nextFloat() - .5f) * 0.06);
                    FxParticles.wisp(level, at, v, r.nextInt(3) == 0 ? gold : ManaFx.GAIA, 0.3f + r.nextFloat() * 0.4f, 0.7f);
                }
                if (r.nextFloat() < th)
                    FxParticles.sparkle(level, FxParticles.world(be, mouth.x, mouth.y, mouth.z).add(out.scale(r.nextFloat() * len)),
                            gold, 1f + r.nextFloat(), 4);
            }
        }

        @Override
        public boolean shouldRenderOffScreen(GaiaThrusterBlockEntity be) { return true; }
    }

    // ------------------------------------------------------------------ bifrost projector

    /** The prism glows in slowly cycling rainbow colours while it lays a road. */
    public static class Projector extends SafeBlockEntityRenderer<BifrostProjectorBlockEntity> {
        public Projector(BlockEntityRendererProvider.Context context) {}

        @Override
        protected void renderSafe(BifrostProjectorBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (!be.projecting()) return;
            float t = time(be, pt);
            Direction f = be.lens();
            Vec3 lens = new Vec3(0.5, 0.5, 0.5).add(Vec3.atLowerCornerOf(f.getNormal()).scale(0.3));
            int rgb = Mth.hsvToRgb((t * 0.01f) % 1f, 0.55f, 1f);
            VertexConsumer vc = ManaFx.buffer(buffers);
            ManaFx.orb(ms, vc, lens.x, lens.y, lens.z, 0.55f + 0.08f * Mth.sin(t * 0.3f), rgb, 0.7f);
            ManaFx.orb(ms, vc, lens.x, lens.y, lens.z, 0.22f, 0xffffff, 0.9f);
        }
    }

    // ------------------------------------------------------------------ flügel navigation core

    /** The Eye of the Flügel floats over the cup, turning to face the course while it navigates. */
    public static class Core extends SafeBlockEntityRenderer<FlugelNavigationCoreBlockEntity> {
        public Core(BlockEntityRendererProvider.Context context) {}

        @Override
        protected void renderSafe(FlugelNavigationCoreBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (be.eye().isEmpty()) return;
            float t = time(be, pt);
            boolean nav = be.status() == FlugelNavigationCoreBlockEntity.Status.NAVIGATING;
            ms.pushPose();
            ms.translate(0.5, 0.95 + 0.06 * Mth.sin(t * 0.1f), 0.5);
            ms.mulPose(Axis.YP.rotationDegrees(nav ? t * 4f : t * 1f));
            ms.scale(0.6f, 0.6f, 0.6f);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.eye(), ItemDisplayContext.FIXED,
                    nav ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, ms, buffers, be.getLevel(), 0);
            ms.popPose();
            if (nav) {
                VertexConsumer vc = ManaFx.buffer(buffers);
                ManaFx.orb(ms, vc, 0.5, 0.95, 0.5, 0.5f + 0.06f * Mth.sin(t * 0.25f), 0xffd27a, 0.55f);
            }
        }
    }
}
