package dev.viridianengineering.content.airship;

import com.mojang.authlib.GameProfile;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Every Bifrost trail block alive in one dimension (spec 3.8: 200-tick life, at most 64 per ship),
 * saved so a restart still clears them on time. Trail blocks have no block entity and never tick:
 * this list fades ({@code age} 0..3 over their life) and removes them.
 * <p>
 * When a ship already has 64 blocks out, placing one more retires its oldest block early, so the
 * trail keeps following the ship instead of stopping (still never more than 64 at once).
 */
public class BifrostTrails extends SavedData {

    public static final String ID = "viridian_engineering_bifrost_trails";

    record Entry(long pos, long placed, long expires, UUID ship) {}

    /** Oldest first (all blocks get the same lifetime, so this is also expiry order). */
    private final ArrayDeque<Entry> all = new ArrayDeque<>();
    private final Map<UUID, ArrayDeque<Entry>> byShip = new HashMap<>();

    public static BifrostTrails get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BifrostTrails::new, BifrostTrails::load, null), ID);
    }

    public int activeFor(UUID ship) {
        ArrayDeque<Entry> q = byShip.get(ship);
        return q == null ? 0 : q.size();
    }

    public int total() { return all.size(); }

    /** True if {@code pos} is a free cell a trail block may go into. */
    public static boolean canPlaceAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return false;
        BlockState s = level.getBlockState(pos);
        return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    /**
     * Places a trail block for {@code ship} if the cell is free and {@code owner} may build there
     * (spawn protection, world border and claim mods via NeoForge's place event).
     */
    public boolean tryPlace(ServerLevel level, BlockPos pos, UUID ship, @Nullable UUID owner) {
        if (!canPlaceAt(level, pos) || !permitted(level, pos, owner)) return false;
        BlockState trail = VEBlocks.BIFROST_TRAIL.getDefaultState();
        if (!level.setBlock(pos, trail, Block.UPDATE_ALL)) return false;
        long now = level.getGameTime();
        Entry e = new Entry(pos.asLong(), now, now + VEConfig.BIFROST_TRAIL_LIFETIME_TICKS.get(), ship);
        all.addLast(e);
        ArrayDeque<Entry> q = byShip.computeIfAbsent(ship, k -> new ArrayDeque<>());
        q.addLast(e);
        int max = VEConfig.BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP.get();
        while (q.size() > max) {
            Entry old = q.pollFirst();
            all.remove(old);
            clear(level, BlockPos.of(old.pos));
        }
        setDirty();
        return true;
    }

    private static boolean permitted(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                owner != null ? owner : UUID.fromString("5f3c1e0a-6b1d-4c9e-9a51-0b1f7e2d8c11"), "[Viridian Bifrost]"));
        if (!level.mayInteract(player, pos)) return false;
        BlockState was = level.getBlockState(pos);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        // ask without changing anything: fire the place event as if the trail were already there
        return !EventHooks.onBlockPlace(player, snapshot, Direction.UP) && level.getBlockState(pos) == was;
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockState(pos).is(VEBlocks.BIFROST_TRAIL.get()))
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BifrostTrails data = level.getDataStorage().get(new SavedData.Factory<>(BifrostTrails::new, BifrostTrails::load, null), ID);
            if (data != null && !data.all.isEmpty()) data.tick(level);
        }
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = false;
        while (!all.isEmpty() && all.peekFirst().expires <= now) {
            Entry e = all.pollFirst();
            ArrayDeque<Entry> q = byShip.get(e.ship);
            if (q != null) { q.remove(e); if (q.isEmpty()) byShip.remove(e.ship); }
            clear(level, BlockPos.of(e.pos));
            changed = true;
        }
        // fade: age 0..3 across the block's life (a client update only when it changes)
        if (now % 5 == 0) {
            for (Iterator<Entry> it = all.iterator(); it.hasNext(); ) {
                Entry e = it.next();
                BlockPos pos = BlockPos.of(e.pos);
                if (!level.isLoaded(pos)) continue;
                BlockState s = level.getBlockState(pos);
                if (!s.is(VEBlocks.BIFROST_TRAIL.get())) continue;
                double life = Math.max(1, e.expires - e.placed);
                int age = (int) Math.min(3, Math.floor(4 * (now - e.placed) / life));
                if (s.getValue(BifrostTrailBlock.AGE) != age)
                    level.setBlock(pos, s.setValue(BifrostTrailBlock.AGE, age), Block.UPDATE_CLIENTS);
            }
        }
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : all) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.pos);
            t.putLong("Placed", e.placed);
            t.putLong("Expires", e.expires);
            t.putUUID("Ship", e.ship);
            list.add(t);
        }
        tag.put("Trail", list);
        return tag;
    }

    public static BifrostTrails load(CompoundTag tag, HolderLookup.Provider registries) {
        BifrostTrails data = new BifrostTrails();
        for (Tag raw : tag.getList("Trail", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Entry e = new Entry(t.getLong("Pos"), t.getLong("Placed"), t.getLong("Expires"), t.getUUID("Ship"));
            data.all.addLast(e);
            data.byShip.computeIfAbsent(e.ship, k -> new ArrayDeque<>()).addLast(e);
        }
        return data;
    }
}
