package dev.viridianengineering.content.autocraft;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaTerminalBlock;
import dev.viridianengineering.content.corporea.TransactionContext;
import dev.viridianengineering.content.drive.ItemStackKey;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.viridianengineering.content.corporea.CorporeaNetworkManager.Access.AUTOMATED;

/**
 * Spec 3.7 job execution. Runs every server tick with fixed budgets, so no tick does unbounded
 * work. A job only advances while its terminal is loaded and powered (decision 2A: automation
 * pauses below the RPM threshold); everything it holds stays in its saved escrow meanwhile.
 * <p>
 * Life of a job: plan ({@link #request}) -> RESERVING (pull planned items from the network into
 * escrow) -> RUNNING (crafting-table steps happen in the terminal; machine steps are dispatched to
 * discovered endpoints; outputs are verified and collected back into escrow; finished target items
 * are delivered to the network) -> FINISHING (no new work; wait for machines to hand back what is
 * in flight) -> REFUNDING (everything left goes back to the network) -> DONE. If the rolled yield of
 * a sequenced recipe falls short, the job plans and reserves a top-up.
 */
public final class CraftingScheduler {

    public static final int CRAFTS_PER_TICK = 64;
    public static final int TRANSFERS_PER_TICK = 16;
    public static final int DEPLOYER_STOCK = 8;
    public static final int RETRY_TICKS = 100;

    private CraftingScheduler() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    // ------------------------------------------------------------------ requests

    public record RequestResult(CraftingJob job, Component error) {}

    /** Plans and starts a job for {@code amount} of {@code target} at the terminal at {@code origin}. */
    public static RequestResult request(ServerLevel level, BlockPos origin, ItemStackKey target, long amount) {
        MachineEndpointKey terminal = SableCompat.endpointKey(level, origin, Direction.DOWN);
        if (amount <= 0) return new RequestResult(null, Component.translatable("message.viridian_engineering.autocraft.amount"));
        if (!CorporeaNetworkManager.isPowered(level, origin))
            return new RequestResult(null, Component.translatable("message.viridian_engineering.autocraft.unpowered"));
        List<CraftPattern> patterns = PatternStore.get(level.getServer()).patterns(terminal);
        CraftingPlanner.Plan plan = CraftingPlanner.plan(target, amount, networkStock(level, origin), patterns);
        if (!plan.complete())
            return new RequestResult(null, Component.translatable("message.viridian_engineering.autocraft.missing", describe(plan.missing())));
        CraftingJob job = new CraftingJob(UUID.randomUUID(), terminal, target, amount);
        job.apply(plan);
        CraftingJobSavedData.get(level.getServer()).add(job);
        return new RequestResult(job, null);
    }

    /** Stops feeding new work; in-flight items finish and everything left is refunded. */
    public static void cancel(CraftingJob job) {
        if (job.cancelled || job.status == CraftingJob.Status.DONE) return;
        job.cancelled = true;
        job.toReserve.clear();
        // Unfinished assemblies parked in escrow are no longer in flight: they are refunded as they are.
        for (CraftingJob.StepState step : job.steps) {
            if (step.pattern.kind != CraftPattern.Kind.SEQUENCED) continue;
            for (var e : job.escrow.entrySet())
                if (isTransitional(e.getKey().toStack(1), step.pattern)) step.inFlight -= e.getValue();
            step.inFlight -= step.parked;
            step.parked = 0;
        }
        job.status = CraftingJob.Status.FINISHING;
    }

    /** Recover a station's dispatched batch before the old port's facing is lost. */
    public static void stationPortRemoved(ServerLevel level, BlockPos port, Direction facing) {
        BlockPos target = port.relative(facing);
        if (!level.isLoaded(target)) return;
        MachineEndpointKey key = SableCompat.endpointKey(level, port, Direction.DOWN);
        CraftingJobSavedData data = CraftingJobSavedData.get(level.getServer());
        for (CraftingJob job : data.jobs()) {
            if (!job.active()) continue;
            for (CraftingJob.StepState step : job.steps) {
                if (!key.equals(step.endpoint) || step.inFlight <= 0 || !CraftPattern.station(step.pattern.op)) continue;
                if (!CraftingStations.recover(level, target, job, step.pattern.op)) continue;
                step.endpoint = null;
                step.inFlight = 0;
                cancel(job);
                data.setDirty();
                return; // The station can only be claimed by one job.
            }
        }
    }

