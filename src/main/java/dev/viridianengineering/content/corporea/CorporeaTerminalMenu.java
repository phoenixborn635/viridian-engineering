package dev.viridianengineering.content.corporea;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.network.VENetwork;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VECorporea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.viridianengineering.content.autocraft.CraftingJob;
import dev.viridianengineering.content.autocraft.CraftingJobSavedData;
import dev.viridianengineering.content.autocraft.CraftPattern;

/**
 * Corporea Crafting Terminal (wired) and Wireless Terminal UI.
 * <ul>
 *   <li>The network view is built on the server: aggregated, searched and sorted there, and sent as
 *       64-entry pages ({@link VENetwork.TerminalPage}). Clients only send intents
 *       ({@link VENetwork.TerminalAction}), which the server re-validates.</li>
 *   <li>Wired access uses {@link CorporeaNetworkManager.Access#MANUAL_WIRED}, so it keeps working
 *       unpowered (decision 2A). Wireless access uses {@code AUTOMATED}, so it pauses below 32 RPM.</li>
 *   <li>3x3 crafting grid: after each craft, emptied grid slots refill from the network, so
 *       shift-clicking the result crafts repeatedly from stored items.</li>
 * </ul>
 * Slot layout: 0 = result, 1-9 = grid, 10-36 = player inventory, 37-45 = hotbar.
 */
public class CorporeaTerminalMenu extends AbstractContainerMenu {

    public static final int RESULT = 0, GRID_START = 1, GRID_END = 10, INV_START = 10, INV_END = 46;
    private static final int MAX_BULK_CRAFTS = 64;
    private static final int REFRESH_INTERVAL = 10;

    private final Player owner;
    private final MachineEndpointKey endpoint;
    private final boolean wireless;
    private final CraftingContainer crafting = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();

    // server-side view state
    private String search = "";
    private TerminalView.Sort sort = TerminalView.Sort.NAME;
    private int page;
    private List<TerminalView.Entry> lastPage = List.of();
    private int lastPages = -1, lastTotal = -1, lastFlags = -1, lastSentPage = -1;
    private int refreshTicks = REFRESH_INTERVAL; // first broadcast sends the opening page

    // autocrafting (server side)
    private boolean patternMode;
    private List<dev.viridianengineering.content.autocraft.CraftPattern> lastPatternPage = List.of();
    private String lastStatus;
    private boolean showJobs;
    private int firstJob;
    private VENetwork.TerminalJobs lastJobs;

