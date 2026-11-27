package dev.viridianengineering.content.battery;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-dimension record of pending battery detonations and blasts still in progress (spec 3.4 step e). */
public class BatteryFailureSavedData extends SavedData {

    public static final String ID = "viridian_engineering_battery_failures";

    private final LinkedHashMap<UUID, BatteryFailureEntry> entries = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ShellExplosionTask> blasts = new LinkedHashMap<>();

    public static BatteryFailureSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BatteryFailureSavedData::new, BatteryFailureSavedData::load, null), ID);
    }

    public Collection<BatteryFailureEntry> entries() { return Collections.unmodifiableCollection(entries.values()); }

    public Collection<ShellExplosionTask> blasts() { return Collections.unmodifiableCollection(blasts.values()); }

    public Optional<BatteryFailureEntry> entry(UUID id) { return Optional.ofNullable(entries.get(id)); }

    public void add(BatteryFailureEntry e) { entries.put(e.eventId(), e); setDirty(); }

    public void remove(UUID id) { if (entries.remove(id) != null) setDirty(); }

    public void addBlast(ShellExplosionTask t) { blasts.put(t.id, t); setDirty(); }

    public void removeBlast(UUID id) { if (blasts.remove(id) != null) setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Entries", Codec.list(BatteryFailureEntry.CODEC).encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values()))
                .getOrThrow());
        tag.put("Blasts", Codec.list(ShellExplosionTask.CODEC).encodeStart(NbtOps.INSTANCE, List.copyOf(blasts.values()))
                .getOrThrow());
        return tag;
    }

    public static BatteryFailureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BatteryFailureSavedData data = new BatteryFailureSavedData();
        if (tag.contains("Entries", Tag.TAG_LIST))
            Codec.list(BatteryFailureEntry.CODEC).parse(NbtOps.INSTANCE, tag.get("Entries")).result()
                    .ifPresent(l -> l.forEach(e -> data.entries.put(e.eventId(), e)));
        if (tag.contains("Blasts", Tag.TAG_LIST))
            Codec.list(ShellExplosionTask.CODEC).parse(NbtOps.INSTANCE, tag.get("Blasts")).result()
                    .ifPresent(l -> l.forEach(t -> data.blasts.put(t.id, t)));
        return data;
    }
}