    public static String describe(Map<ItemStackKey, Long> items) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (var e : items.entrySet()) {
            if (n++ == 3) { sb.append(", ..."); break; }
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append("x ").append(e.getKey().toStack(1).getHoverName().getString());
        }
        return sb.toString();
    }

    public static Map<ItemStackKey, Long> networkStock(ServerLevel level, BlockPos origin) {
        Map<ItemStackKey, Long> stock = new HashMap<>();
        for (ItemStack s : CorporeaNetworkManager.browse(level, origin, AUTOMATED, new TransactionContext()))
            stock.merge(ItemStackKey.of(s), (long) s.getCount(), Long::sum);
        return stock;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) CraftingJobSavedData.watchShips(level);
        CraftingJobSavedData data = CraftingJobSavedData.get(server);
        List<CraftingJob> jobs = data.jobs();
        if (jobs.isEmpty()) return;
        Set<MachineEndpointKey> claimed = new HashSet<>();
        for (CraftingJob job : jobs)
            for (CraftingJob.StepState s : job.steps) if (s.endpoint != null) claimed.add(s.endpoint);
        for (CraftingJob job : jobs) {
            try {
                tickJob(server, job, claimed);
            } catch (RuntimeException e) {
                ViridianEngineering.LOGGER.error("Autocrafting job {} failed; cancelling it so its escrow is refunded", job.id, e);
                cancel(job);
            }
        }
        data.removeDone();
        data.setDirty();
    }

    static void tickJob(MinecraftServer server, CraftingJob job, Set<MachineEndpointKey> claimed) {
        ServerLevel level = server.getLevel(job.terminal.dimension());
        if (level == null) return;
        if (job.orphanedTerminalAt != null) {
            if (level.isLoaded(job.orphanedTerminalAt)) refundLostJob(level, job.orphanedTerminalAt, job, false);
            return;
        }
        BlockPos origin = SableCompat.resolve(level, job.terminal);
        if (origin == null || !level.isLoaded(origin)) return; // paused while unloaded; escrow is saved
        if (!(level.getBlockState(origin).getBlock() instanceof CorporeaTerminalBlock)) {
            job.orphanedTerminalAt = BlockPos.containing(SableCompat.toWorld(level, net.minecraft.world.phys.Vec3.atCenterOf(origin)));
            if (level.isLoaded(job.orphanedTerminalAt)) refundLostJob(level, job.orphanedTerminalAt, job, false);
            return;
        }
        boolean waitingForChunk = false;
        for (CraftingJob.StepState step : job.steps) {
            EndpointState state = endpointState(server, step);
            if (state == EndpointState.MISSING) job.endpointLost = true;
            if (state == EndpointState.UNLOADED) waitingForChunk = true;
        }
        if (job.endpointLost) {
            refundLostJob(level, origin, job, true);
            return;
        }
        if (waitingForChunk) {
            job.lastProgressAt = level.getGameTime();
            return;
        }
        if (!CorporeaNetworkManager.isPowered(level, origin)) return; // automation pauses below the threshold

        switch (job.status) {
            case RESERVING -> reserve(level, origin, job);
            case MISSING -> {
                if (level.getGameTime() >= job.retryAt) {
                    if (!job.toReserve.isEmpty()) job.status = CraftingJob.Status.RESERVING;
                    else replan(level, origin, job);
                }
            }
            case RUNNING, FINISHING -> run(level, origin, job, claimed);
            case REFUNDING -> refund(level, origin, job);
            case DONE -> {}
        }
    }

    private enum EndpointState { AVAILABLE, UNLOADED, MISSING }

    private static EndpointState endpointState(MinecraftServer server, CraftingJob.StepState step) {
        MachineEndpointKey endpoint = step.endpoint;
        if (endpoint == null) return EndpointState.AVAILABLE;
        ServerLevel level = server.getLevel(endpoint.dimension());
        if (level == null) return EndpointState.UNLOADED;
        BlockPos pos = SableCompat.resolve(level, endpoint);
        if (pos == null || !level.isLoaded(pos)) return EndpointState.UNLOADED;
        var port = level.getBlockState(pos);
        if (!port.is(VEBlocks.CORPOREA_MACHINE_PORT.get())) return EndpointState.MISSING;
        BlockPos target = pos.relative(port.getValue(CorporeaPortBlock.FACING));
        if (!level.isLoaded(target)) return EndpointState.UNLOADED;
        boolean present = switch (step.pattern.kind) {
            case PROCESSING -> CraftPattern.station(step.pattern.op) ? MachineDiscovery.stationAt(level, target) == step.pattern.op
                    : level.getBlockEntity(target) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity
                            && MachineDiscovery.machineAbove(level, target) == step.pattern.op;
            case SEQUENCED -> level.getBlockEntity(target) instanceof BeltBlockEntity;
            case CRAFTING -> true;
        };
        return present ? EndpointState.AVAILABLE : EndpointState.MISSING;
    }

    // ------------------------------------------------------------------ reserving

    private static void reserve(ServerLevel level, BlockPos origin, CraftingJob job) {
        int calls = TRANSFERS_PER_TICK;
        boolean shortfall = false;
        for (ItemStackKey key : new ArrayList<>(job.toReserve.keySet())) {
            long need = job.toReserve.get(key);
            ItemStack filter = key.toStack(1);
            while (need > 0 && calls-- > 0) {
                ItemStack got = CorporeaNetworkManager.withdraw(level, origin, filter, (int) Math.min(need, filter.getMaxStackSize()), AUTOMATED, new TransactionContext());
                if (got.isEmpty()) { shortfall = true; break; }
                job.escrowAdd(got);
                need -= got.getCount();
            }
            if (need <= 0) job.toReserve.remove(key); else job.toReserve.put(key, need);
            if (calls <= 0) break;
        }
        if (job.toReserve.isEmpty()) {
            job.missing.clear();
            job.status = CraftingJob.Status.RUNNING;
        } else if (shortfall) {
            job.missing.clear();
            job.missing.putAll(job.toReserve);
            job.status = CraftingJob.Status.MISSING;
            job.retryAt = level.getGameTime() + RETRY_TICKS;
        }
    }

    public static final int STALL_TICKS = 200;

    /**
     * Re-plans what the job still owes. Used when its planned crafts are done but rolled yields came
     * up short, and when it has stalled (for example, deployer parts ran out because scrap results were
     * re-fed). Work not yet started is dropped and planned afresh from the job's own escrow plus the
     * network; assemblies already in flight get the deployer parts they may still need. Only what the
     * escrow does not cover is withdrawn.
     */
    private static void replan(ServerLevel level, BlockPos origin, CraftingJob job) {
        long owed = job.requested - job.delivered - job.escrowed(job.target);
        if (owed <= 0 || job.cancelled) {
            job.status = CraftingJob.Status.FINISHING;
            return;
        }
        Map<ItemStackKey, Long> extra = new HashMap<>();
        long expectedFromFlight = 0;
        for (CraftingJob.StepState s : job.steps) {
            if (s.pattern.kind != CraftPattern.Kind.SEQUENCED || s.inFlight <= 0) continue;
            for (ItemStackKey part : partsPerUnit(s.pattern).keySet())
                extra.merge(part, partsPerUnit(s.pattern).get(part) * s.inFlight, Long::sum);
            if (s.pattern.outputKey().equals(job.target)) expectedFromFlight += (long) Math.floor(s.inFlight * s.pattern.yield());
        }
        Map<ItemStackKey, Long> stock = networkStock(level, origin);
        job.escrow.forEach((k, v) -> stock.merge(k, v, Long::sum)); // the job's own leftovers count first
        CraftingPlanner.Plan plan = CraftingPlanner.plan(job.target, Math.max(0, owed - expectedFromFlight), extra, stock,
                PatternStore.get(level.getServer()).patterns(job.terminal));
        if (!plan.complete()) {
            job.missing.clear();
            job.missing.putAll(plan.missing());
            job.status = CraftingJob.Status.MISSING;
            job.retryAt = level.getGameTime() + RETRY_TICKS;
            return;
        }
        for (CraftingJob.StepState s : job.steps) s.remaining = s.inFlight; // unstarted work is re-planned below
        Map<ItemStackKey, Long> reserve = new HashMap<>(plan.reserve());
        reserve.replaceAll((k, v) -> Math.max(0, v - job.escrowed(k)));
        reserve.values().removeIf(v -> v == 0);
        job.toReserve.clear();
        job.apply(new CraftingPlanner.Plan(plan.steps(), reserve, Map.of()));
        job.missing.clear();
        job.lastProgressAt = level.getGameTime();
        job.status = job.toReserve.isEmpty() ? CraftingJob.Status.RUNNING : CraftingJob.Status.RESERVING;
    }

    /** Deployer parts one assembly unit may still need (a full set of loops; leftovers are refunded). */
    private static Map<ItemStackKey, Long> partsPerUnit(CraftPattern p) {
        Map<ItemStackKey, Long> parts = new HashMap<>();
        for (ItemStack item : p.stepItems) if (!item.isEmpty()) parts.merge(ItemStackKey.of(item), (long) p.loops, Long::sum);
        return parts;
    }

    // ------------------------------------------------------------------ running

    private static void run(ServerLevel level, BlockPos origin, CraftingJob job, Set<MachineEndpointKey> claimed) {
        boolean feeding = job.status == CraftingJob.Status.RUNNING && !job.cancelled;
        List<MachineDiscovery.Endpoint> endpoints = MachineDiscovery.endpoints(level, origin);
        for (CraftingJob.StepState step : job.steps) {
            switch (step.pattern.kind) {
                case CRAFTING -> { if (feeding) craft(level, job, step); }
                case PROCESSING -> {
                    if (CraftPattern.station(step.pattern.op)) stationStep(level, job, step, endpoints, claimed, feeding);
                    else depotStep(level, job, step, endpoints, claimed, feeding);
                }
                case SEQUENCED -> lineStep(level, job, step, endpoints, claimed, feeding);
            }
        }
        deliver(level, origin, job);
        depositUnneeded(level, origin, job);

        if (job.status == CraftingJob.Status.RUNNING) {
            long now = level.getGameTime();
            if (job.progress != job.lastProgress) {
                job.lastProgress = job.progress;
                job.lastProgressAt = now;
            }
            if (job.delivered >= job.requested) {
                job.status = CraftingJob.Status.FINISHING;
            } else if (job.inFlight() == 0 && job.steps.stream().allMatch(s -> s.remaining <= 0)) {
                replan(level, origin, job);
            } else if (now - job.lastProgressAt > STALL_TICKS) {
                replan(level, origin, job);
            }
        }
        if (job.status == CraftingJob.Status.FINISHING && job.inFlight() > 0) {
            // Parts can run out after the target is met or the job is cancelled. Recover the
            // actual items from the reserved machines before releasing their claims.
            long now = level.getGameTime(), flight = job.inFlight();
            if (job.progress != job.lastProgress || flight != job.lastFinishingFlight) {
                job.lastProgress = job.progress;
                job.lastFinishingFlight = flight;
                job.lastProgressAt = now;
            } else if (now - job.lastProgressAt > STALL_TICKS) {
                recoverStalledMachines(level, job);
                deliver(level, origin, job);
            }
        }
        if (job.status == CraftingJob.Status.FINISHING && job.inFlight() <= 0
                && job.steps.stream().allMatch(s -> s.endpoint == null))
            job.status = CraftingJob.Status.REFUNDING;
    }

    private static void recoverStalledMachines(ServerLevel level, CraftingJob job) {
        for (CraftingJob.StepState step : job.steps) {
            if (step.endpoint == null) continue;
            BlockPos port = SableCompat.resolve(level, step.endpoint);
            if (port == null || !level.isLoaded(port)) continue;
            var state = level.getBlockState(port);
            if (!state.is(VEBlocks.CORPOREA_MACHINE_PORT.get())) continue;
            BlockPos target = port.relative(state.getValue(CorporeaPortBlock.FACING));
            if (CraftPattern.station(step.pattern.op)) {
                if (!CraftingStations.recover(level, target, job, step.pattern.op)) continue;
            } else if (step.pattern.kind == CraftPattern.Kind.SEQUENCED) {
                var belt = BeltHelper.getControllerBE(level, target);
                if (belt == null) continue;
                ItemStackKey base = ItemStackKey.of(step.pattern.input);
                belt.getInventory().applyToEachWithin(belt.beltLength / 2f, belt.beltLength + 1f, transported -> {
                    ItemStack stack = transported.stack;
                    if (base.matches(stack) || isTransitional(stack, step.pattern)) job.escrowAdd(stack);
                    else collectLineOutput(job, step, stack);
                    return TransportedResult.removeItem();
                });
                for (int segment = 0; segment < belt.beltLength; segment++) {
                    BlockPos pos = BeltHelper.getPositionForOffset(belt, segment);
                    if (level.isLoaded(pos) && MachineDiscovery.machineAbove(level, pos) == CraftPattern.Op.DEPLOY)
                        drainDeployer(level, job, pos.above(2));
                }
            } else if (step.pattern.kind == CraftPattern.Kind.PROCESSING) {
                IItemHandler depot = handler(level, target, Direction.UP);
                if (depot == null) continue;
                for (int slot = 0; slot < depot.getSlots(); slot++)
                    job.escrowAdd(depot.extractItem(slot, depot.getStackInSlot(slot).getCount(), false));
                if (step.pattern.op == CraftPattern.Op.DEPLOY) drainDeployer(level, job, target.above(2));
            }
            step.endpoint = null;
            step.inFlight = 0;
            step.parked = 0;
        }
    }

    private static void stationStep(ServerLevel level, CraftingJob job, CraftingJob.StepState step,
                                    List<MachineDiscovery.Endpoint> endpoints, Set<MachineEndpointKey> claimed, boolean feeding) {
        if (step.endpoint != null && step.inFlight > 0) {
            BlockPos port = SableCompat.resolve(level, step.endpoint);
            if (port == null || !level.isLoaded(port)) return;
            BlockPos target = port.relative(level.getBlockState(port).getValue(CorporeaPortBlock.FACING));
            if (CraftingStations.collect(level, target, job, step)) finishStationCraft(job, step);
            return;
        }
        if (step.endpoint != null) step.endpoint = null;
        if (!feeding || step.remaining <= 0 || !job.escrowHas(step.pattern.inputsPerCraft(), 1)) return;
        for (var candidate : endpoints) {
            if (!(candidate instanceof MachineDiscovery.Station station) || station.op() != step.pattern.op
                    || claimed.contains(station.key()) || isClaimedMachine(level, station.target())) continue;
            step.endpoint = station.key();
            if (CraftingStations.feed(level, station.target(), job, step)) {
                step.inFlight = 1;
                claimed.add(station.key());
                job.progress++;
                return;
            }
            step.endpoint = null;
        }
    }

    private static void finishStationCraft(CraftingJob job, CraftingJob.StepState step) {
        step.inFlight = 0;
        step.completed++;
        step.remaining--;
        job.progress++;
    }

    public static boolean collectCrafterOutputs(ServerLevel level, BlockPos target, List<ItemStack> stacks) {
        return withClaimedMachine(level, target, (job, step) -> {
            if (step.pattern.op != CraftPattern.Op.MECHANICAL_CRAFT || step.inFlight == 0) return false;
            stacks.forEach(stack -> CraftingStations.collectResult(job, stack));
            finishStationCraft(job, step);
            CraftingJobSavedData.get(level.getServer()).setDirty();
            return true;
        });
    }

    public static net.minecraft.resources.ResourceLocation claimedRecipe(ServerLevel level, BlockPos target, CraftPattern.Op op) {
        var result = new java.util.concurrent.atomic.AtomicReference<net.minecraft.resources.ResourceLocation>();
        withClaimedMachine(level, target, (job, step) -> {
            if (step.pattern.op != op) return false;
            result.set(step.pattern.recipeId);
            return true;
        });
        return result.get();
    }

    /** Crafting-table steps: done by the terminal itself, up to {@link #CRAFTS_PER_TICK} per tick. */
    private static void craft(ServerLevel level, CraftingJob job, CraftingJob.StepState step) {
        if (step.remaining <= 0) return;
        Map<ItemStackKey, Long> inputs = step.pattern.inputsPerCraft();
        if (!job.escrowHas(inputs, 1)) return;
        RecipeHolder<?> holder = level.getRecipeManager().byKey(step.pattern.recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)) {
            ViridianEngineering.LOGGER.warn("Autocrafting recipe {} no longer exists; cancelling job {}", step.pattern.recipeId, job.id);
            cancel(job);
            return;
        }
        List<ItemStack> grid = new ArrayList<>(9);
        for (ItemStack s : step.pattern.grid) grid.add(s.copy());
        CraftingInput in = CraftingInput.of(3, 3, grid);
        if (!recipe.matches(in, level)) {
            cancel(job);
            return;
        }
        for (int i = 0; i < CRAFTS_PER_TICK && step.remaining > 0 && job.escrowHas(inputs, 1); i++) {
            inputs.forEach(job::escrowTake);
            job.escrowAdd(recipe.assemble(in, level.registryAccess()));
            for (ItemStack rest : recipe.getRemainingItems(in)) job.escrowAdd(rest);
            step.remaining--;
            step.completed++;
            job.progress++;
        }
    }

    // ------------------------------------------------------------------ depot machines

    private static void depotStep(ServerLevel level, CraftingJob job, CraftingJob.StepState step,
                                  List<MachineDiscovery.Endpoint> endpoints, Set<MachineEndpointKey> claimed, boolean feeding) {
        CraftPattern p = step.pattern;
        MachineDiscovery.DepotMachine machine = null;
        if (step.endpoint != null) {
            BlockPos port = SableCompat.resolve(level, step.endpoint);
            if (port == null || !level.isLoaded(port)) return; // its chunk is unloaded: wait
            for (var e : endpoints)
                if (e instanceof MachineDiscovery.DepotMachine d && d.key().equals(step.endpoint) && d.op() == p.op) machine = d;
            if (machine == null) return; // the route may be temporarily disconnected
        } else {
            if (!feeding || step.remaining <= 0 || job.escrowed(ItemStackKey.of(p.input)) <= 0) return;
            for (var e : endpoints)
                if (e instanceof MachineDiscovery.DepotMachine d && d.op() == p.op && !claimed.contains(d.key())
                        && isEmpty(handler(level, d.depot(), Direction.UP))) { machine = d; break; }
            if (machine == null) return;
            step.endpoint = machine.key();
            claimed.add(machine.key());
        }
        IItemHandler depot = handler(level, machine.depot(), Direction.UP);
        if (depot == null) return;
        ItemStackKey inputKey = ItemStackKey.of(p.input);

        // Collect: anything on the depot that is not the input is a verified result.
        long present = 0;
        for (int slot = 0; slot < depot.getSlots(); slot++) {
            ItemStack s = depot.getStackInSlot(slot);
            if (s.isEmpty()) continue;
            if (slot == 0 && inputKey.matches(s)) { present = s.getCount(); continue; }
            ItemStack got = depot.extractItem(slot, s.getCount(), false);
            job.escrowAdd(got);
            if (!got.isEmpty()) job.collected.merge(ItemStackKey.of(got), (long) got.getCount(), Long::sum);
        }
        long processed = step.inFlight - present;
        if (processed > 0) {
            step.inFlight -= processed;
            step.remaining -= processed;
            step.completed += processed;
            job.progress += processed;
        }

        if (p.op == CraftPattern.Op.DEPLOY) {
            if (step.inFlight > 0 || (feeding && step.remaining > 0)) stockDeployer(level, job, machine.machine(), p.held, DEPLOYER_STOCK);
        }

        // Feed a new batch onto an empty depot.
        if (feeding && step.inFlight == 0 && step.remaining > 0 && isEmpty(depot)) {
            long n = Math.min(Math.min(step.remaining, p.input.getMaxStackSize()), job.escrowed(inputKey));
            if (p.op == CraftPattern.Op.DEPLOY && !hasItem(level, machine.machine(), p.held)) n = 0;
            if (p.op == CraftPattern.Op.FILL) {
                var recipe = level.getRecipeManager().byKey(p.recipeId).orElse(null);
                n = recipe != null && recipe.value() instanceof FillingRecipe filling
                        && CraftingStations.hasFluids(level, machine.machine(), filling.getFluidIngredients()) ? Math.min(n, 1) : 0;
            }
            if (n > 0) {
                ItemStack batch = inputKey.toStack((int) n);
                ItemStack rest = depot.insertItem(0, batch, false);
                long inserted = n - rest.getCount();
                job.escrowTake(inputKey, inserted);
                step.inFlight += inserted;
                job.progress += inserted;
            }
        }
        if (step.inFlight == 0 && (step.remaining <= 0 || !feeding)) {
            if (p.op == CraftPattern.Op.DEPLOY) drainDeployer(level, job, machine.machine());
            step.endpoint = null;
        }
    }

    // ------------------------------------------------------------------ assembly lines

    private static void lineStep(ServerLevel level, CraftingJob job, CraftingJob.StepState step,
                                 List<MachineDiscovery.Endpoint> endpoints, Set<MachineEndpointKey> claimed, boolean feeding) {
        CraftPattern p = step.pattern;
        MachineDiscovery.AssemblyLine line = null;
        if (step.endpoint != null) {
            BlockPos port = SableCompat.resolve(level, step.endpoint);
            if (port == null || !level.isLoaded(port)) return;
            for (var e : endpoints)
                if (e instanceof MachineDiscovery.AssemblyLine l && l.key().equals(step.endpoint) && l.ops().equals(p.steps)) line = l;
            if (line == null) {
                // The line is gone or stopped. Unfinished items still in escrow are refundable; the rest stays in the world.
                if (!hasBelt(level, port)) {
                    step.inFlight = 0;
                    step.parked = 0;
                    step.endpoint = null;
                }
                return;
            }
        } else {
            if (!feeding || step.remaining <= 0 || job.escrowed(ItemStackKey.of(p.input)) <= 0) return;
            for (var e : endpoints)
                if (e instanceof MachineDiscovery.AssemblyLine l && l.ops().equals(p.steps) && !claimed.contains(l.key())) { line = l; break; }
            if (line == null) return;
            step.endpoint = line.key();
            claimed.add(line.key());
        }
        IItemHandler in = handler(level, line.input(), Direction.UP);
        IItemHandler out = handler(level, line.output(), Direction.UP);
        if (in == null || out == null) return;
        ItemStackKey baseKey = ItemStackKey.of(p.input);

        // Keep each deployer stocked while anything is on the line, even after a cancel: emptying a
        // deployer's hand mid-deploy leaves it holding the belt item forever. Drain only once the line is clear.
        boolean working = step.inFlight > 0 || (feeding && step.remaining > 0);
        for (int i = 0; i < line.ops().size(); i++)
            if (line.ops().get(i) == CraftPattern.Op.DEPLOY) {
                if (working) stockDeployer(level, job, line.machines().get(i), p.stepItems.get(i), DEPLOYER_STOCK);
                else drainDeployer(level, job, line.machines().get(i));
            }

        // Collect what reached the end of the belt.
        ItemStack arrived = out.getStackInSlot(0);
        if (!arrived.isEmpty()) {
            collectLineOutput(job, step, out.extractItem(0, arrived.getCount(), false));
        }

        // Feed at most one item per tick: unfinished assemblies first, then parked bases, then new work.
        if (!job.cancelled && isEmpty(in)) {
            ItemStackKey next = null;
            boolean fresh = false, unpark = false;
            for (ItemStackKey k : job.escrow.keySet())
                if (isTransitional(k.toStack(1), p)) { next = k; break; }
            if (next == null && step.parked > 0 && job.escrowed(baseKey) > 0) { next = baseKey; unpark = true; }
            if (next == null && feeding && step.remaining - step.inFlight > 0 && step.inFlight < line.length()
                    && job.escrowed(baseKey) > 0) { next = baseKey; fresh = true; }
            if (next != null && lineFluidsReady(level, p, line) && in.insertItem(0, next.toStack(1), false).isEmpty()) {
                job.escrowTake(next, 1);
                if (fresh) { step.inFlight++; job.progress++; }
                if (unpark) step.parked--;
            }
        }
        if (step.inFlight <= 0 && (step.remaining <= 0 || !feeding || job.cancelled)) {
            step.inFlight = 0;
            step.parked = 0;
            for (int i = 0; i < line.ops().size(); i++)
                if (line.ops().get(i) == CraftPattern.Op.DEPLOY) drainDeployer(level, job, line.machines().get(i));
            step.endpoint = null;
        }
    }

    private static boolean lineFluidsReady(ServerLevel level, CraftPattern pattern, MachineDiscovery.AssemblyLine line) {
        if (!pattern.steps.contains(CraftPattern.Op.FILL)) return true;
        var holder = level.getRecipeManager().byKey(pattern.recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe seq))
            return false;
        for (int i = 0; i < seq.getSequence().size(); i++) {
            if (seq.getSequence().get(i).getRecipe() instanceof FillingRecipe filling
                    && !CraftingStations.hasFluids(level, line.machines().get(i), filling.getFluidIngredients())) return false;
        }
        return true;
    }

    private static void collectLineOutput(CraftingJob job, CraftingJob.StepState step, ItemStack stack) {
        if (stack.isEmpty()) return;
        job.escrowAdd(stack);
        int count = stack.getCount();
        boolean base = ItemStackKey.of(step.pattern.input).matches(stack);
        if (base || isTransitional(stack, step.pattern)) {
            if (job.cancelled) step.inFlight -= count;
            else if (base) step.parked += count;
            return;
        }
        job.collected.merge(ItemStackKey.of(stack), (long) count, Long::sum);
        step.inFlight -= count;
        step.remaining -= count;
        step.completed += count;
        job.progress++;
    }

    /**
     * A fast belt can advance past the last readable segment and eject in the same tick.
     * Take the actual ejected stack from Create while the line is claimed by a job.
     */
    public static boolean collectBeltEjection(ServerLevel level, BeltBlockEntity belt, ItemStack stack) {
        return withClaimedMachine(level, belt.getBlockPos(), (job, step) -> {
            if (step.pattern.kind != CraftPattern.Kind.SEQUENCED || step.inFlight <= 0) return false;
            collectLineOutput(job, step, stack);
            CraftingJobSavedData.get(level.getServer()).setDirty();
            return true;
        });
    }

    public static boolean isClaimedMachine(ServerLevel level, BlockPos target) {
        return MachineDiscovery.isMachineTarget(level, target)
                && withClaimedMachine(level, target, (job, step) -> true);
    }

    private static boolean withClaimedMachine(ServerLevel level, BlockPos target,
                                               java.util.function.BiPredicate<CraftingJob, CraftingJob.StepState> action) {
        BlockPos identity = machineIdentity(level, target);
        for (CraftingJob job : CraftingJobSavedData.get(level.getServer()).jobs()) {
            if (!job.active() || job.endpointLost) continue;
            for (CraftingJob.StepState step : job.steps) {
                if (step.endpoint == null) continue;
                BlockPos port = SableCompat.resolve(level, step.endpoint);
                if (port == null || !level.isLoaded(port)) continue;
                var state = level.getBlockState(port);
                if (!state.is(VEBlocks.CORPOREA_MACHINE_PORT.get())) continue;
                BlockPos attached = port.relative(state.getValue(CorporeaPortBlock.FACING));
                if (level.isLoaded(attached) && identity.equals(machineIdentity(level, attached)) && action.test(job, step))
                    return true;
            }
        }
        return false;
    }

    private static BlockPos machineIdentity(ServerLevel level, BlockPos target) {
        if (level.getBlockEntity(target) instanceof BeltBlockEntity belt && belt.getController() != null)
            return belt.getController();
        if (level.getBlockEntity(target) instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity crafter) {
            for (int i = 0; i < 256; i++) {
                var next = com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.getTargetingCrafter(crafter);
                if (next == null) return crafter.getBlockPos();
                crafter = next;
            }
        }
        return target;
    }

    static boolean isTransitional(ItemStack stack, CraftPattern p) {
        if (stack.isEmpty() || p.transitional.isEmpty() || stack.getItem() != p.transitional.getItem()) return false;
        var sa = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return sa != null && sa.id().equals(p.recipeId);
    }

    private static boolean hasBelt(ServerLevel level, BlockPos port) {
        var state = level.getBlockState(port);
        if (!(state.getBlock() instanceof dev.viridianengineering.content.corporea.CorporeaPortBlock)) return false;
        BlockPos target = port.relative(state.getValue(dev.viridianengineering.content.corporea.CorporeaPortBlock.FACING));
        return level.getBlockEntity(target) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
    }

    // ------------------------------------------------------------------ deployers

    private static void stockDeployer(ServerLevel level, CraftingJob job, BlockPos deployer, ItemStack item, int target) {
        if (item.isEmpty()) return;
        IItemHandler h = handler(level, deployer, null);
        if (h == null) return;
        int heldSlot = h.getSlots() - 1;
        ItemStack held = h.getStackInSlot(heldSlot);
        ItemStackKey key = ItemStackKey.of(item);
        if (!held.isEmpty() && !key.matches(held)) return; // someone else's item: leave it alone
        int want = target - held.getCount();
        long n = Math.min(want, job.escrowed(key));
        if (n <= 0) return;
        ItemStack rest = h.insertItem(heldSlot, key.toStack((int) n), false);
        long moved = n - rest.getCount();
        job.escrowTake(key, moved);
        if (moved > 0) job.progress++;
    }

    private static boolean hasItem(ServerLevel level, BlockPos deployer, ItemStack item) {
        IItemHandler h = handler(level, deployer, null);
        return h != null && ItemStackKey.of(item).matches(h.getStackInSlot(h.getSlots() - 1));
    }

    /** Takes everything a deployer holds (and its overflow) back into escrow. */
    private static void drainDeployer(ServerLevel level, CraftingJob job, BlockPos deployer) {
        IItemHandler h = handler(level, deployer, null);
        if (h == null) return;
        for (int slot = h.getSlots() - 1; slot >= 0; slot--) {
            ItemStack s = h.getStackInSlot(slot);
            if (s.isEmpty()) continue;
            job.escrowAdd(h.extractItem(slot, s.getCount(), false));
        }
    }

    // ------------------------------------------------------------------ delivery and refunds

    /** Finished target items go to the network (into the Archive Pylons / inventories) as they arrive. */
    private static void deliver(ServerLevel level, BlockPos origin, CraftingJob job) {
        int calls = TRANSFERS_PER_TICK;
        while (calls-- > 0) {
            long have = job.escrowed(job.target);
            if (have <= 0) return;
            ItemStack stack = job.target.toStack((int) Math.min(have, job.target.toStack(1).getMaxStackSize()));
            ItemStack rest = CorporeaNetworkManager.deposit(level, origin, stack, AUTOMATED, new TransactionContext());
            long moved = stack.getCount() - rest.getCount();
            if (moved <= 0) return;
            job.escrowTake(job.target, moved);
            job.delivered += moved;
        }
    }

    /** Byproducts and leftovers no remaining step needs are returned to the network straight away. */
    private static void depositUnneeded(ServerLevel level, BlockPos origin, CraftingJob job) {
        Set<ItemStackKey> needed = new HashSet<>();
        for (CraftingJob.StepState s : job.steps) {
            if (s.remaining <= 0 && s.inFlight <= 0) continue;
            needed.addAll(s.pattern.inputsPerCraft().keySet());
            if (s.pattern.kind == CraftPattern.Kind.SEQUENCED)
                for (ItemStackKey k : job.escrow.keySet()) if (isTransitional(k.toStack(1), s.pattern) && !job.cancelled) needed.add(k);
        }
        needed.add(job.target);
        int calls = TRANSFERS_PER_TICK / 2;
        for (ItemStackKey key : new ArrayList<>(job.escrow.keySet())) {
            if (needed.contains(key) || calls-- <= 0) continue;
            depositKey(level, origin, job, key);
        }
    }

    private static void refund(ServerLevel level, BlockPos origin, CraftingJob job) {
        int calls = TRANSFERS_PER_TICK;
        for (ItemStackKey key : new ArrayList<>(job.escrow.keySet())) {
            if (calls-- <= 0) return;
            depositKey(level, origin, job, key);
        }
        if (job.escrow.isEmpty()) job.status = CraftingJob.Status.DONE;
    }

    private static void depositKey(ServerLevel level, BlockPos origin, CraftingJob job, ItemStackKey key) {
        long have = job.escrowed(key);
        int max = key.toStack(1).getMaxStackSize();
        ItemStack stack = key.toStack((int) Math.min(have, max));
        ItemStack rest = CorporeaNetworkManager.deposit(level, origin, stack, AUTOMATED, new TransactionContext());
        job.escrowTake(key, stack.getCount() - rest.getCount());
    }

    /** Return escrow in bounded batches. A full or missing network spills at the controller. */
    private static void refundLostJob(ServerLevel level, BlockPos at, CraftingJob job, boolean hasTerminal) {
        job.cancelled = true;
        job.endpointLost = true;
        job.toReserve.clear();
        job.steps.forEach(step -> {
            step.endpoint = null;
            step.inFlight = 0;
            step.parked = 0;
        });
        job.status = CraftingJob.Status.REFUNDING;
        CorporeaNetworkManager.Access access = hasTerminal && CorporeaNetworkManager.isPowered(level, at)
                ? AUTOMATED : CorporeaNetworkManager.Access.MANUAL_WIRED;
        var drop = SableCompat.toWorld(level, net.minecraft.world.phys.Vec3.atCenterOf(at));
        int calls = TRANSFERS_PER_TICK;
        for (var e : new ArrayList<>(job.escrow.entrySet())) {
            ItemStackKey key = e.getKey();
            int max = key.toStack(1).getMaxStackSize();
            while (job.escrowed(key) > 0 && calls-- > 0) {
                int count = (int) Math.min(job.escrowed(key), max);
                ItemStack stack = key.toStack(count);
                ItemStack rest = hasTerminal ? CorporeaNetworkManager.deposit(level, at, stack, access, new TransactionContext()) : stack;
                if (!rest.isEmpty()) Containers.dropItemStack(level, drop.x, drop.y, drop.z, rest);
                job.escrowTake(key, count);
            }
            if (calls <= 0) break;
        }
        if (job.escrow.isEmpty()) job.status = CraftingJob.Status.DONE;
    }

    // ------------------------------------------------------------------ helpers

    static IItemHandler handler(ServerLevel level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }

    private static boolean isEmpty(IItemHandler h) {
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) return false;
        return true;
    }
}
