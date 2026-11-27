package dev.viridianengineering.content.autocraft;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Spec 3.7 controller escrow: every autocrafting job and its escrow, saved with the world. */
public final class CraftingJobSavedData extends SavedData {

    public static final String ID = ViridianEngineering.MOD_ID + "_crafting_jobs";

    private final Map<UUID, CraftingJob> jobs = new LinkedHashMap<>();
    private static final Map<SubLevelContainer, Boolean> WATCHED = new WeakHashMap<>();

    /** Sable distinguishes a permanent removal from a ship parked in an unloaded chunk. */
    static void watchShips(ServerLevel level) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null || WATCHED.putIfAbsent(container, true) != null) return;
        container.addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelRemoved(SubLevel ship, SubLevelRemovalReason reason) {
                if (reason == SubLevelRemovalReason.REMOVED)
                    get(level.getServer()).shipRemoved(level, ship);
            }
        });
    }

    private void shipRemoved(ServerLevel level, SubLevel ship) {
        for (CraftingJob job : jobs.values()) {
            if (!job.active()) continue;
            if (onShip(level, ship, job.terminal)) {
                BlockPos local = ship.getPlot().getCenterBlock().offset(job.terminal.localPos());
                job.orphanedTerminalAt = BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(local)));
                job.endpointLost = true;
            } else if (job.steps.stream().anyMatch(step -> onShip(level, ship, step.endpoint))) {
                job.endpointLost = true;
            } else {
                continue;
            }
            setDirty();
        }
    }

    private static boolean onShip(ServerLevel level, SubLevel ship, MachineEndpointKey endpoint) {
        return endpoint != null && endpoint.dimension().equals(level.dimension())
                && ship.getUniqueId().equals(endpoint.sableSubLevelId());
    }

    public static CraftingJobSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CraftingJobSavedData::new, CraftingJobSavedData::load, null), ID);
    }

    public List<CraftingJob> jobs() {
        return new ArrayList<>(jobs.values());
    }

    public CraftingJob find(UUID id) {
        return jobs.get(id);
    }

    public boolean cancelAt(MachineEndpointKey terminal, UUID id) {
        CraftingJob job = jobs.get(id);
        if (job == null || !terminal.equals(job.terminal) || !job.active() || job.cancelled
                || job.status == CraftingJob.Status.REFUNDING) return false;
        CraftingScheduler.cancel(job);
        setDirty();
        return true;
    }

    public List<CraftingJob> jobsFor(MachineEndpointKey terminal) {
        List<CraftingJob> out = new ArrayList<>();
        for (CraftingJob j : jobs.values()) if (j.terminal.equals(terminal)) out.add(j);
        return out;
    }

    /** A machine changed key (its ship was assembled or disassembled): follow it. */
    public void rekey(MachineEndpointKey from, MachineEndpointKey to) {
        boolean changed = false;
        for (CraftingJob job : jobs.values()) {
            if (from.equals(job.terminal)) { job.terminal = to; changed = true; }
            for (CraftingJob.StepState st : job.steps)
                if (from.equals(st.endpoint)) { st.endpoint = to; changed = true; }
        }
        if (changed) setDirty();
    }

    public void add(CraftingJob job) {
        jobs.put(job.id, job);
        setDirty();
    }

    public void removeDone() {
        if (jobs.values().removeIf(j -> j.status == CraftingJob.Status.DONE)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CraftingJob job : jobs.values()) list.add(job.save(registries));
        tag.put("Jobs", list);
        return tag;
    }

    public static CraftingJobSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CraftingJobSavedData data = new CraftingJobSavedData();
        for (Tag t : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
            try {
                CraftingJob job = CraftingJob.load((CompoundTag) t, registries);
                data.jobs.put(job.id, job);
            } catch (RuntimeException e) {
                ViridianEngineering.LOGGER.error("Could not load an autocrafting job; its escrow is lost: {}", e.toString());
            }
        }
        return data;
    }
}
