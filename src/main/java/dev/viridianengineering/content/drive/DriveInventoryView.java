package dev.viridianengineering.content.drive;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * One Terra Drive presented to the Corporea network as an item handler, so browse / deposit /
 * withdraw treat drives and ordinary inventories alike. Built fresh for each network operation.
 * <p>
 * Slots are a snapshot of the drive's keys at creation plus one trailing empty slot for new keys,
 * so indices stay stable while a caller loops over them. Insertion ignores the slot index and
 * files the stack under its own key; counts per slot can exceed a vanilla stack (at most the drive
 * capacity, 1,048,576, so an int is always enough here; network totals are summed as longs).
 */
public final class DriveInventoryView implements IItemHandler {

    private final TerraDriveSavedData data;
    private final DriveStorageRecord record;
    private final List<ItemStackKey> keys;

    public DriveInventoryView(TerraDriveSavedData data, DriveStorageRecord record) {
        this.data = data;
        this.record = record;
        this.keys = new ArrayList<>(record.entries().keySet());
    }

    public DriveStorageRecord record() {
        return record;
    }

    @Override
    public int getSlots() {
        return keys.size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
        ItemStackKey key = keys.get(slot);
        long count = record.count(key);
        return count <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(count, Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        long accepted = data.insert(record, stack, stack.getCount(), simulate);
        if (accepted <= 0) return stack;
        return accepted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
        ItemStackKey key = keys.get(slot);
        int limit = Math.min(amount, key.toStack(1).getMaxStackSize());
        long removed = data.extract(record, key, limit, simulate);
        return removed <= 0 ? ItemStack.EMPTY : key.toStack((int) removed);
    }

    @Override
    public int getSlotLimit(int slot) {
        return (int) Math.min(record.capacity(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return data.check(record, stack) == TerraDriveSavedData.Rejection.NONE;
    }
}
