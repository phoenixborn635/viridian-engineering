package dev.viridianengineering.api.mana;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import velrondevs.botania.api.mana.ManaReceiver;

import java.util.function.LongSupplier;

/**
 * A device's mana store, exposed to Terrasteel pipes ({@link IManaPipeNode}) and Botania spreaders
 * ({@link ManaReceiver}). Consumers refuse extraction, generators refuse insertion; optional per-tick
 * limits cap I/O (Mana Battery). Every transfer reports exactly what moved, so pipes stay conserving.
 */
public class ManaBuffer implements IManaPipeNode {

    private final BlockEntity owner;
    private final LongSupplier capacity;
    private final boolean acceptsInput, allowsOutput;
    private long stored;
    private LongSupplier tickLimit = () -> Long.MAX_VALUE;
    private long tick = Long.MIN_VALUE, movedThisTick;

    public ManaBuffer(BlockEntity owner, LongSupplier capacity, boolean acceptsInput, boolean allowsOutput) {
        this.owner = owner;
        this.capacity = capacity;
        this.acceptsInput = acceptsInput;
        this.allowsOutput = allowsOutput;
    }

    /** Caps total pipe/spreader I/O per game tick. */
    public ManaBuffer withTickLimit(LongSupplier limit) {
        this.tickLimit = limit;
        return this;
    }

    @Override public long getStoredMana() { return stored; }

    @Override
    public Role role() {
        return acceptsInput == allowsOutput ? Role.STORE : allowsOutput ? Role.SOURCE : Role.SINK;
    }

    @Override public long getManaCapacity() { return Math.max(0, capacity.getAsLong()); }

    public long free() { return Math.max(0, getManaCapacity() - stored); }

    private long allowance() {
        Level level = owner.getLevel();
        long now = level == null ? 0 : level.getGameTime();
        if (now != tick) { tick = now; movedThisTick = 0; }
        return Math.max(0, tickLimit.getAsLong() - movedThisTick);
    }

    @Override
    public long insertMana(long amount, boolean simulate) {
        if (!acceptsInput || amount <= 0) return 0;
        long accepted = Math.min(Math.min(amount, free()), allowance());
        if (!simulate && accepted > 0) { stored += accepted; movedThisTick += accepted; owner.setChanged(); }
        return accepted;
    }

    @Override
    public long extractMana(long amount, boolean simulate) {
        if (!allowsOutput || amount <= 0) return 0;
        long removed = Math.min(Math.min(amount, stored), allowance());
        if (!simulate && removed > 0) { stored -= removed; movedThisTick += removed; owner.setChanged(); }
        return removed;
    }

    /** The device's own use or production (no I/O limit). */
    public boolean consume(long amount) {
        if (amount <= 0) return true;
        if (stored < amount) return false;
        stored -= amount;
        owner.setChanged();
        return true;
    }

    /** @return how much was added (the device's own production; no I/O limit). */
    public long produce(long amount) {
        long added = Math.min(Math.max(0, amount), free());
        if (added > 0) { stored += added; owner.setChanged(); }
        return added;
    }

    public void set(long value) {
        stored = Math.max(0, Math.min(value, getManaCapacity()));
        owner.setChanged();
    }

    /** Clamps after the capacity shrank (battery layers removed). */
    public void clamp() {
        if (stored > getManaCapacity()) set(getManaCapacity());
    }

    public void write(CompoundTag tag, String key) { tag.putLong(key, stored); }

    public void read(CompoundTag tag, String key) { stored = Math.max(0, tag.getLong(key)); }

    /** Botania spreader bursts (and Botania mana tools) talk to this view. */
    public ManaReceiver asReceiver() {
        return new ManaReceiver() {
            @Override public Level getManaReceiverLevel() { return owner.getLevel(); }
            @Override public BlockPos getManaReceiverPos() { return owner.getBlockPos(); }
            @Override public int getCurrentMana() { return (int) Math.min(Integer.MAX_VALUE, stored); }
            @Override public boolean isFull() { return !acceptsInput || free() <= 0; }
            @Override public void receiveMana(int mana) {
                if (mana > 0) insertMana(mana, false);
                else if (mana < 0) extractMana(-mana, false);
            }
            @Override public boolean canReceiveManaFromBursts() { return acceptsInput; }
        };
    }
}
