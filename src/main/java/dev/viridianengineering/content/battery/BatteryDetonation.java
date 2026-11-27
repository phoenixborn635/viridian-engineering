package dev.viridianengineering.content.battery;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.network.VENetwork;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The LOCKED detonation engine of the Terrasteel Mana Battery (spec 3.4).
 * <ol>
 *   <li>Intercepts {@link BlockEvent.BreakEvent} and explosions ({@link ExplosionEvent.Detonate}) that
 *       touch a formed battery. Sable ship assembly moves blocks without these events, so it never rolls.</li>
 *   <li>One roll per break: {@code f = M / C} clamped to [0, 1]; {@code triggered = random.nextDouble() < f}.</li>
 *   <li>Safe: the break goes ahead normally (with drops) and the battery is marked unformed.</li>
 *   <li>Unstable: the event is cancelled (no drops), the battery turns UNSTABLE (no GUI, no I/O, no
 *       further breaks), a {@link BatteryFailureEntry} with a deadline 200 ticks out is saved, and
 *       {@link VENetwork.BatteryFailureStart} starts the fracture / levitation / core-collapse cinematic.</li>
 *   <li>At the deadline (kept across restarts, never re-rolled): the multiblock is removed with
 *       UPDATE_CLIENTS and no drops; radius = 16 + 184 f; living entities take damage and knockback; and
 *       if {@code batteryExplosionTerrainDamage}, a {@link ShellExplosionTask} removes the sphere.</li>
 * </ol>
 */
public final class BatteryDetonation {

    private BatteryDetonation() {}

    public enum Outcome { NOT_A_BATTERY, BLOCKED_UNSTABLE, SAFE, TRIGGERED }

    /** The roll itself (step b). */
    public static boolean roll(double fill, RandomSource random) {
        double f = Mth.clamp(fill, 0.0, 1.0);
        return random.nextDouble() < f;
    }

    public static int radiusFor(double fill) {
        int min = VEConfig.BATTERY_EXPLOSION_MIN_RADIUS.get(), max = VEConfig.BATTERY_EXPLOSION_MAX_RADIUS.get();
        return (int) Math.round(min + (max - min) * Mth.clamp(fill, 0.0, 1.0));
    }

