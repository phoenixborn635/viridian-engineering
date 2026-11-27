package dev.viridianengineering.client;

import dev.viridianengineering.compat.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import velrondevs.botania.client.fx.SparkleParticleData;
import velrondevs.botania.client.fx.WispParticleData;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Botania's own mana particles (the wisps that rise off a mana pool, and sparkles) for our machines.
 * Renderers run every frame, so {@link #once(BlockEntity)} lets a renderer spawn particles only once
 * per game tick. Positions given in a block's own space (a ship's plot, or terrain) are turned into
 * world space so particles appear where the machine really is, even on a flying ship.
 */
public final class FxParticles {

    private FxParticles() {}

    private static final Map<Object, Long> LAST = new WeakHashMap<>();

    /** True the first time it is asked for {@code key} in each game tick. */
    public static boolean once(Object key, Level level) {
        long now = level.getGameTime();
        Long last = LAST.get(key);
        if (last != null && last == now) return false;
        LAST.put(key, now);
        return true;
    }

    public static boolean once(BlockEntity be) {
        return be.getLevel() != null && once(be, be.getLevel());
    }

    /** A point in the block entity's local render space (relative to its block) in world space. */
    public static Vec3 world(BlockEntity be, double x, double y, double z) {
        return SableCompat.toWorld(be.getLevel(), Vec3.atLowerCornerOf(be.getBlockPos()).add(x, y, z));
    }

    /** A direction in the block entity's local space in world space (turned with its ship). */
    public static Vec3 worldDir(BlockEntity be, Vec3 dir) {
        return SableCompat.toWorldNormal(be.getLevel(), be.getBlockPos(), dir);
    }

    /** A Botania wisp: {@code size} ~0.1–1, {@code life} multiplies its normal lifetime. */
    public static void wisp(Level level, Vec3 at, Vec3 motion, int rgb, float size, float life) {
        level.addParticle(WispParticleData.wisp(size, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, life, true),
                at.x, at.y, at.z, motion.x, motion.y, motion.z);
    }

    /** A Botania sparkle ({@code life} in its own units, ~2–10). */
    public static void sparkle(Level level, Vec3 at, int rgb, float size, int life) {
        level.addParticle(SparkleParticleData.sparkle(size, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, life),
                at.x, at.y, at.z, 0, 0, 0);
    }

    /** Mana-pool style: a small wisp drifting slowly upward from {@code at}. */
    public static void risingWisp(Level level, Vec3 at, int rgb) {
        var r = level.random;
        wisp(level, at, new Vec3(0, 0.01 + r.nextFloat() / 25f, 0), jitter(rgb, r), 0.08f + r.nextFloat() / 4.5f, 2f);
    }

    /** {@code rgb} with a little random brightness variation, as Botania does for pool wisps. */
    static int jitter(int rgb, net.minecraft.util.RandomSource r) {
        float k = 0.85f + r.nextFloat() * 0.3f;
        int red = Math.min(255, (int) ((rgb >> 16 & 255) * k)), g = Math.min(255, (int) ((rgb >> 8 & 255) * k)),
                b = Math.min(255, (int) ((rgb & 255) * k));
        return red << 16 | g << 8 | b;
    }

    static BlockPos pos(Vec3 v) { return BlockPos.containing(v); }
}
