package dev.viridianengineering.content.autocraft;

import dev.viridianengineering.content.drive.ItemStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A recorded autocrafting recipe (spec 3.7: kept in the terminal's SavedData, never as a pattern
 * item). Ingredients are concrete items (tags already resolved when the pattern was recorded), so
 * planning and dispatch never guess.
 * <ul>
 *   <li>{@link Kind#CRAFTING}: a crafting-table recipe, performed by the terminal itself.</li>
 *   <li>{@link Kind#PROCESSING}: one Create machine operation on a depot fed by a machine port
 *       (pressing, or deploying with {@link #held}).</li>
 *   <li>{@link Kind#SEQUENCED}: a Create sequenced-assembly recipe run on a discovered belt line;
 *       {@link #steps} / {@link #stepItems} describe one loop, repeated {@link #loops} times.</li>
 * </ul>
 */
public final class CraftPattern {

    public enum Kind { CRAFTING, PROCESSING, SEQUENCED }

    /** Machine operation kinds an endpoint can perform. */
    public enum Op { PRESS, DEPLOY, MIX, FILL, CUT, MECHANICAL_CRAFT }

    public static boolean station(Op op) {
        return op == Op.MIX || op == Op.CUT || op == Op.MECHANICAL_CRAFT;
    }

    public final UUID id;
    public final Kind kind;
    public final ResourceLocation recipeId;
    /** CRAFTING: the 3x3 grid, 9 entries, count 1 or empty. */
    public final List<ItemStack> grid;
    /** PROCESSING / SEQUENCED: the item placed on the depot or belt, per craft. */
    public final ItemStack input;
    /** PROCESSING: which machine. */
    public final Op op;
    /** PROCESSING + DEPLOY: the deployer's held item (consumed once per craft unless {@link #keepHeld}). */
    public final ItemStack held;
    public final boolean keepHeld;
    /** SEQUENCED: one loop of steps and, per step, the deployer item (empty for presses). */
    public final List<Op> steps;
    public final List<ItemStack> stepItems;
    public final int loops;
    public final ItemStack transitional;
    /** Primary output per craft and its chance (1 unless the recipe rolls its result). */
    public final ItemStack output;
    public final double chance;

    CraftPattern(UUID id, Kind kind, ResourceLocation recipeId, List<ItemStack> grid, ItemStack input, Op op,
                 ItemStack held, boolean keepHeld, List<Op> steps, List<ItemStack> stepItems, int loops,
                 ItemStack transitional, ItemStack output, double chance) {
        this.id = id;
        this.kind = kind;
        this.recipeId = recipeId;
        this.grid = List.copyOf(grid);
        this.input = input;
        this.op = op;
        this.held = held;
        this.keepHeld = keepHeld;
        this.steps = List.copyOf(steps);
        this.stepItems = List.copyOf(stepItems);
        this.loops = loops;
        this.transitional = transitional;
        this.output = output;
        this.chance = chance;
    }

    public ItemStackKey outputKey() {
        return ItemStackKey.of(output);
    }

    /** Expected primary output per craft (output count x chance). */
    public double yield() {
        return output.getCount() * chance;
    }

    /** Items consumed by one craft, merged by key. */
    public Map<ItemStackKey, Long> inputsPerCraft() {
        Map<ItemStackKey, Long> in = new LinkedHashMap<>();
        switch (kind) {
            case CRAFTING -> grid.forEach(s -> add(in, s, 1));
            case PROCESSING -> {
                add(in, input, input.getCount());
                if (op == Op.DEPLOY && !keepHeld) add(in, held, 1);
                if (station(op)) grid.forEach(stack -> add(in, stack, stack.getCount()));
            }
            case SEQUENCED -> {
                add(in, input, 1);
                for (ItemStack item : stepItems) add(in, item, loops);
            }
        }
        return in;
    }

    private static void add(Map<ItemStackKey, Long> map, ItemStack stack, long count) {
        if (!stack.isEmpty() && count > 0) map.merge(ItemStackKey.of(stack), count, Long::sum);
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Kind", kind.name());
        tag.putString("Recipe", recipeId.toString());
        tag.put("Grid", stacks(grid, registries));
        tag.put("Input", stack(input, registries));
        if (op != null) tag.putString("Op", op.name());
        tag.put("Held", stack(held, registries));
        tag.putBoolean("KeepHeld", keepHeld);
        ListTag stepList = new ListTag();
        for (Op o : steps) {
            CompoundTag s = new CompoundTag();
            s.putString("Op", o.name());
            stepList.add(s);
        }
        tag.put("Steps", stepList);
        tag.put("StepItems", stacks(stepItems, registries));
        tag.putInt("Loops", loops);
        tag.put("Transitional", stack(transitional, registries));
        tag.put("Output", stack(output, registries));
        tag.putDouble("Chance", chance);
        return tag;
    }

    public static CraftPattern load(CompoundTag tag, HolderLookup.Provider registries) {
        List<Op> steps = new ArrayList<>();
        for (Tag t : tag.getList("Steps", Tag.TAG_COMPOUND)) steps.add(Op.valueOf(((CompoundTag) t).getString("Op")));
        return new CraftPattern(tag.getUUID("Id"), Kind.valueOf(tag.getString("Kind")),
                ResourceLocation.parse(tag.getString("Recipe")),
                readStacks(tag.getList("Grid", Tag.TAG_COMPOUND), registries),
                read(tag.getCompound("Input"), registries),
                tag.contains("Op") ? Op.valueOf(tag.getString("Op")) : null,
                read(tag.getCompound("Held"), registries), tag.getBoolean("KeepHeld"),
                steps, readStacks(tag.getList("StepItems", Tag.TAG_COMPOUND), registries), tag.getInt("Loops"),
                read(tag.getCompound("Transitional"), registries),
                read(tag.getCompound("Output"), registries), tag.getDouble("Chance"));
    }

    private static CompoundTag stack(ItemStack s, HolderLookup.Provider registries) {
        return s.isEmpty() ? new CompoundTag() : (CompoundTag) s.save(registries);
    }

    private static ItemStack read(CompoundTag tag, HolderLookup.Provider registries) {
        return ItemStack.parseOptional(registries, tag);
    }

    private static ListTag stacks(List<ItemStack> list, HolderLookup.Provider registries) {
        ListTag out = new ListTag();
        for (ItemStack s : list) out.add(stack(s, registries));
        return out;
    }

    private static List<ItemStack> readStacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        for (Tag t : list) out.add(read((CompoundTag) t, registries));
        return out;
    }
}
