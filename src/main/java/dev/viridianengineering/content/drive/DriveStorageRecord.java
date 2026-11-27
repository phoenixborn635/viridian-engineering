package dev.viridianengineering.content.drive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contents of one Terra Drive (spec 3.6): item key -> long count. The tier capacity limits the
 * total item count only; the number of distinct keys is unlimited. All mutation goes through
 * {@link #insert} / {@link #extract}, which report changes to the owning SavedData.
 */
public final class DriveStorageRecord {

    private final LinkedHashMap<ItemStackKey, Long> counts = new LinkedHashMap<>();
    private final long capacity;
    private long total;
    private Runnable onChange = () -> {};

    public DriveStorageRecord(long capacity) {
        this.capacity = capacity;
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public long capacity() { return capacity; }
    public long total() { return total; }
    public long free() { return Math.max(0, capacity - total); }
    public int distinctKeys() { return counts.size(); }

    public long count(ItemStackKey key) {
        return counts.getOrDefault(key, 0L);
    }

    /** Read-only, insertion-ordered view. */
    public Map<ItemStackKey, Long> entries() {
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Inserts up to {@code amount} of {@code key}. New keys must already have passed the
     * component-payload check (see {@link TerraDriveSavedData#insert}).
     *
     * @return how many were (or would be) accepted
     */
    long insert(ItemStackKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long accepted = Math.min(amount, free());
        if (accepted <= 0 || simulate) return accepted;
        counts.merge(key, accepted, Long::sum);
        total += accepted;
        onChange.run();
        return accepted;
    }

    /** @return how many were (or would be) removed */
    long extract(ItemStackKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long have = count(key);
        long removed = Math.min(have, amount);
        if (removed <= 0 || simulate) return removed;
        if (removed == have) counts.remove(key); else counts.put(key, have - removed);
        total -= removed;
        onChange.run();
        return removed;
    }

    /** Load path only: restores a saved entry without capacity checks. */
    void restore(ItemStackKey key, long count) {
        if (count <= 0) return;
        counts.merge(key, count, Long::sum);
        total += count;
    }
}