    // client-side copy of the last page received
    private VENetwork.TerminalPage clientPage;
    private String clientStatus = "";
    private VENetwork.TerminalJobs clientJobs;

    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, boolean wireless) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(wireless);
    }

    public CorporeaTerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readBoolean());
    }

    public CorporeaTerminalMenu(int id, Inventory inventory, BlockPos terminalPos, boolean wireless) {
        super(VECorporea.TERMINAL_MENU.get(), id);
        this.owner = inventory.player;
        this.wireless = wireless;
        this.endpoint = SableCompat.endpointKey(owner.level(), terminalPos, Direction.DOWN);

        addSlot(new RefillingResultSlot(owner, crafting, result, 0, 230, 58));
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                addSlot(new Slot(crafting, x + y * 3, 166 + x * 18, 40 + y * 18));
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 9; x++)
                addSlot(new Slot(inventory, 9 + x + y * 9, 48 + x * 18, 184 + y * 18));
        for (int x = 0; x < 9; x++)
            addSlot(new Slot(inventory, x, 48 + x * 18, 242));
    }

    public boolean isWireless() { return wireless; }

    @org.jetbrains.annotations.Nullable
    public VENetwork.TerminalPage clientPage() { return clientPage; }

    public void acceptPage(VENetwork.TerminalPage page) { this.clientPage = page; }

    public void acceptStatus(String status) { this.clientStatus = status; }

    public String clientStatus() { return clientStatus; }
    public VENetwork.TerminalJobs clientJobs() { return clientJobs; }
    public void acceptJobs(VENetwork.TerminalJobs jobs) { clientJobs = jobs; }

    public MachineEndpointKey endpoint() { return endpoint; }

    private CorporeaNetworkManager.Access access() {
        return wireless ? CorporeaNetworkManager.Access.AUTOMATED : CorporeaNetworkManager.Access.MANUAL_WIRED;
    }

    @org.jetbrains.annotations.Nullable
    private BlockPos terminalPos() { return SableCompat.resolve(owner.level(), endpoint); }

    @Override
    public boolean stillValid(Player player) {
        BlockPos pos = terminalPos();
        if (pos == null || !player.level().hasChunkAt(pos)
                || !player.level().getBlockState(pos).is(VEBlocks.CORPOREA_CRAFTING_TERMINAL.get()))
            return false;
        if (wireless) return CorporeaWirelessTerminalItem.isHoldingBoundTo(player, endpoint);
        var terminalWorld = SableCompat.toWorld(player.level(), net.minecraft.world.phys.Vec3.atCenterOf(pos));
        return SableCompat.toWorld(player.level(), player.position()).distanceToSqr(terminalWorld) <= 64;
    }

    // ------------------------------------------------------------------ server view

    private List<TerminalView.Entry> buildView(ServerLevel level, BlockPos pos) {
        List<ItemStack> raw = CorporeaNetworkManager.browse(level, pos, access(), new TransactionContext());
        return TerminalView.filterAndSort(TerminalView.aggregate(raw), search, sort);
    }

    /** Rebuilds the view and sends the current page if anything visible changed (or {@code force}). */
    public void refresh(boolean force) {
        if (!(owner instanceof ServerPlayer player) || !(owner.level() instanceof ServerLevel level)) return;
        BlockPos pos = terminalPos();
        if (pos == null || !stillValid(owner)) return;
        boolean powered = CorporeaNetworkManager.isPowered(level, pos);
        sendStatus(player, level);
        if (showJobs) sendJobs(player, level);
        List<TerminalView.Entry> view;
        List<dev.viridianengineering.content.autocraft.CraftPattern> patterns = List.of();
        if (patternMode) {
            patterns = patternView(level);
            List<TerminalView.Entry> entries = new ArrayList<>();
            for (var p : patterns) entries.add(new TerminalView.Entry(p.output.copy(), p.output.getCount()));
            view = entries;
        } else {
            view = wireless && !powered ? List.of() : buildView(level, pos);
        }
        int pages = TerminalView.pageCount(view.size());
        page = Math.clamp(page, 0, pages - 1);
        List<TerminalView.Entry> slice = TerminalView.page(view, page);
        if (patternMode) {
            int from = Math.min(page * TerminalView.PAGE_SIZE, patterns.size());
            lastPatternPage = List.copyOf(patterns.subList(from, Math.min(from + TerminalView.PAGE_SIZE, patterns.size())));
        }
        int flags = (powered ? VENetwork.TerminalPage.POWERED : 0) | (wireless ? VENetwork.TerminalPage.WIRELESS : 0)
                | (patternMode ? VENetwork.TerminalPage.PATTERNS : 0);

        boolean changed = force || pages != lastPages || view.size() != lastTotal || flags != lastFlags
                || page != lastSentPage || !TerminalView.sameEntries(slice, lastPage);
        lastPage = slice;
        if (!changed) return;
        lastPages = pages;
        lastTotal = view.size();
        lastFlags = flags;
        lastSentPage = page;
        PacketDistributor.sendToPlayer(player, new VENetwork.TerminalPage(containerId, page, pages, view.size(), flags, slice));
    }

    private void sendJobs(ServerPlayer player, ServerLevel level) {
        List<CraftingJob> jobs = CraftingJobSavedData.get(level.getServer()).jobsFor(endpoint).stream()
                .filter(CraftingJob::active).toList();
        firstJob = Math.clamp(firstJob, 0, Math.max(0, jobs.size() - VENetwork.TerminalJobs.VISIBLE_ROWS));
        List<VENetwork.JobRow> rows = new ArrayList<>();
        for (int i = firstJob; i < Math.min(jobs.size(), firstJob + VENetwork.TerminalJobs.VISIBLE_ROWS); i++) {
            CraftingJob job = jobs.get(i);
            List<String> machines = new ArrayList<>();
            for (var step : job.steps) {
                if (step.endpoint == null) continue;
                BlockPos port = SableCompat.resolve(level, step.endpoint);
                if (port == null || !level.hasChunkAt(port)) continue;
                var state = level.getBlockState(port);
                if (!state.is(VEBlocks.CORPOREA_MACHINE_PORT.get())) continue;
                BlockPos pos = port.relative(state.getValue(CorporeaPortBlock.FACING));
                if (step.pattern.op == CraftPattern.Op.MIX || step.pattern.op == CraftPattern.Op.FILL
                        || step.pattern.op == CraftPattern.Op.PRESS || step.pattern.op == CraftPattern.Op.DEPLOY) pos = pos.above(2);
                if (!level.hasChunkAt(pos)) continue;
                var world = BlockPos.containing(SableCompat.toWorld(level, net.minecraft.world.phys.Vec3.atCenterOf(pos)));
                machines.add(level.getBlockState(pos).getBlock().getName().getString() + " @ " + world.toShortString());
            }
            rows.add(new VENetwork.JobRow(job.id, limit(job.target.toStack(1).getHoverName().getString(), 154),
                    job.delivered + "/" + job.requested + " " + job.status.name().toLowerCase(java.util.Locale.ROOT),
                    limit(String.join(", ", machines), 154), !job.cancelled && job.status != CraftingJob.Status.REFUNDING));
        }
        var packet = new VENetwork.TerminalJobs(containerId, firstJob, jobs.size(), rows);
        if (!packet.equals(lastJobs)) {
            lastJobs = packet;
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private static String limit(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private List<dev.viridianengineering.content.autocraft.CraftPattern> patternView(ServerLevel level) {
        String q = search.toLowerCase(java.util.Locale.ROOT).trim();
        List<dev.viridianengineering.content.autocraft.CraftPattern> out = new ArrayList<>();
        for (var p : dev.viridianengineering.content.autocraft.PatternStore.get(level.getServer()).patterns(endpoint))
            if (q.isEmpty() || p.output.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(p);
        out.sort(java.util.Comparator.comparing(p -> p.output.getHoverName().getString()));
        return out;
    }

    /** The first active job's progress, e.g. "Precision Mechanism 37/500 running". */
    private void sendStatus(ServerPlayer player, ServerLevel level) {
        String status = "";
        for (var job : dev.viridianengineering.content.autocraft.CraftingJobSavedData.get(level.getServer()).jobsFor(endpoint)) {
            if (!job.active()) continue;
            status = job.target.toStack(1).getHoverName().getString() + " " + job.delivered + "/" + job.requested + " "
                    + job.status.name().toLowerCase(java.util.Locale.ROOT);
            if (job.status == dev.viridianengineering.content.autocraft.CraftingJob.Status.MISSING && !job.missing.isEmpty())
                status += ": " + job.missing.size() + " missing";
            break;
        }
        if (status.length() > 250) status = status.substring(0, 250);
        if (status.equals(lastStatus)) return;
        lastStatus = status;
        PacketDistributor.sendToPlayer(player, new VENetwork.TerminalStatus(containerId, status));
    }

    @Override
    public void broadcastChanges() {
        if (++refreshTicks >= REFRESH_INTERVAL) {
            refreshTicks = 0;
            refresh(false);
        }
        super.broadcastChanges();
    }

    /** All client input arrives here (see {@link VENetwork}). */
    public void handleAction(ServerPlayer player, VENetwork.TerminalAction a) {
        if (!stillValid(player) || !(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = terminalPos();
        if (pos == null) return;
        VENetwork.Action[] actions = VENetwork.Action.values();
        if (a.action() < 0 || a.action() >= actions.length) return;

        switch (actions[a.action()]) {
            case PAGE -> page += Integer.signum(a.index());
            case SEARCH -> {
                search = a.text().length() > 64 ? a.text().substring(0, 64) : a.text();
                page = 0;
            }
            case SORT -> {
                sort = sort.next();
                page = 0;
            }
            case WITHDRAW -> {
                if (patternMode) break;
                if (a.index() < 0 || a.index() >= lastPage.size()) break;
                ItemStack template = lastPage.get(a.index()).template();
                int want = a.button() == 1 ? 1 : template.getMaxStackSize();
                if (!a.shift() && !getCarried().isEmpty()) {
                    // Clicking the grid while holding an item deposits it instead.
                    depositCarried(level, pos, a.button() == 1);
                    break;
                }
                ItemStack got = CorporeaNetworkManager.withdraw(level, pos, template, want, access(), new TransactionContext());
                if (got.isEmpty()) break;
                if (a.shift()) {
                    if (!player.getInventory().add(got) && !got.isEmpty()) {
                        // No room: put it straight back so nothing is lost or dropped.
                        ItemStack back = CorporeaNetworkManager.deposit(level, pos, got, access(), new TransactionContext());
                        if (!back.isEmpty()) player.drop(back, false);
                    }
                } else {
                    setCarried(got);
                }
            }
            case DEPOSIT -> depositCarried(level, pos, a.button() == 1);
            case MODE -> {
                patternMode = !patternMode;
                page = 0;
            }
            case RECORD -> recordGrid(player, level);
            case RECORD_RECIPE -> recordRecipe(player, level, pos, a.text());
            case CRAFT -> {
                if (a.index() < 0 || a.index() >= lastPatternPage.size()) break;
                long amount;
                try { amount = Long.parseLong(a.text().trim()); } catch (NumberFormatException e) { amount = 1; }
                amount = Math.clamp(amount, 1, 1_000_000);
                var p = lastPatternPage.get(a.index());
                var res = dev.viridianengineering.content.autocraft.CraftingScheduler.request(level, pos, p.outputKey(), amount);
                player.displayClientMessage(res.error() != null ? res.error()
                        : net.minecraft.network.chat.Component.translatable("message.viridian_engineering.autocraft.started",
                        amount, p.output.getHoverName()), true);
            }
            case REMOVE_PATTERN -> {
                if (a.index() < 0 || a.index() >= lastPatternPage.size()) break;
                dev.viridianengineering.content.autocraft.PatternStore.get(level.getServer()).remove(endpoint, lastPatternPage.get(a.index()).id);
            }
            case CANCEL_JOB -> {
                if (!showJobs || a.text().length() != 36) break;
                UUID id;
                try { id = UUID.fromString(a.text()); } catch (IllegalArgumentException invalid) { break; }
                CraftingJobSavedData.get(level.getServer()).cancelAt(endpoint, id);
            }
            case JOBS -> {
                showJobs = a.index() != 0;
                if (showJobs) firstJob = 0;
                lastJobs = null;
            }
            case JOB_SCROLL -> { if (showJobs) firstJob += Integer.signum(a.index()); }
        }
        refresh(true);
        broadcastChanges();
    }

    // ------------------------------------------------------------------ patterns (spec 3.7)

    /** Ghost-grid recording: the recipe the crafting grid currently matches becomes a pattern (items stay put). */
    private void recordGrid(ServerPlayer player, ServerLevel level) {
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting.asCraftInput(), level);
        if (recipe.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viridian_engineering.pattern.no_match", "grid"), true);
            return;
        }
        store(player, level, dev.viridianengineering.content.autocraft.PatternFactory.fromRecipe(level, recipe.get(), java.util.Set.of(), gridSnapshot()));
    }

    /** JEI / EMI recipe transfer: the client names a recipe; the server looks it up and validates it. */
    private void recordRecipe(ServerPlayer player, ServerLevel level, BlockPos pos, String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        var holder = rl == null ? java.util.Optional.<net.minecraft.world.item.crafting.RecipeHolder<?>>empty() : level.getRecipeManager().byKey(rl);
        if (holder.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viridian_engineering.pattern.unsupported", id), true);
            return;
        }
        java.util.Set<net.minecraft.world.item.Item> stocked = new java.util.HashSet<>();
        for (ItemStack s : CorporeaNetworkManager.browse(level, pos, access(), new TransactionContext())) stocked.add(s.getItem());
        store(player, level, dev.viridianengineering.content.autocraft.PatternFactory.fromRecipe(level, holder.get(), stocked));
    }

    private void store(ServerPlayer player, ServerLevel level, dev.viridianengineering.content.autocraft.PatternFactory.Result result) {
        if (!result.ok()) {
            player.displayClientMessage(result.error(), true);
            return;
        }
        boolean added = dev.viridianengineering.content.autocraft.PatternStore.get(level.getServer()).add(endpoint, result.pattern());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(added
                ? "message.viridian_engineering.pattern.recorded" : "message.viridian_engineering.pattern.full",
                result.pattern().output.getHoverName()), true);
    }

    private void depositCarried(ServerLevel level, BlockPos pos, boolean single) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return;
        if (single) {
            ItemStack one = carried.copyWithCount(1);
            if (CorporeaNetworkManager.deposit(level, pos, one, access(), new TransactionContext()).isEmpty())
                carried.shrink(1);
            setCarried(carried);
        } else {
            setCarried(CorporeaNetworkManager.deposit(level, pos, carried, access(), new TransactionContext()));
        }
    }

    // ------------------------------------------------------------------ crafting

    @Override
    public void slotsChanged(Container container) {
        if (!(owner.level() instanceof ServerLevel level) || !(owner instanceof ServerPlayer player)) return;
        ItemStack output = ItemStack.EMPTY;
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting.asCraftInput(), level);
        if (recipe.isPresent() && result.setRecipeUsed(level, player, recipe.get())) {
            output = recipe.get().value().assemble(crafting.asCraftInput(), level.registryAccess());
            if (!output.isItemEnabled(level.enabledFeatures())) output = ItemStack.EMPTY;
        }
        result.setItem(0, output);
        setRemoteSlot(RESULT, output);
        player.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, output));
    }

    /** After a craft, refill grid slots that ran out with the same item from the network. */
    private void refillGrid(List<ItemStack> before) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        BlockPos pos = terminalPos();
        if (pos == null) return;
        for (int i = 0; i < crafting.getContainerSize(); i++) {
            ItemStack was = before.get(i);
            if (was.isEmpty() || !crafting.getItem(i).isEmpty()) continue;
            ItemStack got = CorporeaNetworkManager.withdraw(level, pos, was, 1, access(), new TransactionContext());
            if (!got.isEmpty()) crafting.setItem(i, got);
        }
        slotsChanged(crafting);
    }

    private List<ItemStack> gridSnapshot() {
        List<ItemStack> snap = new ArrayList<>(crafting.getContainerSize());
        for (int i = 0; i < crafting.getContainerSize(); i++) snap.add(crafting.getItem(i).copy());
        return snap;
    }

    private final class RefillingResultSlot extends ResultSlot {
        RefillingResultSlot(Player player, CraftingContainer grid, Container result, int index, int x, int y) {
            super(player, grid, result, index, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            List<ItemStack> before = gridSnapshot();
            super.onTake(player, stack);
            if (!player.level().isClientSide) refillGrid(before);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player.level() instanceof ServerLevel level) || !stillValid(player) || index < 0 || index >= INV_END)
            return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        if (index == RESULT) {
            // Shift-click crafts repeatedly (grid refills from the network) until it runs out or the inventory is full.
            ItemStack first = slot.getItem().copy();
            for (int i = 0; i < MAX_BULK_CRAFTS && slot.hasItem()
                    && ItemStack.isSameItemSameComponents(slot.getItem(), first); i++) {
                ItemStack crafted = slot.getItem().copy();
                if (!canFit(player.getInventory(), crafted)) break;
                ItemStack taken = slot.remove(crafted.getCount());
                slot.onTake(player, taken);
                player.getInventory().add(taken);
                if (!taken.isEmpty()) player.drop(taken, false);
            }
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        if (index >= INV_START) {
            // Player inventory -> network.
            BlockPos pos = terminalPos();
            if (pos == null) return ItemStack.EMPTY;
            ItemStack remainder = CorporeaNetworkManager.deposit(level, pos, stack, access(), new TransactionContext());
            slot.setByPlayer(remainder);
            refresh(true);
            return ItemStack.EMPTY; // never loop: the network accepted what it could
        }
        // Grid -> player inventory.
        if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        slot.setChanged();
        return ItemStack.EMPTY;
    }

    private static boolean canFit(Inventory inv, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(s, stack)) room += s.getMaxStackSize() - s.getCount();
            if (room >= stack.getCount()) return true;
        }
        return false;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index != RESULT;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide || !(player.level() instanceof ServerLevel level)) return;
        // Return grid contents to the network first, then to the player.
        BlockPos pos = terminalPos();
        for (int i = 0; i < crafting.getContainerSize(); i++) {
            ItemStack s = crafting.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            if (pos != null) s = CorporeaNetworkManager.deposit(level, pos, s, access(), new TransactionContext());
            if (!s.isEmpty() && !player.getInventory().add(s)) player.drop(s, false);
        }
    }
}
