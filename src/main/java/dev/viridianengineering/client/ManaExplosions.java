package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.network.VENetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Spherical mana detonations, drawn as real 3D meshes ({@link FxMesh}) rather than one flat sprite:
 * <ul>
 *   <li>a white flash at the heart;</li>
 *   <li>the fireball: three nested, turbulent sphere shells (bright core, mana body, rim-lit terra shell)
 *       that swell to the blast radius and boil while they fade;</li>
 *   <li>a thin rim-lit shockwave dome that outruns the fireball;</li>
 *   <li>two shock rings racing out across the ground and a tilted ring through the fireball;</li>
 *   <li>a pillar of light straight up;</li>
 *   <li>the cracks: the failure sequence's ground fractures flare white, and a far larger network races
 *       out over the terrain and smoulders, venting wisps;</li>
 *   <li>a spherical burst of Botania wisps and sparkles flung out in every direction.</li>
 * </ul>
 * The battery uses the full version ({@link #start}); the cannon's arcane bolt a small one ({@link #impact}).
 */
@EventBusSubscriber(modid = ViridianEngineering.MOD_ID, value = Dist.CLIENT)
public final class ManaExplosions {

    private ManaExplosions() {}

    private record Blast(Vec3 center, float radius, float fill, long start, boolean small, float groundY,
                         GroundCracks cracks, GroundCracks inherited, Vector3f tilt) {
        int duration() { return small ? 22 : 110; }
    }

    private static final List<Blast> ACTIVE = new ArrayList<>();
    static final int GROW = 30;

    static final int WHITE = 0xffffff, MANA = ManaFx.MANA, CORE = ManaFx.MANA_CORE, TERRA = ManaFx.TERRA,
            CRACK = BatteryCinematics.CRACK;

    public static void start(VENetwork.BatteryDetonated p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 c = new Vec3(p.x(), p.y(), p.z());
        float radius = Math.max(4f, p.radius());
        long seed = Double.doubleToLongBits(c.x * 31 + c.z);
        GroundCracks inherited = BatteryCinematics.takeCracks(c);
        float crackLen = Math.min(radius * 1.15f, 72f);
        double groundRef = inherited != null ? inherited.cy : c.y;
        GroundCracks cracks = GroundCracks.build(level, c.x, groundRef, c.z, 1.5f, crackLen, seed, 3, 14);
        float groundY = (float) (inherited != null ? inherited.cy : c.y - 0.5);
        RandomSource r = RandomSource.create(seed);
        Vector3f tilt = new Vector3f(r.nextFloat() - .5f, 1.2f, r.nextFloat() - .5f).normalize();
        ACTIVE.add(new Blast(c, radius, p.fill(), level.getGameTime(), false, groundY, cracks, inherited, tilt));
        burst(level, c, radius, 220, false);
    }

    /** The arcane bolt's impact: a small, quick version (sphere, rings, spark burst). */
    public static void impact(Vec3 at, float size) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ACTIVE.add(new Blast(at, size, 1f, level.getGameTime(), true, (float) at.y, null, null, new Vector3f(0, 1, 0)));
        burst(level, at, size * 2.5f, 40, true);
    }

    /** Wisps and sparkles flung out over a sphere (Botania's own particles). */
    private static void burst(ClientLevel level, Vec3 c, float radius, int count, boolean gaia) {
        RandomSource r = level.random;
        float speed = 0.25f + Mth.sqrt(radius) * 0.18f;
        for (int i = 0; i < count; i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            Vec3 dir = new Vec3(s * Math.cos(th), u, s * Math.sin(th)); // uniform on the sphere
            double v = speed * (0.4 + r.nextDouble());
            int rgb = gaia ? (r.nextBoolean() ? ManaFx.GAIA : PhaseFourRenderers.GAIA_CORE)
                    : (i % 5 == 0 ? TERRA : i % 3 == 0 ? CORE : MANA);
            FxParticles.wisp(level, c.add(dir.scale(0.5)), dir.scale(v), rgb, 0.3f + r.nextFloat() * (gaia ? 0.4f : 0.9f),
                    gaia ? 0.6f : 1.6f + r.nextFloat());
            if (i % 4 == 0) FxParticles.sparkle(level, c.add(dir.scale(r.nextDouble() * radius * 0.5)), rgb,
                    1f + r.nextFloat() * 1.5f, 6 + r.nextInt(6));
        }
    }

    static float easeOut(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { ACTIVE.clear(); return; }
        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ACTIVE.removeIf(b -> time - b.start > b.duration() || time < b.start - 5);
        if (ACTIVE.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Blast b : ACTIVE) {
            ms.pushPose();
            ms.translate(b.center.x - cam.x, b.center.y - cam.y, b.center.z - cam.z);
            if (b.small) small(b, time - b.start, time, ms, buffers);
            else big(b, time - b.start, time, ms, buffers, level);
            ms.popPose();
        }
        buffers.endBatch(RenderType.eyes(ManaFx.WISP));
        buffers.endBatch(VERenderTypes.GLOW);
    }

    private static void big(Blast b, float t, float time, PoseStack ms, MultiBufferSource buffers, ClientLevel level) {
        float R = b.radius;
        float grow = easeOut(t / GROW);
        float fade = t < GROW ? 1f : Mth.clamp(1f - (t - GROW) / (b.duration() - GROW - 10f), 0f, 1f);
        int detail = R > 40 ? 28 : 22;

        VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
        Matrix4f m = ms.last().pose();
        Vector3f eye = FxMesh.camera(ms);

        // 1. flash (short, so it never washes the whole sphere out)
        float flash = Mth.clamp(1f - t / 6f, 0f, 1f);
        if (flash > 0)
            FxMesh.sphere(vc, m, eye, 0, 0, 0, Math.max(0.8f, R * 0.22f * easeOut(t / 3f)), WHITE, CORE, 0.6f * flash, 0.2f, 0f, time, 14);

        // 2. fireball: hot core, mana body, rim-lit terra shell; boils harder as it fades.
        //    Kept dim face-on and bright at the rims, so it reads as a sphere, not a white disc.
        float r = Math.max(0.6f, R * grow);
        float boil = 0.05f + 0.1f * (1f - fade);
        FxMesh.sphere(vc, m, eye, 0, 0, 0, r * 0.45f, CORE, MANA, 0.32f * fade * fade, 0.15f, boil * 1.6f, time * 1.4f, detail);
        FxMesh.sphere(vc, m, eye, 0, 0, 0, r * 0.8f, MANA, MANA, 0.07f * fade, 6f, boil, time, detail);
        FxMesh.sphere(vc, m, eye, 0, 0, 0, r, MANA, TERRA, 0.035f * fade, 12f, boil * 0.5f, time * 0.7f, detail);

        // 3. shockwave dome: only its rim shows, racing ahead and thinning out
        float dome = Mth.clamp(t / 22f, 0f, 1f);
        if (dome < 1f)
            FxMesh.sphere(vc, m, eye, 0, 0, 0, R * 1.45f * easeOut(dome), WHITE, CORE, 0.03f * (1f - dome), 18f, 0f, time, detail);

        // 4. shock rings across the ground and one tilted ring through the fireball
        Vector3f ground = new Vector3f(0, b.groundY - (float) b.center.y + 0.15f, 0), up = new Vector3f(0, 1, 0);
        for (int k = 0; k < 2; k++) {
            float s = Mth.clamp((t - k * 7f) / 40f, 0f, 1f);
            if (s <= 0f || s >= 1f) continue;
            float rr = R * (1.6f - k * 0.35f) * easeOut(s);
            FxMesh.ring(vc, m, ground, up, rr, 1.2f + rr * 0.12f, k == 0 ? MANA : TERRA, 0.6f * (1f - s), 0, 0);
        }
        float s3 = Mth.clamp(t / 30f, 0f, 1f);
        if (s3 < 1f)
            FxMesh.ring(vc, m, new Vector3f(), b.tilt, R * 1.15f * easeOut(s3), 0.8f + R * 0.06f, CORE, 0.4f * (1f - s3), 0, 0);

        // 5. pillar of light
        float pillar = Mth.clamp(1f - t / 35f, 0f, 1f);
        if (pillar > 0) {
            float h = R * 3.5f * easeOut(t / 12f);
            FxMesh.cone(vc, m, eye, new Vector3f(0, -R * 0.2f, 0), up, h, R * 0.14f, R * 0.03f, CORE, MANA,
                    0.35f * pillar, 0f, 0.1f, 0.08f, time * 2, 16, 10);
        }

        // 6. cracks: the inherited ones flare white and cool; the big network races out and smoulders
        if (b.inherited != null)
            b.inherited.render(vc, m, (float) (b.inherited.cx - b.center.x), (float) (b.inherited.cy - b.center.y),
                    (float) (b.inherited.cz - b.center.z), 999f, fade, t < 15 ? WHITE : CORE, CRACK, 1.4f);
        if (b.cracks != null) {
            float reveal = b.cracks.maxDistance * easeOut(t / 28f);
            float heat = t < 20 ? 1f : fade;
            b.cracks.render(vc, m, 0, (float) (b.cracks.cy - b.center.y), 0, reveal, heat, t < 25 ? WHITE : CORE, CRACK, 1.6f);
            if (FxParticles.once(b, level) && fade > 0.1f) {
                RandomSource rnd = level.random;
                for (int i = 0; i < 6; i++) {
                    Vec3 p = b.cracks.randomPoint(rnd, reveal);
                    if (p != null) FxParticles.risingWisp(level,
                            new Vec3(b.cracks.cx + p.x, b.cracks.cy + p.y, b.cracks.cz + p.z), rnd.nextInt(3) == 0 ? TERRA : MANA);
                }
            }
        }
    }

    private static void small(Blast b, float t, float time, PoseStack ms, MultiBufferSource buffers) {
        float s = Mth.clamp(t / b.duration(), 0f, 1f), fade = 1f - s;
        VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
        Matrix4f m = ms.last().pose();
        Vector3f eye = FxMesh.camera(ms);
        float r = b.radius * 1.4f * easeOut(s * 2f);
        FxMesh.sphere(vc, m, eye, 0, 0, 0, r * 0.55f, WHITE, PhaseFourRenderers.GAIA_CORE, 0.6f * fade * fade, 0.2f, 0.12f, time * 2, 12);
        FxMesh.sphere(vc, m, eye, 0, 0, 0, r, ManaFx.GAIA, PhaseFourRenderers.GAIA_HALO, 0.1f * fade, 6f, 0.1f, time, 14);
        FxMesh.ring(vc, m, new Vector3f(), new Vector3f(0, 1, 0), b.radius * 3.2f * easeOut(s), 0.5f + b.radius * 0.3f,
                PhaseFourRenderers.GAIA_CORE, 0.8f * fade, 0, 0);
        FxMesh.ring(vc, m, new Vector3f(), new Vector3f(1, 0.3f, 0.2f).normalize(), b.radius * 2.4f * easeOut(s * 1.2f),
                0.35f + b.radius * 0.2f, ManaFx.GAIA, 0.6f * fade, 0, 0);
    }
}
