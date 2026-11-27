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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.List;

/**
 * Gaia Thruster (spec 3.8), a 3x3x3 engine. The bell points along {@code FACING}; the ship is pushed the
 * other way, at the engine's centre (so off-centre thrusters also turn the ship).
 * <p>
 * Throttle = redstone signal at the redstone port / 15 (Aeronautics throttle lever, redstone links ...). While the ship's
 * Eye of the Flügel Navigation Core is navigating, the thruster instead fires as far as it points
 * along the course. Mana: 60/t at 100%, linear in throttle (fractions carried between ticks, so
 * 50% is exactly 30/t on average).
 */
public class GaiaThrusterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, BlockEntitySubLevelActor {

    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.AIRSHIP_BLOCK_MANA_BUFFER.get(), true, false);

    /** Throttle actually paid for this tick, 0..1. */
    private double throttle;
    /** Mana owed for fractional throttle, carried between ticks. */
    private double debt;
    private boolean autopilot;
    private long lastSyncedMana = -1;
    private int lastSyncedThrottle = -1;

    public GaiaThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Direction nozzle() { return getBlockState().getValue(DirectionalBlock.FACING); }

    public double throttle() { return throttle; }

    /** The block in front of the redstone port (the throttle signal comes from there), like the cannon. */
    public BlockPos redstoneInput() {
        return dev.viridianengineering.content.cannon.GaiaCannonBlock.portCell(worldPosition, nozzle(), false)
                .relative(dev.viridianengineering.content.cannon.GaiaCannonBlock.portFace(nozzle(), false));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        ServerSubLevel ship = ShipFlight.ship(this);
        double want = level.getSignal(redstoneInput(), dev.viridianengineering.content.cannon.GaiaCannonBlock.portFace(nozzle(), false)) / 15.0;
        autopilot = false;
        if (ship == null) want = 0; // off a ship it has nothing to push
        else {
            ShipFlight.State st = ShipFlight.existing(ship.getUniqueId());
            if (st != null && st.autopilot(now)) {
                Vector3d push = ship.logicalPose().transformNormal(pushDirection(), new Vector3d());
                Vector3d heading = new Vector3d(st.autopilotHeading());
                want = Math.max(0, push.dot(heading)) * st.autopilotThrottle();
                autopilot = true;
            }
        }
        want = Math.max(0, Math.min(1, want));
        // pay for it: 60 * throttle per tick, the fraction carried over
        double cost = VEConfig.GAIA_THRUSTER_MAX_MANA_PER_TICK.get() * want + debt;
        long whole = (long) Math.floor(cost);
        if (want > 0 && mana.consume(whole)) {
            debt = cost - whole;
            throttle = want;
        } else {
            throttle = 0;
            if (want <= 0) debt = 0;
        }
        boolean manaChanged = now % 10 == 0 && mana.getStoredMana() != lastSyncedMana;
        int t = (int) Math.round(throttle * 20);
        if (manaChanged || t != lastSyncedThrottle) {
            lastSyncedMana = mana.getStoredMana();
            lastSyncedThrottle = t;
            sendData();
        }
    }

    /** Direction the ship is pushed, in the ship's local (plot) frame: opposite the nozzle. */
    public Vector3d pushDirection() {
        Direction d = nozzle().getOpposite();
        return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    @Override
    public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle handle, double dt) {
        if (throttle <= 0) return;
        Vector3d force = pushDirection().mul(VEConfig.GAIA_THRUSTER_MAX_THRUST.get() * throttle);
        Vector3d at = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        ShipFlight.localForceAt(ship, ForceGroups.PROPULSION.get(), at, force, dt);
    }

    // ------------------------------------------------------------------ info / data

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.gaia_thruster")));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.thruster.throttle",
                Math.round(throttle * 100))).withStyle(throttle > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        if (autopilot)
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.autopilot"))
                    .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), mana.getManaCapacity())).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(12); // 3x3x3 body plus the full-throttle plume
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        tag.putDouble("Debt", debt);
        if (clientPacket) tag.putDouble("Throttle", throttle);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        debt = tag.getDouble("Debt");
        if (clientPacket) throttle = tag.getDouble("Throttle");
    }
}
