package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The running reactor's mana flow (client only): the frame round the core spins up, the four corner
 * spires send mana up and over in arcs to the centre, where it is caught in a spinning collection
 * ring, orbits, and spirals into the core.
 * <ul>
 *   <li>Each spire has a faint flowing stream along its arc, so the flow reads even with few motes.</li>
 *   <li>Motes (small 3D glows) are emitted from the spire tips, ride the arc (each with its own wobble),
 *       join the ring, orbit, then shrink into the core. A mote is only its spawn time and a few random
 *       numbers: positions are computed when drawn, so nothing is updated per tick.</li>
 *   <li><b>Limits:</b> at most {@link #MAX_PER_REACTOR} motes per reactor and {@link #MAX_TOTAL} across
 *       all reactors; when either is reached a spire simply skips its emission.</li>
 *   <li>The spire tips flash when they emit; the ring and the core glow brighter the more mana orbits.</li>
 * </ul>
 * All positions are in the controller renderer's local space, so it all rides along on a Sable ship.
 */
final class ReactorFlow {

    private ReactorFlow() {}

    static final int MAX_PER_REACTOR = 48, MAX_TOTAL = 240;
    /** Ticks a mote takes along its arc, then circling the ring (the last {@link #SINK} spiralling in). */
    static final float TRAVEL = 28f, ORBIT = 46f, SINK = 14f;
    /** Radius of the collection ring round the core. */
    static final float RING = 1.3f;
    /** Each spire emits about once every this many ticks while the reactor runs. */
    static final int EMIT_EVERY = 6;

    private record Mote(int corner, float born, float wobble, float lift, float orbitSpeed, float size) {}

    private static final class State {
        final List<Mote> motes = new ArrayList<>();
        final float[] flash = new float[4];   // last emission time per spire
        float angle, speed, last = Float.NaN; // frame spin (degrees), its speed (degrees/tick)
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();
    private static int total;

    /** Spin speed of the frame: idle drift, running fast. Degrees per tick. */
    static final float IDLE_SPIN = 0.6f, RUN_SPIN = 13f;

    /**
     * Advances the frame spin toward its target (smooth spin-up and wind-down) and returns its angle
     * in degrees. The core turns with it, more slowly.
     */
    static float spin(BlockPos controller, boolean running, float time) {
        State s = STATES.computeIfAbsent(controller.immutable(), p -> new State());
        float dt = Float.isNaN(s.last) ? 0f : Mth.clamp(time - s.last, 0f, 5f);
        s.last = time;
        float target = running ? RUN_SPIN : IDLE_SPIN;
        s.speed += (target - s.speed) * Math.min(1f, dt * (running ? 0.035f : 0.02f));
        s.angle = (s.angle + s.speed * dt) % 360f;
        return s.angle;
    }

    /** 0 at idle, 1 at full speed: how "awake" the frame is (for glow strength). */
    static float spinLevel(BlockPos controller) {
        State s = STATES.get(controller);
        return s == null ? 0f : Mth.clamp((s.speed - IDLE_SPIN) / (RUN_SPIN - IDLE_SPIN), 0f, 1f);
    }

    static void forget(BlockPos controller) {
        State s = STATES.remove(controller);
        if (s != null) total -= s.motes.size();
    }

    /**
     * Drops reactors that haven't been drawn for 5 s (broken, unloaded, out of view, another world),
     * so their motes stop counting against {@link #MAX_TOTAL}; the count is re-summed to stay exact.
     */
    private static void purge(float time) {
        STATES.values().removeIf(s -> Float.isNaN(s.last) || Math.abs(time - s.last) > 100f);
        int sum = 0;
        for (State s : STATES.values()) sum += s.motes.size();
        total = sum;
    }

    /**
     * Emits (once per tick, while running) and draws the flow. {@code core} = the core's centre, {@code r}
     * = bowl radius, both in local space; {@code level} = 0..1 spin level.
     */
    static void render(BlockPos controller, Vector3f core, int r, boolean running, boolean newTick, float time,
                       RandomSource rnd, PoseStack ms, net.minecraft.client.renderer.MultiBufferSource buffers) {
        if (newTick) purge(time);
        State s = STATES.computeIfAbsent(controller.immutable(), p -> new State());
        // retire finished motes
        for (Iterator<Mote> it = s.motes.iterator(); it.hasNext(); )
            if (time - it.next().born > TRAVEL + ORBIT) { it.remove(); total--; }
        if (running && newTick)
            for (int k = 0; k < 4; k++)
                if (rnd.nextInt(EMIT_EVERY) == 0 && s.motes.size() < MAX_PER_REACTOR && total < MAX_TOTAL) {
                    s.motes.add(new Mote(k, time, (rnd.nextFloat() - .5f) * 0.9f, rnd.nextFloat() * 0.8f,
                            0.12f + rnd.nextFloat() * 0.08f, 0.07f + rnd.nextFloat() * 0.05f));
                    s.flash[k] = time;
                    total++;
                }
        float level = spinLevel(controller);
        if (level < 0.02f && s.motes.isEmpty()) return;

        VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
        Matrix4f m = ms.last().pose();
        Vector3f eye = FxMesh.camera(ms);
        Vector3f up = new Vector3f(0, 1, 0);
        int orbiting = 0;
        for (Mote mo : s.motes) if (time - mo.born > TRAVEL) orbiting++;
        float load = Math.min(1f, orbiting / 24f);

        // spire streams and tips
        for (int k = 0; k < 4; k++) {
            Vector3f[] arc = arc(core, r, k);
            stream(vc, m, eye, arc, time, k, level);
            Vector3f tip = arc[0];
            float f = Mth.clamp(1f - (time - s.flash[k]) / 6f, 0f, 1f);
            FxMesh.sphere(vc, m, eye, tip.x, tip.y, tip.z, 0.16f + 0.14f * f, 0xffffff, ManaFx.TERRA,
                    (0.25f + 0.55f * f) * Math.max(level, f), 0.1f, 0.1f, time, 6);
        }

        // the collection ring: a dashed band turning with the flow, a fine bright line, a faint tilted halo
        float ringA = (0.35f + 0.5f * load) * level;
        FxMesh.ring(vc, m, core, up, RING, 0.22f, ManaFx.MANA, ringA, 10, -time * 0.9f);
        FxMesh.ring(vc, m, core, up, RING, 0.05f, ManaFx.MANA_CORE, ringA * 1.2f, 0, 0);
        Vector3f tilt = new Vector3f(Mth.sin(time * 0.02f) * 0.35f, 1f, Mth.cos(time * 0.02f) * 0.35f).normalize();
        FxMesh.ring(vc, m, core, tilt, RING * 0.8f, 0.08f, ManaFx.TERRA, 0.3f * level * load, 6, time * 0.6f);
        // the core drinks it: a soft glow that swells with the load
        FxMesh.sphere(vc, m, eye, core.x, core.y, core.z, 0.55f + 0.25f * load, ManaFx.MANA_CORE, ManaFx.MANA,
                (0.12f + 0.25f * load) * level, 0f, 0.06f, time, 10);

        // motes
        for (Mote mo : s.motes) {
            float age = time - mo.born;
            Vector3f p = position(mo, core, r, age), prev = position(mo, core, r, Math.max(0f, age - 2.5f));
            float a = age < 3f ? age / 3f : 1f;
            float sinkAge = age - (TRAVEL + ORBIT - SINK);
            if (sinkAge > 0) a *= 1f - sinkAge / SINK;
            FxMesh.line(vc, m, eye, prev, p, mo.size * 1.4f, ManaFx.MANA, 0f, 0.5f * a);
            FxMesh.sphere(vc, m, eye, p.x, p.y, p.z, mo.size, 0xffffff, ManaFx.MANA, 0.85f * a, 0.15f, 0f, time, 5);
            FxMesh.sphere(vc, m, eye, p.x, p.y, p.z, mo.size * 2.4f, ManaFx.MANA, ManaFx.TERRA, 0.18f * a, 0f, 0f, time, 5);
        }
    }

    /** The cubic arc from spire {@code k}'s tip, up and over, down onto the ring: p0, c1, c2, p3. */
    private static Vector3f[] arc(Vector3f core, int r, int k) {
        int sx = (k & 1) == 0 ? -1 : 1, sz = (k & 2) == 0 ? -1 : 1;
        Vector3f tip = new Vector3f(core.x + sx * r, core.y - 0.5f + 1.72f, core.z + sz * r);
        float ang = (float) Math.atan2(sz, sx);
        Vector3f entry = new Vector3f(core.x + Mth.cos(ang) * RING, core.y, core.z + Mth.sin(ang) * RING);
        float h = 1.6f + 0.35f * r;
        Vector3f c1 = new Vector3f(tip).add(0, h, 0).lerp(entry, 0.15f).add(0, 0.2f, 0);
        Vector3f c2 = new Vector3f(entry).add(0, h * 0.9f, 0);
        return new Vector3f[]{tip, c1, c2, entry};
    }

    private static Vector3f bezier(Vector3f[] a, float t) {
        float u = 1 - t, b0 = u * u * u, b1 = 3 * u * u * t, b2 = 3 * u * t * t, b3 = t * t * t;
        return new Vector3f(
                a[0].x * b0 + a[1].x * b1 + a[2].x * b2 + a[3].x * b3,
                a[0].y * b0 + a[1].y * b1 + a[2].y * b2 + a[3].y * b3,
                a[0].z * b0 + a[1].z * b1 + a[2].z * b2 + a[3].z * b3);
    }

    /** Where a mote is {@code age} ticks after leaving its spire. */
    private static Vector3f position(Mote mo, Vector3f core, int r, float age) {
        Vector3f[] a = arc(core, r, mo.corner);
        if (age <= TRAVEL) {
            float x = age / TRAVEL, t = x * x * (3 - 2 * x); // ease in and out
            Vector3f p = bezier(a, t);
            // sideways wobble and extra lift, both zero at the ends so it leaves the tip and lands on the ring
            Vector3f d = new Vector3f(a[3]).sub(a[0]);
            Vector3f side = new Vector3f(-d.z, 0, d.x);
            if (side.lengthSquared() > 1e-6f) side.normalize();
            float env = Mth.sin(t * Mth.PI);
            return p.add(side.mul(mo.wobble * env)).add(0, mo.lift * env, 0);
        }
        // orbit: start at the entry angle, circle the core, spiral in during the last SINK ticks
        float o = age - TRAVEL;
        float entry = (float) Math.atan2(a[3].z - core.z, a[3].x - core.x);
        float ang = entry - o * mo.orbitSpeed;
        float sinkAge = o - (ORBIT - SINK);
        float rad = RING;
        float y = core.y + 0.06f * Mth.sin(o * 0.3f + mo.wobble * 7f);
        if (sinkAge > 0) {
            float k = sinkAge / SINK;
            rad = Mth.lerp(k * k, RING, 0.15f);
            ang -= k * 2.5f;
        }
        return new Vector3f(core.x + Mth.cos(ang) * rad, y, core.z + Mth.sin(ang) * rad);
    }

    /** A faint flowing stream along spire {@code k}'s arc: bright pulses travelling toward the centre. */
    private static void stream(VertexConsumer vc, Matrix4f m, Vector3f eye, Vector3f[] arc, float time, int k, float level) {
        if (level < 0.02f) return;
        int n = 24;
        Vector3f prev = arc[0];
        float prevA = alpha(0f, time, k) * level;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            Vector3f p = bezier(arc, t);
            float a = alpha(t, time, k) * level;
            FxMesh.line(vc, m, eye, prev, p, 0.22f, ManaFx.MANA, prevA * 0.35f, a * 0.35f);
            FxMesh.line(vc, m, eye, prev, p, 0.06f, ManaFx.MANA_CORE, prevA, a);
            prev = p;
            prevA = a;
        }
    }

    private static float alpha(float t, float time, int k) {
        float flow = 0.5f + 0.5f * Mth.sin((t * 9f - time * 0.35f) + k * 1.7f);
        float ends = Math.min(1f, Math.min(t, 1f - t) * 6f + 0.25f);
        return (0.12f + 0.38f * flow * flow) * ends;
    }
}
