package dev.viridianengineering.content.drive;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Corporea Archive Pylon (spec 3.6, LOCKED): exactly 4 Terra Drive slots. Each mounted drive's UUID
 * is locked to this pylon in {@link TerraDriveSavedData}; a drive whose UUID is already locked
 * elsewhere is refused (or, if found mounted after a load, kept but inactive). Pending drive data is
 * flushed to disk before any drive leaves the pylon.
 */
public class ArchivePylonBlockEntity extends SmartBlockEntity {

    public static final int SLOTS = 4;

    public enum MountResult { MOUNTED, NOT_A_DRIVE, NO_FREE_SLOT, LOCKED_ELSEWHERE }

    private final ItemStackHandler drives = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof TerraDriveItem; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    /** Server-side: slots whose drive holds this pylon's mount lock. Synced for the bay lights. */
    private final boolean[] active = new boolean[SLOTS];

    public ArchivePylonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public MachineEndpointKey endpoint() {
        return SableCompat.endpointKey(level, worldPosition, Direction.DOWN);
    }

    public ItemStack driveIn(int slot) { return drives.getStackInSlot(slot); }
    public boolean isActive(int slot) { return active[slot]; }

    public int mountedCount() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (!drives.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    // ------------------------------------------------------------------ mount / eject (server only)

    /** Mounts one drive from {@code held}; on success the caller shrinks its own stack by one. */
    public MountResult mount(ItemStack held) {
        if (!(level instanceof ServerLevel server) || !(held.getItem() instanceof TerraDriveItem item)) return MountResult.NOT_A_DRIVE;
        int slot = -1;
        for (int i = 0; i < SLOTS && slot < 0; i++) if (drives.getStackInSlot(i).isEmpty()) slot = i;
        if (slot < 0) return MountResult.NO_FREE_SLOT;
        ItemStack drive = held.copyWithCount(1);
        UUID id = TerraDriveItem.getOrAssignId(drive);
        TerraDriveSavedData data = TerraDriveSavedData.get(server.getServer());
        if (!data.tryMount(id, endpoint(), at -> stillHeld(server.getServer(), at, id))) return MountResult.LOCKED_ELSEWHERE;
        data.getOrCreate(id, item.capacity());
        drives.setStackInSlot(slot, drive);
        active[slot] = true;
        changed();
        return MountResult.MOUNTED;
    }

    /** Removes the drive in the highest occupied slot, after flushing its data. Empty if none. */
    public ItemStack ejectLast() {
        for (int i = SLOTS - 1; i >= 0; i--) if (!drives.getStackInSlot(i).isEmpty()) return eject(i);
        return ItemStack.EMPTY;
    }

    public ItemStack eject(int slot) {
        ItemStack drive = drives.getStackInSlot(slot);
        if (drive.isEmpty() || !(level instanceof ServerLevel server)) return ItemStack.EMPTY;
        release(server.getServer(), drive, slot);
        drives.setStackInSlot(slot, ItemStack.EMPTY);
        changed();
        return drive;
    }

    /** Block removal: every drive drops as an item, with its data flushed and its lock released. */
    public void dropDrives() {
        if (!(level instanceof ServerLevel server)) return;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack drive = drives.getStackInSlot(i);
            if (drive.isEmpty()) continue;
            release(server.getServer(), drive, i);
            drives.setStackInSlot(i, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, drive);
        }
    }

    private void release(MinecraftServer server, ItemStack drive, int slot) {
        TerraDriveSavedData data = TerraDriveSavedData.get(server);
        UUID id = TerraDriveItem.getId(drive);
        if (id != null) {
            DriveStorageRecord record = data.find(id);
            if (record != null) TerraDriveItem.cacheUsed(drive, record.total());
            if (active[slot]) data.unmount(id, endpoint());
        }
        active[slot] = false;
        data.flush(server);
    }

    /** True while the pylon at {@code at} still holds drive {@code id}, or can't be checked (unloaded). */
    private static boolean stillHeld(MinecraftServer server, MachineEndpointKey at, UUID id) {
        ServerLevel level = server.getLevel(at.dimension());
        if (level == null) return false;
        BlockPos pos = SableCompat.resolve(level, at);
        if (pos == null || !level.isLoaded(pos)) return true;
        if (!(level.getBlockEntity(pos) instanceof ArchivePylonBlockEntity pylon)) return false;
        for (int i = 0; i < SLOTS; i++)
            if (id.equals(TerraDriveItem.getId(pylon.drives.getStackInSlot(i)))) return true;
        return false;
    }

    // ------------------------------------------------------------------ network access

    /** One view per active drive, for {@link CorporeaNetworkManager}. */
    public List<IItemHandler> storageViews() {
        if (!(level instanceof ServerLevel server)) return List.of();
        TerraDriveSavedData data = TerraDriveSavedData.get(server.getServer());
        List<IItemHandler> views = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            if (!active[i]) continue;
            ItemStack drive = drives.getStackInSlot(i);
            UUID id = TerraDriveItem.getId(drive);
            if (id == null || !(drive.getItem() instanceof TerraDriveItem item)) continue;
            views.add(new DriveInventoryView(data, data.getOrCreate(id, item.capacity())));
        }
        return views;
    }

    // ------------------------------------------------------------------ lifecycle / sync

    @Override
    public void initialize() {
        super.initialize();
        if (!(level instanceof ServerLevel server)) return;
        // Re-claim locks after a load. A copy of a drive that is locked elsewhere stays inactive.
        TerraDriveSavedData data = TerraDriveSavedData.get(server.getServer());
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack drive = drives.getStackInSlot(i);
            UUID id = TerraDriveItem.getId(drive);
            boolean ok = id != null && data.tryMount(id, endpoint(), at -> stillHeld(server.getServer(), at, id));
            if (ok != active[i]) { active[i] = ok; changed = true; }
        }
        if (changed) changed();
    }

    private void changed() {
        setChanged();
        CorporeaNetworkManager.invalidate(level);
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            BlockState lit = state;
            for (int i = 0; i < SLOTS; i++) lit = lit.setValue(ArchivePylonBlock.BAY[i], active[i]);
            if (lit != state) level.setBlock(worldPosition, lit, 3);
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Drives", drives.serializeNBT(registries));
        byte mask = 0;
        for (int i = 0; i < SLOTS; i++) if (active[i]) mask |= (byte) (1 << i);
        tag.putByte("Active", mask);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        drives.deserializeNBT(registries, tag.getCompound("Drives"));
        byte mask = tag.getByte("Active");
        for (int i = 0; i < SLOTS; i++) active[i] = (mask & (1 << i)) != 0;
    }
}
