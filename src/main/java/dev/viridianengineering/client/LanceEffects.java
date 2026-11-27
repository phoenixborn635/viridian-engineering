package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.network.VENetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * Gaia Kinetic Lance effects in the world.
 * <ul>
 *   <li><b>Gale</b> (wind-lunge release): a pale cone of wind bursting out along the look for the gale's
 *       length, white-gold at its core and pink at its rim, three shock rings racing down it, and wisps
 *       swept along with it. ~16 ticks.</li>
 *   <li><b>Empowered strike</b>: Botania-style impact burst at the target, bigger the more kinetic bonus.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ViridianEngineering.MOD_ID, value = Dist.CLIENT)
public final class LanceEffects {

    private record Gale(Vec3 from, Vector3f dir, float len, long start) {}

    private static final List<Gale> ACTIVE = new ArrayList<>();
    private static final int LIFE = 16;
    static final int GOLD = 0xfff1c8, PINK = 0xff8fd8, WHITE = 0xffffff, SKY = 0xbfe8ff;

    private LanceEffects() {}

    public static void start(VENetwork.LanceFx p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        if (p.kind() == 1) {
            ManaExplosions.impact(at, 0.35f + 0.5f * Mth.clamp(p.length(), 0f, 1f));
            return;
        }
        Vector3f dir = new Vector3f(p.dx(), p.dy(), p.dz()).normalize();
        ACTIVE.add(new Gale(at, dir, Math.max(1f, p.length()), level.getGameTime()));
        RandomSource r = level.random;
        for (int i = 0; i < 60; i++) {
            float s = r.nextFloat() * p.length();
            Vec3 q = at.add(dir.x * s, dir.y * s, dir.z * s).add((r.nextDouble() - .5) * 1.4, (r.nextDouble() - .5) * 1.4, (r.nextDouble() - .5) * 1.4);
            Vec3 v = new Vec3(dir.x, dir.y, dir.z).scale(0.35 + r.nextDouble() * 0.4);
            FxParticles.wisp(level, q, v, i % 3 == 0 ? PINK : i % 3 == 1 ? GOLD : SKY, 0.25f + r.nextFloat() * 0.35f, 0.5f + r.nextFloat() * 0.4f);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { ACTIVE.clear(); return; }
        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ACTIVE.removeIf(g -> time - g.start > LIFE || time < g.start - 5);
        if (ACTIVE.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Gale g : ACTIVE) {
            float t = (time - g.start) / LIFE;
            ms.pushPose();
            ms.translate(g.from.x - cam.x, g.from.y - cam.y, g.from.z - cam.z);
            Matrix4f m = ms.last().pose();
            Vector3f c = FxMesh.camera(ms);
            VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
            float grow = ManaExplosions.easeOut(t * 2.2f), fade = Mth.clamp(1f - t, 0f, 1f);
            float len = g.len * grow;
            Vector3f start = new Vector3f(g.dir).mul(0.6f);
            // outer wind sheath (rim-lit), inner bright core
            FxMesh.cone(vc, m, c, start, g.dir, len, 0.35f, 0.6f + 1.4f * grow, WHITE, PINK, 0.22f * fade, 0.04f * fade,
                    1.6f, 0.18f, time, 20, 8);
            FxMesh.cone(vc, m, c, start, g.dir, len * 0.9f, 0.12f, 0.35f + 0.5f * grow, WHITE, GOLD, 0.35f * fade, 0.0f,
                    0.7f, 0.1f, time, 14, 6);
            // three shock rings racing down the gale
            for (int i = 0; i < 3; i++) {
                float k = Mth.clamp(t * 1.8f - i * 0.18f, 0f, 1f);
                if (k <= 0f || k >= 1f) continue;
                Vector3f at = new Vector3f(g.dir).mul(0.6f + g.len * k);
                float rr = 0.4f + 1.6f * k;
                FxMesh.ring(vc, m, at, g.dir, rr, 0.25f, i == 1 ? PINK : GOLD, 0.55f * (1f - k) * fade, 0, 0);
            }
            ms.popPose();
        }
        buffers.endBatch(VERenderTypes.GLOW);
    }
}
