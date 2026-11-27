package dev.viridianengineering.content.elementium;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.drive.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Elementium Request Interface: holds open requests (item + how many are still wanted). Smart Funnels
 * and Smart Tunnels within range follow the nearest interface and move or divert only requested items,
 * counting each request down as items pass. Runs on mana: while it has open requests it uses
 * {@code interfaceUpkeepPerTick}; with an empty buffer it goes offline and its devices fall back to
 * their plain filters.
 */
public class RequestInterfaceBlockEntity extends SmartBlockEntity {

    /** Loaded interfaces per level, so funnels and tunnels find the nearest one without scanning blocks. */
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();

    private final LinkedHashMap<ItemStackKey, Long> requests = new LinkedHashMap<>();
    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.ELEMENTIUM_MANA_BUFFER.get(), true, false);
    private boolean online;
    private long lastSyncedMana = -1;

    public RequestInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ------------------------------------------------------------------ registry

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) LOADED.computeIfAbsent(level, l -> new HashSet<>()).add(worldPosition.immutable());
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null) { Set<BlockPos> set = LOADED.get(level); if (set != null) set.remove(worldPosition); }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) { Set<BlockPos> set = LOADED.get(level); if (set != null) set.remove(worldPosition); }
    }

    /** The nearest loaded interface within range of {@code pos} on the same ship (or terrain), or null. */
    public static RequestInterfaceBlockEntity nearest(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level);
        if (set == null || set.isEmpty()) return null;
        int range = VEConfig.ELEMENTIUM_LINK_RANGE.get();
        UUID ship = SableCompat.endpointKey(level, pos, Direction.DOWN).sableSubLevelId();
        RequestInterfaceBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : set) {
            double d = p.distSqr(pos);
            if (d > (double) range * range || d >= bestDist) continue;
            if (!(level.getBlockEntity(p) instanceof RequestInterfaceBlockEntity be)) continue;
            if (!Objects.equals(ship, SableCompat.endpointKey(level, p, Direction.DOWN).sableSubLevelId())) continue;
            best = be;
            bestDist = d;
        }
        return best;
    }

    // ------------------------------------------------------------------ requests

    public boolean isOnline() { return level != null && level.isClientSide ? online : mana.getStoredMana() > 0; }

    public boolean hasRequests() { return !requests.isEmpty(); }

    public Map<ItemStackKey, Long> requests() { return Collections.unmodifiableMap(requests); }

    public long outstanding(ItemStack stack) {
        return stack.isEmpty() ? 0 : requests.getOrDefault(ItemStackKey.of(stack), 0L);
    }

    public void addRequest(ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        requests.merge(ItemStackKey.of(stack), count, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
        changed();
    }

    /** Counts {@code count} items of {@code stack} against its request. */
    public void fulfil(ItemStack stack, long count) {
        ItemStackKey key = ItemStackKey.of(stack);
        long left = requests.getOrDefault(key, 0L) - count;
        if (left > 0) requests.put(key, left); else requests.remove(key);
        changed();
    }

    public void clearRequests() {
        requests.clear();
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    /** Online while it has mana (requests cost {@code interfaceUpkeepPerTick} each tick they are open). */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (hasRequests()) mana.consume(Math.min(mana.getStoredMana(), VEConfig.ELEMENTIUM_INTERFACE_UPKEEP.get()));
        boolean now = mana.getStoredMana() > 0;
        long stored = mana.getStoredMana();
        if (now != online || (level.getGameTime() % 10 == 0 && stored != lastSyncedMana)) {
            online = now;
            lastSyncedMana = stored;
            sendData();
        }
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        tag.putBoolean("Online", online);
        ListTag list = new ListTag();
        requests.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.put("Item", k.toStack(1).save(registries));
            e.putLong("Count", v);
            list.add(e);
        });
        tag.put("Requests", list);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        online = tag.getBoolean("Online");
        requests.clear();
        for (Tag t : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ItemStack s = ItemStack.parseOptional(registries, e.getCompound("Item"));
            if (!s.isEmpty()) requests.put(ItemStackKey.of(s), e.getLong("Count"));
        }
    }
}
