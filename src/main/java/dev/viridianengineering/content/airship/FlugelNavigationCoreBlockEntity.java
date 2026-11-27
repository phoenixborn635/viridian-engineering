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
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.item.CoordBoundItem;

import java.util.List;

/**
 * Eye of the Flügel Navigation Core (spec 3.8). Holds one Botania Eye of the Flügel (right-click to
 * insert, empty hand to take it back), reads the position it is bound to in this dimension, and flies
 * the ship there at 10 mana/t:
 * <ul>
 *   <li><b>altitude:</b> a vertical-speed setpoint toward {@code flugelArrivalHeight} above the target,
 *       followed by the ship's levitation pylons;</li>
 *   <li><b>course:</b> a horizontal-velocity setpoint toward the target (pylons) and a throttle for
 *       every Gaia Thruster aboard, by how well it points along the course;</li>
 *   <li><b>heading:</b> turns the ship so the core's front faces the course.</li>
 * </ul>
 * It stops navigating (and draining) once within {@code flugelArrivalRadius} of the target, and
 * pauses while it gets a redstone signal. The ship then hovers where it is.
 */
public class FlugelNavigationCoreBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, BlockEntitySubLevelActor {

    public enum Status { NO_EYE, UNBOUND, NOT_ON_SHIP, PAUSED, NO_MANA, NAVIGATING, ARRIVED }

    private static final double K_ALTITUDE = 0.8, K_YAW = 2.5, K_YAW_DAMP = 2.0, BRAKE_DISTANCE = 16;

    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.AIRSHIP_BLOCK_MANA_BUFFER.get(), true, false);

    private ItemStack eye = ItemStack.EMPTY;
    private Status status = Status.NO_EYE;
    @Nullable private BlockPos target;
    private double distance;
    private final Vector3d heading = new Vector3d(1, 0, 0);
    private long lastSyncedMana = -1;
    private Status lastSyncedStatus = Status.NO_EYE;

    public FlugelNavigationCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public ItemStack eye() { return eye; }

    public Status status() { return status; }

    @Nullable public BlockPos target() { return target; }

    public double distance() { return distance; }

    public static boolean isEye(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(BotaniaCapabilities.COORD_BOUND_ITEM) != null
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("flugel_eye");
    }

    public void setEye(ItemStack stack) {
        eye = stack.copyWithCount(Math.min(1, stack.getCount()));
        setChanged();
        sendData();
    }

    public ItemStack takeEye() {
        ItemStack out = eye;
        eye = ItemStack.EMPTY;
        setChanged();
        sendData();
        return out;
    }

    public Direction front() { return getBlockState().getValue(HorizontalDirectionalBlock.FACING); }

    @Nullable
    private BlockPos binding() {
        if (eye.isEmpty()) return null;
        CoordBoundItem bound = eye.getCapability(BotaniaCapabilities.COORD_BOUND_ITEM);
        return bound == null ? null : bound.getBinding(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        ServerSubLevel ship = ShipFlight.ship(this);
        target = binding();
        Status s;
        if (eye.isEmpty()) s = Status.NO_EYE;
        else if (target == null) s = Status.UNBOUND;
        else if (ship == null) s = Status.NOT_ON_SHIP;
        else if (level.hasNeighborSignal(worldPosition)) s = Status.PAUSED;
        else s = navigate(ship, now);
        status = s;
        boolean manaChanged = now % 10 == 0 && mana.getStoredMana() != lastSyncedMana;
        if (manaChanged || status != lastSyncedStatus) {
            lastSyncedMana = mana.getStoredMana();
            lastSyncedStatus = status;
            sendData();
        }
    }

    private Status navigate(ServerSubLevel ship, long now) {
        Vector3d here = ShipFlight.worldCom(ship);
        Vector3d goal = new Vector3d(target.getX() + 0.5, target.getY() + VEConfig.FLUGEL_ARRIVAL_HEIGHT.get(), target.getZ() + 0.5);
        Vector3d d = goal.sub(here, new Vector3d());
        double dh = Math.hypot(d.x, d.z);
        distance = d.length();
        int radius = VEConfig.FLUGEL_ARRIVAL_RADIUS.get();
        // arrive inside the radius; resume only once pushed well outside it again (no dithering)
        boolean wasArrived = status == Status.ARRIVED;
        if (dh <= radius && Math.abs(d.y) <= 2 || wasArrived && dh <= radius * 2 && Math.abs(d.y) <= 4) return Status.ARRIVED;
        if (!mana.consume(VEConfig.FLUGEL_AUTOPILOT_MANA_PER_TICK.get())) return Status.NO_MANA;

        double climb = VEConfig.LEVITATION_CLIMB_SPEED.get();
        double vy = Math.max(-climb, Math.min(climb, K_ALTITUDE * d.y));
        if (dh > 1e-6) heading.set(d.x / dh, 0, d.z / dh);
        double cruise = VEConfig.FLUGEL_CRUISE_SPEED.get();
        double speed = Math.min(cruise, 0.5 * dh);
        Vector3d horizontal = new Vector3d(heading).mul(speed);
        double throttle = Math.min(1, dh / BRAKE_DISTANCE);
        ShipFlight.state(ship.getUniqueId()).setAutopilot(now, vy, horizontal, heading, throttle);
        return Status.NAVIGATING;
    }

    /** Heading: a yaw torque turning the core's front toward the course. */
    @Override
    public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle handle, double dt) {
        if (status != Status.NAVIGATING || level == null) return;
        Direction f = front();
        Vector3d fwd = ship.logicalPose().transformNormal(new Vector3d(f.getStepX(), 0, f.getStepZ()), new Vector3d());
        fwd.y = 0;
        if (fwd.lengthSquared() < 1e-6) return;
        fwd.normalize();
        double sin = fwd.z * heading.x - fwd.x * heading.z; // (fwd x heading).y
        double cos = fwd.dot(heading);
        double angle = Math.atan2(sin, cos);
        Vector3d w = handle.getAngularVelocity(new Vector3d());
        Vector3d alpha = new Vector3d(0, K_YAW * angle - K_YAW_DAMP * w.y, 0);
        ShipFlight.angularAccel(ship, ForceGroups.PROPULSION.get(), alpha, dt);
    }

    // ------------------------------------------------------------------ info / data

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.flugel_navigation_core")));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.flugel."
                + status.name().toLowerCase())).withStyle(status == Status.NAVIGATING ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        if (target != null)
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.flugel.target",
                    target.getX(), target.getY(), target.getZ())).withStyle(ChatFormatting.WHITE));
        if (status == Status.NAVIGATING)
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.flugel.distance",
                    Math.round(distance))).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), mana.getManaCapacity())).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        if (!eye.isEmpty()) tag.put("Eye", eye.save(registries));
        tag.putString("Status", status.name());
        if (clientPacket && target != null) tag.putLong("Target", target.asLong());
        if (clientPacket) tag.putDouble("Distance", distance);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        eye = tag.contains("Eye") ? ItemStack.parse(registries, tag.getCompound("Eye")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        try { status = Status.valueOf(tag.getString("Status")); } catch (IllegalArgumentException e) { status = Status.NO_EYE; }
        if (clientPacket) {
            target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
            distance = tag.getDouble("Distance");
        }
    }
}
