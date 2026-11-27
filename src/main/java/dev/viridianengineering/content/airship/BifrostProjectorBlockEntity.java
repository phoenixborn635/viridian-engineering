package dev.viridianengineering.content.airship;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Bifrost Road Projector (spec 3.8). On a moving ship it follows, in world space, the cell just in
 * front of its lens ({@code FACING}); each time that cell changes (one voxel step) it leaves a Bifrost
 * trail block in the cell it just left, behind the ship: 50 mana per block, 200-tick life, at most 64
 * per ship ({@link BifrostTrails}), only where its placer may build. A redstone signal switches it off.
 */
public class BifrostProjectorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** At most this many voxel steps are filled in one tick (a very fast ship leaves a dotted trail). */
    private static final int MAX_STEPS_PER_TICK = 8;

    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.AIRSHIP_BLOCK_MANA_BUFFER.get(), true, false);

    @Nullable private UUID owner;
    @Nullable private BlockPos lastCell;
    private boolean projecting;
    private long lastStep = Long.MIN_VALUE / 2;
    private int placedTotal;
    private long lastSyncedMana = -1;
    private boolean lastSyncedProjecting;

    public BifrostProjectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public void setOwner(@Nullable UUID owner) { this.owner = owner; setChanged(); }

    public boolean projecting() { return projecting; }

    public int placedTotal() { return placedTotal; }

    public Direction lens() { return getBlockState().getValue(DirectionalBlock.FACING); }

    /** World cell just in front of the lens. */
    BlockPos frontCell(ServerSubLevel ship) {
        Direction f = lens();
        Vec3 local = Vec3.atCenterOf(worldPosition).add(f.getStepX() * 1.0, f.getStepY() * 1.0, f.getStepZ() * 1.0);
        return BlockPos.containing(ship.logicalPose().transformPosition(local));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        ServerSubLevel ship = ShipFlight.ship(this);
        boolean off = level.hasNeighborSignal(worldPosition);
        projecting = false;
        if (ship == null || off) {
            lastCell = null;
        } else {
            BlockPos cell = frontCell(ship);
            if (lastCell != null && !lastCell.equals(cell)) {
                stepTo((ServerLevel) level, ship.getUniqueId(), lastCell, cell);
                lastStep = level.getGameTime();
            }
            lastCell = cell;
            projecting = level.getGameTime() - lastStep <= 10; // the ship moved within the last half second
        }
        long now = level.getGameTime();
        boolean manaChanged = now % 10 == 0 && mana.getStoredMana() != lastSyncedMana;
        if (manaChanged || projecting != lastSyncedProjecting) {
            lastSyncedMana = mana.getStoredMana();
            lastSyncedProjecting = projecting;
            sendData();
        }
    }

    /** Walks voxel by voxel from {@code from} to {@code to}, leaving a block in every cell left behind. */
    private void stepTo(ServerLevel level, UUID ship, BlockPos from, BlockPos to) {
        long cost = VEConfig.BIFROST_MANA_PER_BLOCK.get();
        BifrostTrails trails = BifrostTrails.get(level);
        BlockPos cur = from;
        for (int i = 0; i < MAX_STEPS_PER_TICK && !cur.equals(to); i++) {
            if (mana.getStoredMana() >= cost && trails.tryPlace(level, cur, ship, owner)) {
                mana.consume(cost);
                placedTotal++;
            }
            int dx = Integer.signum(to.getX() - cur.getX()), dy = Integer.signum(to.getY() - cur.getY()),
                    dz = Integer.signum(to.getZ() - cur.getZ());
            // step along the axis with the most distance left (one voxel at a time)
            int ax = Math.abs(to.getX() - cur.getX()), ay = Math.abs(to.getY() - cur.getY()), az = Math.abs(to.getZ() - cur.getZ());
            if (ax >= ay && ax >= az) cur = cur.offset(dx, 0, 0);
            else if (ay >= az) cur = cur.offset(0, dy, 0);
            else cur = cur.offset(0, 0, dz);
        }
    }

    // ------------------------------------------------------------------ info / data

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.bifrost_projector")));
        tooltip.add(Component.literal("     ").append(Component.translatable(projecting
                ? "viridian_engineering.goggles.bifrost.projecting" : "viridian_engineering.goggles.bifrost.idle"))
                .withStyle(projecting ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), mana.getManaCapacity())).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        if (owner != null && !clientPacket) tag.putUUID("Owner", owner);
        if (!clientPacket) tag.putInt("Placed", placedTotal);
        if (clientPacket) tag.putBoolean("Projecting", projecting);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        if (!clientPacket) {
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            placedTotal = tag.getInt("Placed");
        }
        if (clientPacket) projecting = tag.getBoolean("Projecting");
    }
}
