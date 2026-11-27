package dev.viridianengineering.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Member block -> controller lookup for formed multiblocks (reactor, battery), so a port's capability
 * or a break event finds its controller in O(1). Rebuilt by each controller whenever it validates;
 * nothing here is saved (controllers re-register on load).
 */
public final class MultiblockIndex {

    private final Map<Level, Map<BlockPos, BlockPos>> byLevel = new WeakHashMap<>();
    private final Map<Level, Map<BlockPos, Set<BlockPos>>> owned = new WeakHashMap<>();

    public synchronized void claim(Level level, BlockPos controller, Collection<BlockPos> members) {
        release(level, controller);
        Map<BlockPos, BlockPos> map = byLevel.computeIfAbsent(level, l -> new HashMap<>());
        Set<BlockPos> set = new HashSet<>();
        for (BlockPos m : members) {
            BlockPos im = m.immutable();
            map.put(im, controller.immutable());
            set.add(im);
        }
        owned.computeIfAbsent(level, l -> new HashMap<>()).put(controller.immutable(), set);
    }

    public synchronized void release(Level level, BlockPos controller) {
        Map<BlockPos, Set<BlockPos>> o = owned.get(level);
        if (o == null) return;
        Set<BlockPos> set = o.remove(controller);
        Map<BlockPos, BlockPos> map = byLevel.get(level);
        if (set == null || map == null) return;
        for (BlockPos m : set) if (controller.equals(map.get(m))) map.remove(m);
    }

    @Nullable
    public synchronized BlockPos controllerOf(Level level, BlockPos member) {
        Map<BlockPos, BlockPos> map = byLevel.get(level);
        return map == null ? null : map.get(member);
    }

    public synchronized Set<BlockPos> membersOf(Level level, BlockPos controller) {
        Map<BlockPos, Set<BlockPos>> o = owned.get(level);
        Set<BlockPos> set = o == null ? null : o.get(controller);
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
