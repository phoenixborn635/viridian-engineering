package dev.viridianengineering.content.airship;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-ship flight state shared by the Phase 5 blocks, plus the Sable force plumbing they use.
 * <p>
 * Everything here is transient: it is rebuilt from the blocks every tick (pylons re-register, the
 * Flügel core re-sends its setpoints), so nothing needs saving. Forces are applied the way
 * Aeronautics does it: an impulse ({@code force * timeStep}) in the ship's local (plot) frame,
 * at a point in plot coordinates, on one of Sable's {@code ForceGroup}s.
 */
public final class ShipFlight {

    private ShipFlight() {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** One ship's flight state. */
    public static final class State {
        /** Pylon (plot position) -> last game tick it paid its mana and is lifting. */
        final Map<BlockPos, Long> pylons = new ConcurrentHashMap<>();
        /** Hover altitude (world y of the centre of mass) held while pylons hover. */
        double holdY;
        long lastHoverTick = Long.MIN_VALUE;
        // ---- Eye of the Flügel autopilot setpoints (world frame) ----
        long autopilotTick = Long.MIN_VALUE;
        double apTargetVy;
        final Vector3d apHorizontalVelocity = new Vector3d();
        final Vector3d apHeading = new Vector3d(1, 0, 0);
        double apThrottle;

        public int activePylons(long now) {
            int n = 0;
            for (var it = pylons.entrySet().iterator(); it.hasNext(); ) {
                long t = it.next().getValue();
                if (t >= now - 1) n++;
                else if (t < now - 40) it.remove();
            }
            return n;
        }

        public boolean autopilot(long now) { return autopilotTick >= now - 2; }
        public double autopilotTargetVy() { return apTargetVy; }
        public Vector3dc autopilotHorizontalVelocity() { return apHorizontalVelocity; }
        public Vector3dc autopilotHeading() { return apHeading; }
        public double autopilotThrottle() { return apThrottle; }

        public void setAutopilot(long now, double targetVy, Vector3dc horizontalVelocity, Vector3dc heading, double throttle) {
            autopilotTick = now;
            apTargetVy = targetVy;
            apHorizontalVelocity.set(horizontalVelocity);
            apHeading.set(heading);
            apThrottle = throttle;
        }
    }

    public static State state(UUID ship) {
        return STATES.computeIfAbsent(ship, k -> new State());
    }

    @Nullable
    public static State existing(UUID ship) { return STATES.get(ship); }

    /** Server stop: drop everything (ship ids are re-registered by the blocks next time). */
    public static void clear() { STATES.clear(); }

    // ---------------------------------------------------------------- Sable helpers

    @Nullable
    public static ServerSubLevel ship(BlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide) return null;
        SubLevel s = Sable.HELPER.getContaining(be);
        return s instanceof ServerSubLevel ss && !ss.isRemoved() ? ss : null;
    }

    public static Vector3d gravity(Level level) {
        return DimensionPhysicsData.getGravity(level);
    }

    /** World position of the ship's centre of mass. */
    public static Vector3d worldCom(ServerSubLevel ship) {
        return ship.logicalPose().transformPosition(new Vector3d(ship.getMassTracker().getCenterOfMass()), new Vector3d());
    }

    /** A plot-space block centre as a world-space point. */
    public static Vec3 worldCenter(ServerSubLevel ship, BlockPos plotPos) {
        return ship.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
    }

    /** A force (world frame) through the centre of mass for one substep of {@code dt} seconds. */
    public static void forceAtCom(ServerSubLevel ship, ForceGroup group, Vector3dc worldForce, double dt) {
        Vector3d local = ship.logicalPose().transformNormalInverse(new Vector3d(worldForce), new Vector3d()).mul(dt);
        QueuedForceGroup g = ship.getOrCreateQueuedForceGroup(group);
        g.applyAndRecordPointForce(new Vector3d(ship.getMassTracker().getCenterOfMass()), local);
    }

    /** A force (ship-local frame) at a plot-space point for one substep. */
    public static void localForceAt(ServerSubLevel ship, ForceGroup group, Vector3dc plotPoint, Vector3dc localForce, double dt) {
        QueuedForceGroup g = ship.getOrCreateQueuedForceGroup(group);
        g.applyAndRecordPointForce(plotPoint, new Vector3d(localForce).mul(dt));
    }

    /** An angular acceleration (world frame, rad/s²) for one substep, scaled by the ship's inertia. */
    public static void angularAccel(ServerSubLevel ship, ForceGroup group, Vector3dc worldAlpha, double dt) {
        MassData mass = ship.getMassTracker();
        Vector3d local = ship.logicalPose().transformNormalInverse(new Vector3d(worldAlpha), new Vector3d());
        mass.getInertiaTensor().transform(local);
        ship.getOrCreateQueuedForceGroup(group).getForceTotal().applyLinearAndAngularImpulse(new Vector3d(), local.mul(dt));
    }
}
