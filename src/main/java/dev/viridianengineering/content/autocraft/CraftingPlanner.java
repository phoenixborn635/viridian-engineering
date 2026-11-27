package dev.viridianengineering.content.autocraft;

import dev.viridianengineering.content.drive.ItemStackKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spec 3.7 recursive sub-recipe resolution. Pure and deterministic: given what the network holds and
 * the terminal's patterns, works out how many times to run each pattern, which stored items to
 * reserve into escrow, and what is missing.
 * <ul>
 *   <li>Each needed item comes first from surplus made by earlier crafts in the same plan, then from
 *       stock (reserved), then from a pattern (recursively).</li>
 *   <li>The requested item itself is always crafted, never taken from stock.</li>
 *   <li>Rolled outputs (sequenced assembly) plan for the expected yield; a job tops up any shortfall
 *       when its planned crafts are done.</li>
 *   <li>Cycles and depth over {@value #MAX_DEPTH} count as missing.</li>
 * </ul>
 */
public final class CraftingPlanner {

    public static final int MAX_DEPTH = 32;

    public record Step(CraftPattern pattern, long crafts) {}

    public record Plan(List<Step> steps, Map<ItemStackKey, Long> reserve, Map<ItemStackKey, Long> missing) {
        public boolean complete() { return missing.isEmpty(); }

        public long craftsOf(ItemStackKey output) {
            long n = 0;
            for (Step s : steps) if (s.pattern().outputKey().equals(output)) n += s.crafts();
            return n;
        }
    }

    private final Map<ItemStackKey, CraftPattern> byOutput = new HashMap<>();
    private final Map<ItemStackKey, Long> stock;
    private final Map<ItemStackKey, Long> surplus = new HashMap<>();
    private final Map<ItemStackKey, Long> reserve = new LinkedHashMap<>();
    private final Map<ItemStackKey, Long> missing = new LinkedHashMap<>();
    private final Map<UUID, Long> crafts = new LinkedHashMap<>();
    private final Map<UUID, CraftPattern> byId = new HashMap<>();
    private final Set<ItemStackKey> path = new HashSet<>();

    private CraftingPlanner(Map<ItemStackKey, Long> stock, Collection<CraftPattern> patterns) {
        this.stock = new HashMap<>(stock);
        for (CraftPattern p : patterns) {
            byOutput.putIfAbsent(p.outputKey(), p);
            byId.put(p.id, p);
        }
    }

    public static Plan plan(ItemStackKey target, long amount, Map<ItemStackKey, Long> stock, Collection<CraftPattern> patterns) {
        return plan(target, amount, Map.of(), stock, patterns);
    }

    /**
     * As above, plus {@code extra}: further items needed directly (taken from stock first, crafted if
     * short), e.g. deployer parts for assemblies already in flight when a job re-plans.
     */
    public static Plan plan(ItemStackKey target, long amount, Map<ItemStackKey, Long> extra,
                            Map<ItemStackKey, Long> stock, Collection<CraftPattern> patterns) {
        CraftingPlanner planner = new CraftingPlanner(stock, patterns);
        extra.forEach((k, v) -> planner.need(k, v, 0, false));
        if (amount > 0) planner.need(target, amount, 0, true);
        List<Step> steps = new ArrayList<>();
        planner.crafts.forEach((id, n) -> steps.add(new Step(planner.byId.get(id), n)));
        return new Plan(steps, planner.reserve, planner.missing);
    }

    private void need(ItemStackKey key, long amount, int depth, boolean top) {
        if (amount <= 0) return;
        if (!top) {
            amount -= take(surplus, key, amount);
            long fromStock = take(stock, key, amount);
            if (fromStock > 0) reserve.merge(key, fromStock, Long::sum);
            amount -= fromStock;
            if (amount <= 0) return;
        }
        CraftPattern pattern = byOutput.get(key);
        if (pattern == null || depth > MAX_DEPTH || path.contains(key) || pattern.yield() <= 0) {
            missing.merge(key, amount, Long::sum);
            return;
        }
        long runs = (long) Math.ceil(amount / pattern.yield() - 1e-9);
        path.add(key);
        for (Map.Entry<ItemStackKey, Long> in : pattern.inputsPerCraft().entrySet())
            need(in.getKey(), in.getValue() * runs, depth + 1, false);
        path.remove(key);
        crafts.merge(pattern.id, runs, Long::sum); // after its inputs: leaves come first
        if (pattern.chance >= 1.0) {
            long extra = runs * pattern.output.getCount() - amount;
            if (extra > 0) surplus.merge(key, extra, Long::sum);
        }
    }

    private static long take(Map<ItemStackKey, Long> pool, ItemStackKey key, long amount) {
        long have = pool.getOrDefault(key, 0L);
        long got = Math.min(have, amount);
        if (got > 0) pool.put(key, have - got);
        return got;
    }
}
