package dev.viridianengineering.content.corporea;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.*;

/** Server-only physical topology. Inventories and power are resolved afresh for each operation. */
public final class CorporeaNetworkManager {
    public enum Access { MANUAL_WIRED, AUTOMATED }
    private static final Map<ServerLevel, Map<MachineEndpointKey, Set<MachineEndpointKey>>> CACHE = new WeakHashMap<>();
    private CorporeaNetworkManager() {}

    private static int version;

    /** Bumped whenever any cached topology is dropped; machine discovery caches key on it. */
    public static int version() { return version; }

    public static void invalidate(Level level) {
        version++;
        if (level instanceof ServerLevel server) {
            CACHE.remove(server);
            POWER.remove(server);
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof Level level) invalidate(level);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level) invalidate(level);
    }

    /** No unloaded chunk is read or force-loaded. Oversized graphs fail closed, never return partial inventories. */
    public static Set<MachineEndpointKey> wiredNodes(ServerLevel level, BlockPos origin) {
        MachineEndpointKey root = SableCompat.endpointKey(level, origin, Direction.DOWN);
        var cache = CACHE.computeIfAbsent(level, ignored -> new HashMap<>());
        if (cache.containsKey(root)) return cache.get(root);
        Set<MachineEndpointKey> nodes = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> positions = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(origin.immutable());
        int limit = VEConfig.CORPOREA_MAX_WIRED_NODES.get();
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !level.hasChunkAt(pos)) continue;
            var nodeState = level.getBlockState(pos);
            if (!(nodeState.getBlock() instanceof CorporeaNode)) continue;
            MachineEndpointKey key = SableCompat.endpointKey(level, pos, Direction.DOWN);
            if (!Objects.equals(root.sableSubLevelId(), key.sableSubLevelId())) continue;
            if (nodes.size() >= limit) {
                positions.add(pos);
                for (BlockPos member : positions)
                    if (level.getBlockEntity(member) instanceof CorporeaPipeBlockEntity pipe)
                        pipe.setLightRoute(CorporeaPipeBlockEntity.LightRoute.DISCONNECTED);
                cache.put(root, Set.of());
                return Set.of();
            }
            nodes.add(key);
            positions.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (level.hasChunkAt(next) && CorporeaNode.links(nodeState, side, level.getBlockState(next))) pending.addLast(next);
            }
        }
        Set<MachineEndpointKey> result = Collections.unmodifiableSet(nodes);
        // One shared component snapshot, rather than a separate graph per terminal.
        for (MachineEndpointKey key : nodes) cache.put(key, result);
        updatePipeLights(level, positions);
        return result;
    }

    /**
     * Visuals only: multi-source BFS gives every member its distance to the nearest wired terminal.
     * Each connected edge with a distance gradient carries light from the farther end to the nearer end.
     */
    private static void updatePipeLights(ServerLevel level, Set<BlockPos> positions) {
        Map<BlockPos, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : positions)
            if (level.getBlockState(pos).getBlock() instanceof CorporeaTerminalBlock) {
                dist.put(pos, 0);
                queue.add(pos);
            }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            var state = level.getBlockState(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (dist.containsKey(next) || !positions.contains(next)) continue;
                if (!CorporeaNode.links(state, side, level.getBlockState(next))) continue;
                dist.put(next, dist.get(pos) + 1);
                queue.addLast(next);
            }
        }
        for (BlockPos pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof CorporeaPipeBlockEntity pipe)) continue;
            int distance = dist.getOrDefault(pos, -1);
            if (distance < 0) {
                pipe.setLightRoute(CorporeaPipeBlockEntity.LightRoute.DISCONNECTED);
                continue;
            }
            var state = pipe.getBlockState();
            int toward = 0, from = 0;
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                Integer other = dist.get(next);
                if (other == null || !CorporeaNode.links(state, side, level.getBlockState(next))) continue;
                if (other < distance) toward |= 1 << side.get3DDataValue();
                if (other > distance) from |= 1 << side.get3DDataValue();
            }
            pipe.setLightRoute(new CorporeaPipeBlockEntity.LightRoute(distance, toward, from));
        }
    }

    private record Route(BlockPos origin, List<ArcaneSparkEntity> sparks) {}

    /** Power state per wired component, valid for one game tick (many ports share one component). */
    private static final Map<ServerLevel, PowerCache> POWER = new WeakHashMap<>();

    private static final class PowerCache {
        long tick = Long.MIN_VALUE;
        final Map<Set<MachineEndpointKey>, Boolean> byComponent = new IdentityHashMap<>();
    }

    public static boolean isPowered(ServerLevel level, BlockPos origin) {
        Set<MachineEndpointKey> component = wiredNodes(level, origin);
        PowerCache cache = POWER.computeIfAbsent(level, l -> new PowerCache());
        long now = level.getGameTime();
        if (cache.tick != now) {
            cache.tick = now;
            cache.byComponent.clear();
        }
        Boolean known = cache.byComponent.get(component);
        if (known != null) return known;
        boolean powered = computePowered(level, origin);
        cache.byComponent.put(component, powered);
        return powered;
    }

    private static boolean computePowered(ServerLevel level, BlockPos origin) {
        // Wireless storage segments inherit the controller's power, even without a local controller.
        Set<MachineEndpointKey> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            for (MachineEndpointKey key : wiredNodes(level, pending.removeFirst())) {
                if (!visited.add(key)) continue;
                if (visited.size() > VEConfig.CORPOREA_MAX_WIRED_NODES.get()) return false;
                BlockPos pos = SableCompat.resolve(level, key);
                if (pos == null || !level.hasChunkAt(pos)) continue;
                if (level.getBlockEntity(pos) instanceof CorporeaTerminalBlockEntity terminal && terminal.isPowered()) return true;
                ArcaneSparkEntity spark = ArcaneSparkEntity.at(level, pos);
                if (spark != null)
                    for (ArcaneSparkEntity other : ArcaneSparkEntity.linksOf(spark)) pending.add(other.attachmentPos());
            }
        }
        return false;
    }

    /**
     * Every node reachable for automation from {@code origin}: the wired component plus components
     * linked by Arcane Sparks. Empty while unpowered (automation pauses below the RPM threshold).
     * Used by autocrafting machine discovery; nothing is routed, so sparks are not drained.
     */
    public static List<BlockPos> automationNodes(ServerLevel level, BlockPos origin) {
        if (!isPowered(level, origin)) return List.of();
        List<BlockPos> out = new ArrayList<>();
        Set<MachineEndpointKey> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            for (MachineEndpointKey node : wiredNodes(level, pending.removeFirst())) {
                if (!visited.add(node)) continue;
                if (visited.size() > VEConfig.CORPOREA_MAX_WIRED_NODES.get()) return List.of();
                BlockPos pos = SableCompat.resolve(level, node);
                if (pos == null || !level.hasChunkAt(pos)) continue;
                out.add(pos);
                ArcaneSparkEntity spark = ArcaneSparkEntity.at(level, pos);
                if (spark != null)
                    for (ArcaneSparkEntity other : ArcaneSparkEntity.linksOf(spark))
                        if (other.attachmentPos() != null) pending.add(other.attachmentPos());
            }
        }
        return out;
    }

    public static MachineEndpointKey inventoryKey(ServerLevel level,BlockPos target) {
        BlockPos identity=target;
        if (level.getBlockEntity(target) instanceof ItemVaultBlockEntity vault) identity=vault.getController();
        // A vanilla double chest also represents one inventory through either half.
        var state=level.getBlockState(target);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other=target.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            if (other.compareTo(target)<0) identity=other;
        }
        return SableCompat.endpointKey(level,identity,Direction.DOWN);
    }

    public static List<IItemHandler> inventories(ServerLevel level, BlockPos origin, Access access, TransactionContext tx) {
        boolean powered=isPowered(level,origin);
        if (access==Access.AUTOMATED && !powered) return List.of();
        List<IItemHandler> result=new ArrayList<>();
        Set<MachineEndpointKey> visited=new HashSet<>();
        Set<IItemHandler> handlers=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Route> pending=new ArrayDeque<>(); pending.add(new Route(origin,List.of()));
        while (!pending.isEmpty()) {
            Route route=pending.removeFirst();
            for (MachineEndpointKey node:wiredNodes(level,route.origin())) {
                if (!visited.add(node)) continue;
                if (visited.size()>VEConfig.CORPOREA_MAX_WIRED_NODES.get()) return List.of();
                BlockPos pos=SableCompat.resolve(level,node);
                if (pos==null || !level.hasChunkAt(pos)) continue;
                var state=level.getBlockState(pos);
                if (state.getBlock() instanceof CorporeaPortBlock && !state.is(VEBlocks.CORPOREA_BRIDGE_PORT.get())) {
                    if (access==Access.MANUAL_WIRED && level.getBlockEntity(pos) instanceof CorporeaPortBlockEntity port && tx.visitInventory(inventoryKey(level,pos)))
                        result.add(new RoutedInventory(new EscrowView(port.buffer),route.sparks()));
                    Direction face=state.getValue(CorporeaPortBlock.FACING); BlockPos target=pos.relative(face);
                    // A machine port in front of an autocrafting endpoint (belt, or depot under a press/deployer) is a
                    // dispatch point, not storage: the network must never park items on it.
                    boolean endpoint=state.is(VEBlocks.CORPOREA_MACHINE_PORT.get()) && level.hasChunkAt(target)
                            && dev.viridianengineering.content.autocraft.MachineDiscovery.isMachineTarget(level,target);
                    if (!endpoint && level.hasChunkAt(target) && Objects.equals(node.sableSubLevelId(),SableCompat.endpointKey(level,target,face.getOpposite()).sableSubLevelId()))
                        addInventory(level,target,face.getOpposite(),route.sparks(),tx,handlers,result);
                }
                // Archive Pylons are storage nodes; wired ones work unpowered for manual access (decision 2A).
                if (level.getBlockEntity(pos) instanceof dev.viridianengineering.content.drive.ArchivePylonBlockEntity pylon
                        && tx.visitInventory(node))
                    for (IItemHandler drive : pylon.storageViews()) result.add(new RoutedInventory(drive, route.sparks()));
                if (!powered) continue;
                ArcaneSparkEntity spark=ArcaneSparkEntity.at(level,pos);
                if (spark!=null) for (ArcaneSparkEntity other:ArcaneSparkEntity.linksOf(spark)) {
                    List<ArcaneSparkEntity> path=new ArrayList<>(route.sparks()); path.add(spark);path.add(other);
                    pending.add(new Route(other.attachmentPos(),List.copyOf(path)));
                }
                if (state.is(VEBlocks.CORPOREA_BRIDGE_PORT.get()) && tx.enterBridge(node)) {
                    var nativeSpark=velrondevs.botania.api.corporea.CorporeaHelper.instance().getSparkForBlock(level,pos);
                    if (nativeSpark==null) continue;
                    for (var nativeNode:velrondevs.botania.api.corporea.CorporeaHelper.instance().getNodesOnNetwork(nativeSpark)) {
                        if (nativeNode==null || nativeNode.getWorld()!=level || !level.hasChunkAt(nativeNode.getPos())) continue;
                        BlockPos target=nativeNode.getPos();
                        if (level.getBlockState(target).is(VEBlocks.CORPOREA_BRIDGE_PORT.get())) {
                            // A bridge may never energize an unpowered addon component.
                            if (isPowered(level,target)) pending.add(new Route(target,route.sparks()));
                        } else addInventory(level,target,Direction.UP,route.sparks(),tx,handlers,result);
                    }
                }
            }
        }
        return result;
    }

    private static void addInventory(ServerLevel level,BlockPos target,Direction face,List<ArcaneSparkEntity> path,TransactionContext tx,Set<IItemHandler> seen,List<IItemHandler> result) {
        if (dev.viridianengineering.content.autocraft.CraftingScheduler.isClaimedMachine(level,target)) return;
        IItemHandler handler=level.getCapability(Capabilities.ItemHandler.BLOCK,target,face);
        if (handler==null && face==Direction.UP) handler=level.getCapability(Capabilities.ItemHandler.BLOCK,target,null);
        if (handler!=null && tx.visitInventory(inventoryKey(level,target)) && seen.add(handler)) result.add(new RoutedInventory(handler,path));
    }

    private record EscrowView(IItemHandler delegate) implements IItemHandler {
        public int getSlots() { return delegate.getSlots(); }
        public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { return stack; }
        public ItemStack extractItem(int slot,int amount,boolean simulate) { return delegate.extractItem(slot,amount,simulate); }
        public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
        public boolean isItemValid(int slot,ItemStack stack) { return false; }
    }
    private record RoutedInventory(IItemHandler delegate,List<ArcaneSparkEntity> path) implements IItemHandler {
        private void routed() { for (ArcaneSparkEntity spark:path) spark.routed(); }
        @Override public int getSlots() { return delegate.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return delegate.isItemValid(slot,stack); }
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
            ItemStack remaining=delegate.insertItem(slot,stack,simulate);
            if (!simulate && remaining.getCount()<stack.getCount()) routed();
            return remaining;
        }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) {
            ItemStack got=delegate.extractItem(slot,amount,simulate);
            if (!simulate && !got.isEmpty()) routed();
            return got;
        }
    }

    /** Returns independent stack copies; counts may exceed a vanilla stack's size. */
    public static List<ItemStack> browse(ServerLevel level, BlockPos origin, Access access, TransactionContext tx) {
        List<ItemStack> items = new ArrayList<>();
        for (IItemHandler handler : inventories(level, origin, access, tx)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) items.add(stack.copy());
            }
        }
        return items;
    }

    /** Caller retains the returned remainder. The offered stack is never modified. */
    public static ItemStack deposit(ServerLevel level, BlockPos origin, ItemStack offered, Access access, TransactionContext tx) {
        ItemStack remainder = offered.copy();
        for (IItemHandler handler : inventories(level, origin, access, tx)) {
            remainder = ItemHandlerHelper.insertItemStacked(handler, remainder, false);
            if (remainder.isEmpty()) break;
        }
        return remainder;
    }

    /** Exact component matching prevents mixing differently enchanted/named items. */
    public static ItemStack withdraw(ServerLevel level, BlockPos origin, ItemStack filter, int amount, Access access, TransactionContext tx) {
        if (filter.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int remaining = Math.min(amount, filter.getMaxStackSize());
        ItemStack result = ItemStack.EMPTY;
        for (IItemHandler handler : inventories(level, origin, access, tx)) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                if (!ItemStack.isSameItemSameComponents(filter, handler.getStackInSlot(slot))) continue;
                ItemStack extracted = handler.extractItem(slot, remaining, false);
                if (extracted.isEmpty()) continue;
                if (result.isEmpty()) result = extracted.copy(); else result.grow(extracted.getCount());
                remaining -= extracted.getCount();
            }
            if (remaining == 0) break;
        }
        return result;
    }
}