    // ------------------------------------------------------------------ hooks

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Outcome o = handleBreak(level, event.getPos(), level.random);
        if (o == Outcome.BLOCKED_UNSTABLE || o == Outcome.TRIGGERED) event.setCanceled(true);
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;
        Map<BlockPos, BatteryControllerBlockEntity> hit = new LinkedHashMap<>();
        for (BlockPos p : affected) {
            BlockState s = level.getBlockState(p);
            if (!isBatteryBlock(s)) continue;
            BatteryControllerBlockEntity c = BatteryControllerBlockEntity.owning(level, p);
            if (c != null && (c.isFormed() || c.isUnstable())) hit.putIfAbsent(c.getBlockPos(), c);
        }
        for (BatteryControllerBlockEntity c : hit.values()) {
            Set<BlockPos> members = new HashSet<>(c.members(c.isUnstable() ? c.failureLayers() : c.validLayers()));
            if (c.isUnstable()) { affected.removeIf(members::contains); continue; }
            // One roll for the whole battery, however many of its blocks the blast reaches.
            if (roll(c.fillFraction(), level.random)) {
                affected.removeIf(members::contains);
                begin(level, c);
            } else {
                c.markUnformed();
            }
        }
    }

    private static boolean isBatteryBlock(BlockState s) {
        return s.is(VEBlocks.TERRASTEEL_MANA_BATTERY_CASING.get()) || s.is(VEBlocks.TERRASTEEL_MANA_BATTERY_CONTROLLER.get())
                || s.is(VEBlocks.TERRASTEEL_MANA_BATTERY_PORT.get());
    }

    /** Steps a-d for a break at {@code pos}. Public for GameTests. */
    public static Outcome handleBreak(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isBatteryBlock(level.getBlockState(pos))) return Outcome.NOT_A_BATTERY;
        BatteryControllerBlockEntity c = BatteryControllerBlockEntity.owning(level, pos);
        if (c == null) return Outcome.NOT_A_BATTERY;
        if (c.isUnstable()) return Outcome.BLOCKED_UNSTABLE;
        if (!c.isFormed()) return Outcome.NOT_A_BATTERY; // an unformed stack is just blocks
        if (!roll(c.fillFraction(), random)) {
            c.markUnformed();
            return Outcome.SAFE;
        }
        begin(level, c);
        return Outcome.TRIGGERED;
    }

    private static void begin(ServerLevel level, BatteryControllerBlockEntity c) {
        MachineEndpointKey key = SableCompat.endpointKey(level, c.getBlockPos(), Direction.UP);
        double f = c.fillFraction();
        long deadline = level.getGameTime() + VEConfig.BATTERY_FAILURE_TICKS.get();
        BatteryFailureEntry entry = new BatteryFailureEntry(UUID.randomUUID(), level.dimension(), key.sableSubLevelId(),
                key.localPos(), c.validLayers(), f, deadline);
        BatteryFailureSavedData.get(level).add(entry);
        c.beginFailure(entry.eventId(), deadline, f, c.validLayers());
        broadcast(level, entry, c.getBlockPos());
        level.playSound(null, c.getBlockPos(), dev.viridianengineering.registry.VESounds.BATTERY_UNSTABLE.get(), SoundSource.BLOCKS, 4f, 0.5f);
        ViridianEngineering.LOGGER.info("Mana battery at {} in {} turned UNSTABLE ({}% full, {} layers); detonation at tick {}",
                c.getBlockPos(), level.dimension().location(), Math.round(f * 100), c.validLayers(), deadline);
    }

    private static void broadcast(ServerLevel level, BatteryFailureEntry e, BlockPos worldPos) {
        PacketDistributor.sendToPlayersInDimension(level, new VENetwork.BatteryFailureStart(e.eventId(), worldPos,
                e.validLayers(), (float) e.fillFraction(), e.detonationGameTick()));
    }

    /** Re-sends running cinematics to a player who joins or changes dimension. */
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) { resend(event.getEntity()); }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { resend(event.getEntity()); }

    private static void resend(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        for (BatteryFailureEntry e : BatteryFailureSavedData.get(level).entries()) {
            BlockPos pos = SableCompat.resolve(level, e.key());
            if (pos != null) PacketDistributor.sendToPlayer(sp, new VENetwork.BatteryFailureStart(e.eventId(), pos,
                    e.validLayers(), (float) e.fillFraction(), e.detonationGameTick()));
        }
    }

    // ------------------------------------------------------------------ deadline + blasts

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    public static void tick(ServerLevel level) {
        BatteryFailureSavedData data = BatteryFailureSavedData.get(level);
        if (!data.entries().isEmpty()) {
            long now = level.getGameTime();
            for (BatteryFailureEntry e : List.copyOf(data.entries())) {
                if (now < e.detonationGameTick()) continue;
                BlockPos pos = SableCompat.resolve(level, e.key());
                if (pos == null) { data.remove(e.eventId()); continue; } // its ship is gone
                if (!level.isLoaded(pos)) continue; // detonates as soon as the chunk is back
                detonate(level, e, pos);
                data.remove(e.eventId());
            }
        }
        if (!data.blasts().isEmpty()) {
            for (ShellExplosionTask t : List.copyOf(data.blasts())) {
                if (t.step(level)) {
                    data.removeBlast(t.id);
                    ViridianEngineering.LOGGER.info("Battery blast {} finished: {} blocks removed, slowest step {} ms",
                            t.id, t.removedTotal(), t.worstStepNanos() / 1_000_000);
                }
                data.setDirty();
            }
        }
    }

    /** Step e. Public for GameTests. */
    public static void detonate(ServerLevel level, BatteryFailureEntry e, BlockPos controllerPos) {
        double f = Mth.clamp(e.fillFraction(), 0, 1);
        int radius = radiusFor(f);

        // 1. remove the multiblock: UPDATE_CLIENTS, no drops
        Set<BlockPos> members = new LinkedHashSet<>();
        if (level.getBlockEntity(controllerPos) instanceof BatteryControllerBlockEntity c) {
            members.addAll(c.members(Math.max(1, e.validLayers())));
        } else {
            for (int y = -1; y <= e.validLayers(); y++)
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) members.add(controllerPos.offset(dx, y, dz));
        }
        members.add(controllerPos);
        Vec3 centerLocal = Vec3.atCenterOf(controllerPos);
        for (BlockPos p : members)
            if (isBatteryBlock(level.getBlockState(p)))
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);

        // The blast happens where the battery really is (on a ship: its world position).
        Vec3 center = SableCompat.toWorld(level, centerLocal);
        BlockPos centerBlock = BlockPos.containing(center);

        // 2. entities: damage and knockback, both falling off linearly to the edge of the radius
        double maxDamage = VEConfig.BATTERY_EXPLOSION_MAX_ENTITY_DAMAGE.get() * f;
        DamageSource source = VEDamageTypes.source(level, VEDamageTypes.BATTERY_DETONATION, null, null);
        for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius), x -> x instanceof LivingEntity)) {
            double d = entity.position().distanceTo(center);
            if (d > radius) continue;
            double scale = 1.0 - d / radius;
            entity.hurt(source, (float) (maxDamage * scale));
            Vec3 push = entity.position().subtract(center);
            push = push.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : push.normalize();
            entity.push(push.x * scale * 3, push.y * scale * 3 + 0.4 * scale, push.z * scale * 3);
            entity.hurtMarked = true;
        }

        level.playSound(null, centerBlock, dev.viridianengineering.registry.VESounds.BATTERY_DETONATE.get(), SoundSource.BLOCKS, 16f, 0.5f);
        PacketDistributor.sendToPlayersInDimension(level, new VENetwork.BatteryDetonated(center.x, center.y, center.z, radius, (float) f));

        // 3. terrain
        if (VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.get()) {
            BatteryFailureSavedData.get(level).addBlast(new ShellExplosionTask(e.eventId(), centerBlock, radius, 0));
        }
        ViridianEngineering.LOGGER.info("Mana battery detonated at {} (radius {}, terrain {})", centerBlock, radius,
                VEConfig.BATTERY_EXPLOSION_TERRAIN_DAMAGE.get());
    }

    /** Runs a blast to completion synchronously from a GameTest, returning it; normal play steps it per tick. */
    @Nullable
    public static ShellExplosionTask blastFor(ServerLevel level, UUID id) {
        for (ShellExplosionTask t : BatteryFailureSavedData.get(level).blasts()) if (t.id.equals(id)) return t;
        return null;
    }
}
