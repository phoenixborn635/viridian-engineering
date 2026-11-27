package dev.viridianengineering.content.drive;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Spec 3.6: server-authoritative storage for every Terra Drive, keyed by the UUID the drive item
 * carries. Also holds the single-mount locks (drive UUID -> pylon endpoint), so a duplicated drive
 * item can never expose the same storage through two pylons. Lives in the overworld's data storage
 * so drives work in any dimension and on Sable ships.
 */
public final class TerraDriveSavedData extends SavedData {

    public static final String ID = ViridianEngineering.MOD_ID + "_terra_drives";

    private final Map<UUID, DriveStorageRecord> drives = new HashMap<>();
    private final Map<UUID, MachineEndpointKey> mounts = new HashMap<>();
    private HolderLookup.Provider registries;

    public TerraDriveSavedData() {}

    public static TerraDriveSavedData get(MinecraftServer server) {
        TerraDriveSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerraDriveSavedData::new, TerraDriveSavedData::load, null), ID);
        data.registries = server.registryAccess();
        return data;
    }

    // ------------------------------------------------------------------ records

    public DriveStorageRecord getOrCreate(UUID id, long capacity) {
        return drives.computeIfAbsent(id, k -> {
            DriveStorageRecord record = new DriveStorageRecord(capacity);
            record.setOnChange(this::setDirty);
            setDirty();
            return record;
        });
    }

    public DriveStorageRecord find(UUID id) {
        return drives.get(id);
    }

    public enum Rejection { NONE, FULL, COMPONENT_PAYLOAD }

    /** Whether {@code stack} may enter {@code record} at all (ignoring how many fit). */
    public Rejection check(DriveStorageRecord record, ItemStack stack) {
        if (stack.isEmpty()) return Rejection.FULL;
        ItemStackKey key = ItemStackKey.of(stack);
        if (record.count(key) == 0 && key.payloadBytes(registries) > VEConfig.MAX_COMPONENT_PAYLOAD_BYTES.get())
            return Rejection.COMPONENT_PAYLOAD;
        return record.free() > 0 ? Rejection.NONE : Rejection.FULL;
    }

    /** @return how many items of {@code stack} were (or would be) accepted */
    public long insert(DriveStorageRecord record, ItemStack stack, long amount, boolean simulate) {
        if (check(record, stack) != Rejection.NONE) return 0;
        return record.insert(ItemStackKey.of(stack), amount, simulate);
    }

    public long extract(DriveStorageRecord record, ItemStackKey key, long amount, boolean simulate) {
        return record.extract(key, amount, simulate);
    }

    // ------------------------------------------------------------------ single-mount locks

    /**
     * Locks {@code id} to the pylon at {@code at}. An existing lock elsewhere is taken over only when
     * {@code stillHeld} says that pylon no longer holds the drive (it was broken or replaced).
     */
    public boolean tryMount(UUID id, MachineEndpointKey at, Predicate<MachineEndpointKey> stillHeld) {
        MachineEndpointKey current = mounts.get(id);
        if (current != null && !current.equals(at) && stillHeld.test(current)) return false;
        if (!at.equals(current)) {
            mounts.put(id, at);
            setDirty();
        }
        return true;
    }

    public void unmount(UUID id, MachineEndpointKey at) {
        if (at.equals(mounts.get(id))) {
            mounts.remove(id);
            setDirty();
        }
    }

    /** A pylon changed key (its ship was assembled or disassembled): its drive locks follow it. */
    public void rekey(MachineEndpointKey from, MachineEndpointKey to) {
        boolean changed = false;
        for (var e : mounts.entrySet()) if (from.equals(e.getValue())) { e.setValue(to); changed = true; }
        if (changed) setDirty();
    }

    public MachineEndpointKey mountOf(UUID id) {
        return mounts.get(id);
    }

    /**
     * Writes pending drive changes to disk now, so a drive taken out of a pylon never refers to
     * contents that exist only in memory (spec 3.6 "flushes dirty entries before extraction").
     */
    public void flush(MinecraftServer server) {
        if (isDirty()) server.overworld().getDataStorage().save();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        drives.forEach((id, record) -> {
            CompoundTag drive = new CompoundTag();
            drive.putUUID("Id", id);
            drive.putLong("Capacity", record.capacity());
            ListTag entries = new ListTag();
            record.entries().forEach((key, count) -> {
                CompoundTag entry = new CompoundTag();
                entry.put("Item", key.toStack(1).save(registries));
                entry.putLong("Count", count);
                entries.add(entry);
            });
            drive.put("Entries", entries);
            list.add(drive);
        });
        tag.put("Drives", list);

        ListTag locks = new ListTag();
        mounts.forEach((id, at) -> MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE, at).result().ifPresent(encoded -> {
            CompoundTag lock = new CompoundTag();
            lock.putUUID("Id", id);
            lock.put("At", encoded);
            locks.add(lock);
        }));
        tag.put("Mounts", locks);
        return tag;
    }

    public static TerraDriveSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerraDriveSavedData data = new TerraDriveSavedData();
        data.registries = registries;
        for (Tag t : tag.getList("Drives", Tag.TAG_COMPOUND)) {
            CompoundTag drive = (CompoundTag) t;
            DriveStorageRecord record = new DriveStorageRecord(drive.getLong("Capacity"));
            for (Tag e : drive.getList("Entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) e;
                ItemStack proto = ItemStack.parseOptional(registries, entry.getCompound("Item"));
                if (proto.isEmpty()) {
                    ViridianEngineering.LOGGER.warn("Terra Drive {}: dropped {} items of a type that no longer exists",
                            drive.getUUID("Id"), entry.getLong("Count"));
                    continue;
                }
                record.restore(ItemStackKey.of(proto), entry.getLong("Count"));
            }
            record.setOnChange(data::setDirty);
            data.drives.put(drive.getUUID("Id"), record);
        }
        for (Tag t : tag.getList("Mounts", Tag.TAG_COMPOUND)) {
            CompoundTag lock = (CompoundTag) t;
            MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE, lock.get("At")).result()
                    .ifPresent(at -> data.mounts.put(lock.getUUID("Id"), at));
        }
        return data;
    }
}
