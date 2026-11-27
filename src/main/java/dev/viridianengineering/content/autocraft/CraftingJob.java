package dev.viridianengineering.content.autocraft;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.content.drive.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One autocrafting job. Everything a job owns is either in its {@link #escrow} (saved with the job)
 * or physically inside a machine in the world (saved with the chunk), so a restart neither loses nor
 * duplicates items (spec 0.2 / 3.7).
 */
public final class CraftingJob {

    public enum Status {
        /** Pulling the planned stored items from the network into escrow. */
        RESERVING,
        RUNNING,
        /** Waiting for items the network does not have (retries periodically). */
        MISSING,
        /** Target reached or cancelled: no new work, waiting for machines to hand back in-flight items. */
        FINISHING,
        /** Returning everything left in escrow to the network. */
        REFUNDING,
        DONE
    }

    public static final class StepState {
        public final CraftPattern pattern;
        /** Crafts not yet completed (including those in flight). */
        public long remaining;
        /** Crafts started in a machine and not yet collected. */
        public long inFlight;
        public long completed;
        /** Assembly lines: started units that came back as an unprocessed base item and wait in escrow to be re-fed. */
        public long parked;
        /** The machine port this step is using (claimed while it has work in flight). */
        public MachineEndpointKey endpoint;

        StepState(CraftPattern pattern, long remaining) {
            this.pattern = pattern;
            this.remaining = remaining;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put("Pattern", pattern.save(registries));
            tag.putLong("Remaining", remaining);
            tag.putLong("InFlight", inFlight);
            tag.putLong("Completed", completed);
            tag.putLong("Parked", parked);
            if (endpoint != null) MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE, endpoint).result().ifPresent(e -> tag.put("Endpoint", e));
            return tag;
        }

        static StepState load(CompoundTag tag, HolderLookup.Provider registries) {
            StepState s = new StepState(CraftPattern.load(tag.getCompound("Pattern"), registries), tag.getLong("Remaining"));
            s.inFlight = tag.getLong("InFlight");
            s.completed = tag.getLong("Completed");
            s.parked = tag.getLong("Parked");
            if (tag.contains("Endpoint"))
                s.endpoint = MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE, tag.get("Endpoint")).result().orElse(null);
            return s;
        }
    }

    public final UUID id;
    /** Not final: re-keyed when the terminal's ship is assembled or disassembled ({@code EndpointRekey}). */
    public MachineEndpointKey terminal;
    public final ItemStackKey target;
    public final long requested;
    public long delivered;
    /** Transient: in-flight count last seen while FINISHING (stall detection). */
    public transient long lastFinishingFlight = -1;
    public Status status = Status.RESERVING;
    public boolean cancelled;
    public boolean endpointLost;
    public BlockPos orphanedTerminalAt;
    public long retryAt;
    /** Bumped whenever the job moves forward; a long stall triggers a re-plan. */
    public long progress;
    public long lastProgress = -1;
    public long lastProgressAt;
    public final Map<ItemStackKey, Long> toReserve = new LinkedHashMap<>();
    public final Map<ItemStackKey, Long> escrow = new LinkedHashMap<>();
    /** Outputs handed back by machines (statistics, used by tests and the status line). */
    public final Map<ItemStackKey, Long> collected = new LinkedHashMap<>();
    public final List<StepState> steps = new ArrayList<>();
    public final Map<ItemStackKey, Long> missing = new LinkedHashMap<>();

    public CraftingJob(UUID id, MachineEndpointKey terminal, ItemStackKey target, long requested) {
        this.id = id;
        this.terminal = terminal;
        this.target = target;
        this.requested = requested;
    }

    /** Adds a plan's steps and reservations to this job. */
    public void apply(CraftingPlanner.Plan plan) {
        for (CraftingPlanner.Step s : plan.steps()) {
            StepState existing = null;
            for (StepState st : steps) if (st.pattern.id.equals(s.pattern().id)) existing = st;
            if (existing != null) existing.remaining += s.crafts();
            else steps.add(new StepState(s.pattern(), s.crafts()));
        }
        plan.reserve().forEach((k, v) -> toReserve.merge(k, v, Long::sum));
    }

    // ------------------------------------------------------------------ escrow

    public long escrowed(ItemStackKey key) {
        return escrow.getOrDefault(key, 0L);
    }

    public void escrowAdd(ItemStackKey key, long amount) {
        if (amount > 0) escrow.merge(key, amount, Long::sum);
    }

    public void escrowAdd(ItemStack stack) {
        if (!stack.isEmpty()) escrowAdd(ItemStackKey.of(stack), stack.getCount());
    }

    /** Removes up to {@code amount}; returns how many were removed. */
    public long escrowTake(ItemStackKey key, long amount) {
        long have = escrowed(key);
        long got = Math.min(have, amount);
        if (got <= 0) return 0;
        if (got == have) escrow.remove(key); else escrow.put(key, have - got);
        return got;
    }

    public boolean escrowHas(Map<ItemStackKey, Long> needs, long times) {
        for (Map.Entry<ItemStackKey, Long> e : needs.entrySet())
            if (escrowed(e.getKey()) < e.getValue() * times) return false;
        return true;
    }

    public long inFlight() {
        long n = 0;
        for (StepState s : steps) n += s.inFlight;
        return n;
    }

    public boolean active() {
        return status != Status.DONE;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE, terminal).result().ifPresent(e -> tag.put("Terminal", e));
        tag.put("Target", target.toStack(1).save(registries));
        tag.putLong("Requested", requested);
        tag.putLong("Delivered", delivered);
        tag.putString("Status", status.name());
        tag.putBoolean("Cancelled", cancelled);
        if (endpointLost) tag.putBoolean("EndpointLost", true);
        if (orphanedTerminalAt != null) tag.putLong("OrphanedTerminalAt", orphanedTerminalAt.asLong());
        tag.putLong("RetryAt", retryAt);
        tag.putLong("Progress", progress);
        tag.put("ToReserve", saveMap(toReserve, registries));
        tag.put("Escrow", saveMap(escrow, registries));
        tag.put("Collected", saveMap(collected, registries));
        tag.put("Missing", saveMap(missing, registries));
        ListTag stepList = new ListTag();
        for (StepState s : steps) stepList.add(s.save(registries));
        tag.put("Steps", stepList);
        return tag;
    }

    public static CraftingJob load(CompoundTag tag, HolderLookup.Provider registries) {
        MachineEndpointKey terminal = MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE, tag.get("Terminal")).result().orElseThrow();
        ItemStack target = ItemStack.parseOptional(registries, tag.getCompound("Target"));
        CraftingJob job = new CraftingJob(tag.getUUID("Id"), terminal, ItemStackKey.of(target), tag.getLong("Requested"));
        job.delivered = tag.getLong("Delivered");
        job.status = Status.valueOf(tag.getString("Status"));
        job.cancelled = tag.getBoolean("Cancelled");
        job.endpointLost = tag.getBoolean("EndpointLost");
        if (tag.contains("OrphanedTerminalAt")) job.orphanedTerminalAt = BlockPos.of(tag.getLong("OrphanedTerminalAt"));
        job.retryAt = tag.getLong("RetryAt");
        job.progress = tag.getLong("Progress");
        loadMap(tag.getList("ToReserve", Tag.TAG_COMPOUND), job.toReserve, registries);
        loadMap(tag.getList("Escrow", Tag.TAG_COMPOUND), job.escrow, registries);
        loadMap(tag.getList("Collected", Tag.TAG_COMPOUND), job.collected, registries);
        loadMap(tag.getList("Missing", Tag.TAG_COMPOUND), job.missing, registries);
        for (Tag t : tag.getList("Steps", Tag.TAG_COMPOUND)) job.steps.add(StepState.load((CompoundTag) t, registries));
        return job;
    }

    static ListTag saveMap(Map<ItemStackKey, Long> map, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        map.forEach((key, count) -> {
            CompoundTag e = new CompoundTag();
            e.put("Item", key.toStack(1).save(registries));
            e.putLong("Count", count);
            list.add(e);
        });
        return list;
    }

    static void loadMap(ListTag list, Map<ItemStackKey, Long> into, HolderLookup.Provider registries) {
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            ItemStack s = ItemStack.parseOptional(registries, e.getCompound("Item"));
            if (!s.isEmpty()) into.merge(ItemStackKey.of(s), e.getLong("Count"), Long::sum);
        }
    }
}
