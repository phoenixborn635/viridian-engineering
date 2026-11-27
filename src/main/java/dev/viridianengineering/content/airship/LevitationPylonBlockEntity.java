package dev.viridianengineering.content.airship;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * Mana Levitation Pylon (spec 3.8). On an assembled ship it holds the ship up against gravity and
 * keeps it level, sharing the load with every other powered pylon aboard. 15 mana/t while hovering,
 * 45 mana/t while climbing, descending or flying the Flügel autopilot.
 * <p>
 * Redstone 1–7 climbs, 8–15 descends, and 0 holds altitude. A navigating Flügel core
 * supplies the automatic flight setpoint while no redstone is applied.
 * <p>
 * A pylon off a ship, or without mana, does nothing and uses nothing.
 */
public class LevitationPylonBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, BlockEntitySubLevelActor {

    public enum Mode { IDLE, HOVER, CLIMB, DESCEND, AUTOPILOT }

    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.AIRSHIP_BLOCK_MANA_BUFFER.get(), true, false);

    /** Gains of the lift controller (per second). */
    private static final double K_VERTICAL = 3.0, K_ALTITUDE = 1.5, K_HORIZONTAL = 2.0, BRAKE = 0.6,
            K_LEVEL = 6.0, K_SPIN = 3.0, MAX_HORIZONTAL_ACCEL = 3.0;

    private Mode mode = Mode.IDLE;
    private int syncedSignal;
    private double targetVy;
    private long lastSyncedMana = -1;
    private Mode lastSyncedMode = Mode.IDLE;

    public LevitationPylonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Mode mode() { return mode; }

    /** Strongest signal on either half of the pylon. */
    public int signal() {
        if (level == null || level.isClientSide) return syncedSignal;
        return Math.max(level.getBestNeighborSignal(worldPosition), level.getBestNeighborSignal(worldPosition.above()));
    }

    /** Mana per tick for a mode (spec: 15 hover, 45 climb/manoeuvre). */
    public static long manaFor(Mode m) {
        return switch (m) {
            case IDLE -> 0;
            case HOVER -> VEConfig.LEVITATION_HOVER_MANA_PER_TICK.get();
            default -> VEConfig.LEVITATION_ACTIVE_MANA_PER_TICK.get();
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        ServerSubLevel ship = ShipFlight.ship(this);
        Mode want = Mode.IDLE;
        double vy = 0;
        long now = level.getGameTime();
        int s = signal();
        if (ship != null) {
            ShipFlight.State st = ShipFlight.state(ship.getUniqueId());
            double climb = VEConfig.LEVITATION_CLIMB_SPEED.get();
            if (s == 0 && st.autopilot(now)) {
                want = Mode.AUTOPILOT;
                vy = st.autopilotTargetVy();
            } else if (s == 0) {
                want = Mode.HOVER;
            } else if (s <= 7) {
                want = Mode.CLIMB;
                vy = climb * s / 7.0;
            } else {
                want = Mode.DESCEND;
                vy = -climb * (s - 7) / 8.0;
            }
            if (mana.consume(manaFor(want))) {
                double y = ShipFlight.worldCom(ship).y;
                if (want == Mode.HOVER) {
                    if (st.lastHoverTick < now - 1) st.holdY = y; // just started hovering: hold here
                    st.lastHoverTick = Math.max(st.lastHoverTick, now);
                    vy = Math.max(-climb, Math.min(climb, K_ALTITUDE * (st.holdY - y)));
                }
                st.pylons.put(worldPosition.immutable(), now);
            } else {
                want = Mode.IDLE;
                vy = 0;
            }
        }
        mode = want;
        targetVy = vy;
        boolean manaChanged = now % 10 == 0 && mana.getStoredMana() != lastSyncedMana;
        if (manaChanged || mode != lastSyncedMode || s != syncedSignal) {
            lastSyncedMana = mana.getStoredMana();
            lastSyncedMode = mode;
            syncedSignal = s;
            sendData();
        }
    }

    /** Lift, course-keeping and levelling, split evenly between the ship's active pylons. */
    @Override
    public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle handle, double dt) {
        if (mode == Mode.IDLE || level == null) return;
        long now = level.getGameTime();
        ShipFlight.State st = ShipFlight.state(ship.getUniqueId());
        Long mine = st.pylons.get(worldPosition);
        if (mine == null || mine < now - 1) return;
        int n = Math.max(1, st.activePylons(now));
        double m = ship.getMassTracker().getMass();
        if (!(m > 0)) return;
        Vector3d g = ShipFlight.gravity(level);
        double gMag = g.length();
        Vector3d v = handle.getLinearVelocity(new Vector3d());

        // linear: cancel gravity, steer vertical speed, and brake (or, on autopilot, steer) horizontally
        Vector3d accel = new Vector3d(g).negate();
        accel.y += K_VERTICAL * (targetVy - v.y);
        Vector3d horizontal;
        if (mode == Mode.AUTOPILOT) {
            Vector3d want = new Vector3d(st.autopilotHorizontalVelocity());
            horizontal = want.sub(v.x, 0, v.z).mul(K_HORIZONTAL);
            if (horizontal.length() > MAX_HORIZONTAL_ACCEL) horizontal.normalize(MAX_HORIZONTAL_ACCEL);
        } else {
            horizontal = new Vector3d(-v.x, 0, -v.z).mul(BRAKE);
        }
        accel.add(horizontal.x, 0, horizontal.z);
        Vector3d force = accel.mul(m / n);
        double maxPerPylon = VEConfig.LEVITATION_LIFT_CAPACITY.get() * gMag * 1.5;
        if (force.y < 0) force.y = 0; // pylons lift, they never pull down: descending = less lift
        if (force.length() > maxPerPylon) force.normalize(maxPerPylon);
        ShipFlight.forceAtCom(ship, ForceGroups.LEVITATION.get(), force, dt);

        // angular: keep the deck level, damp spin (the Flügel core adds its own yaw steering)
        Vector3d up = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0), new Vector3d());
        Vector3d tilt = up.cross(0, 1, 0, new Vector3d());
        Vector3d w = handle.getAngularVelocity(new Vector3d());
        Vector3d alpha = tilt.mul(K_LEVEL).sub(w.mul(K_SPIN)).div(n);
        ShipFlight.angularAccel(ship, ForceGroups.LEVITATION.get(), alpha, dt);
    }

    // ------------------------------------------------------------------ info / data

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.mana_levitation_pylon")));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.pylon.mode."
                + mode.name().toLowerCase(Locale.ROOT))).withStyle(mode == Mode.IDLE ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), mana.getManaCapacity())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana_per_tick",
                manaFor(mode))).withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4, 16, 4);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        if (clientPacket) {
            tag.putString("Mode", mode.name());
            tag.putInt("Signal", syncedSignal);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        if (clientPacket) {
            syncedSignal = tag.getInt("Signal");
            try { mode = Mode.valueOf(tag.getString("Mode")); } catch (IllegalArgumentException e) { mode = Mode.IDLE; }
        }
    }
}
