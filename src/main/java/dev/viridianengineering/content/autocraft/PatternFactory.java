package dev.viridianengineering.content.autocraft;

import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a recipe (from the terminal grid, a JEI/EMI transfer or a test) into a {@link CraftPattern}.
 * Tag ingredients become one concrete item: the first option the network already stocks, otherwise
 * the first option. Fluids are supplied to the machine's own tank.
 */
public final class PatternFactory {

    private PatternFactory() {}

    public record Result(CraftPattern pattern, Component error) {
        static Result ok(CraftPattern p) { return new Result(p, null); }
        static Result fail(String key, Object... args) { return new Result(null, Component.translatable("message.viridian_engineering.pattern." + key, args)); }
        public boolean ok() { return pattern != null; }
    }

    /** {@code stocked}: items the network holds (preferred when a tag has several options). */
    public static Result fromRecipe(ServerLevel level, RecipeHolder<?> holder, Set<Item> stocked) {
        return fromRecipe(level, holder, stocked, null);
    }

    /** {@code grid}: optional 3x3 contents to use verbatim for a crafting recipe (ghost-grid recording). */
    public static Result fromRecipe(ServerLevel level, RecipeHolder<?> holder, Set<Item> stocked, List<ItemStack> grid) {
        ResourceLocation id = holder.id();
        var registries = level.registryAccess();
        var recipe = holder.value();

        if (recipe instanceof MechanicalCraftingRecipe mechanical) {
            List<ItemStack> inputs = mechanical.getIngredients().stream()
                    .map(ingredient -> ingredient.isEmpty() ? ItemStack.EMPTY : choose(ingredient, stocked)).toList();
            return processing(holder, CraftPattern.Op.MECHANICAL_CRAFT, inputs, ItemStack.EMPTY,
                    mechanical.getResultItem(registries), 1);
        }

        if (recipe instanceof CraftingRecipe crafting) {
            List<ItemStack> slots = grid != null ? copyGrid(grid) : layout(crafting, stocked);
            if (slots == null) return Result.fail("unsupported", id.toString());
            CraftingInput in = CraftingInput.of(3, 3, slots);
            if (!crafting.matches(in, level)) return Result.fail("no_match", id.toString());
            ItemStack out = crafting.assemble(in, registries);
            if (out.isEmpty()) return Result.fail("no_output", id.toString());
            return Result.ok(new CraftPattern(UUID.randomUUID(), CraftPattern.Kind.CRAFTING, id, slots, ItemStack.EMPTY,
                    null, ItemStack.EMPTY, false, List.of(), List.of(), 0, ItemStack.EMPTY, out, 1.0));
        }

        if (recipe instanceof SequencedAssemblyRecipe sequenced) {
            List<CraftPattern.Op> steps = new ArrayList<>();
            List<ItemStack> items = new ArrayList<>();
            for (SequencedRecipe<?> step : sequenced.getSequence()) {
                var r = step.getRecipe();
                if (r instanceof DeployerApplicationRecipe deploy) {
                    steps.add(CraftPattern.Op.DEPLOY);
                    items.add(choose(deploy.getRequiredHeldItem(), stocked));
                } else if (r instanceof PressingRecipe) {
                    steps.add(CraftPattern.Op.PRESS);
                    items.add(ItemStack.EMPTY);
                } else if (r instanceof FillingRecipe) {
                    steps.add(CraftPattern.Op.FILL);
                    items.add(ItemStack.EMPTY);
                } else {
                    return Result.fail("unsupported_step", id.toString());
                }
            }
            ItemStack base = choose(sequenced.getIngredient(), stocked);
            ItemStack out = sequenced.getResultItem(registries).copy();
            if (base.isEmpty() || out.isEmpty()) return Result.fail("no_output", id.toString());
            return Result.ok(new CraftPattern(UUID.randomUUID(), CraftPattern.Kind.SEQUENCED, id, List.of(),
                    base.copyWithCount(1), null, ItemStack.EMPTY, false, steps, items, sequenced.getLoops(),
                    sequenced.getTransitionalItem().copyWithCount(1), out, sequenced.getOutputChance()));
        }

        if (recipe instanceof PressingRecipe pressing) {
            if (!pressing.getFluidIngredients().isEmpty()) return Result.fail("fluids", id.toString());
            ItemStack in = choose(pressing.getIngredients().get(0), stocked);
            ProcessingOutput first = pressing.getRollableResults().get(0);
            return Result.ok(new CraftPattern(UUID.randomUUID(), CraftPattern.Kind.PROCESSING, id, List.of(),
                    in.copyWithCount(1), CraftPattern.Op.PRESS, ItemStack.EMPTY, false, List.of(), List.of(), 0,
                    ItemStack.EMPTY, first.getStack().copy(), first.getChance()));
        }

        if (recipe instanceof DeployerApplicationRecipe deploy) {
            ItemStack target = choose(deploy.getProcessedItem(), stocked);
            ItemStack held = choose(deploy.getRequiredHeldItem(), stocked);
            ProcessingOutput first = deploy.getRollableResults().get(0);
            return Result.ok(new CraftPattern(UUID.randomUUID(), CraftPattern.Kind.PROCESSING, id, List.of(),
                    target.copyWithCount(1), CraftPattern.Op.DEPLOY, held.copyWithCount(1), deploy.shouldKeepHeldItem(),
                    List.of(), List.of(), 0, ItemStack.EMPTY, first.getStack().copy(), first.getChance()));
        }

        if (recipe instanceof MixingRecipe || recipe instanceof ManaInfusionRecipe infusion && infusion.mixesInBasin()) {
            List<ItemStack> inputs = recipe.getIngredients().stream().map(i -> choose(i, stocked)).toList();
            if (inputs.isEmpty() || inputs.stream().anyMatch(ItemStack::isEmpty)) return Result.fail("unsupported", id.toString());
            if (recipe instanceof MixingRecipe mixing && !mixing.getRollableResults().isEmpty()) {
                ProcessingOutput out = mixing.getRollableResults().getFirst();
                return processing(holder, CraftPattern.Op.MIX, inputs, ItemStack.EMPTY, out.getStack(), out.getChance());
            }
            return processing(holder, CraftPattern.Op.MIX, inputs, ItemStack.EMPTY, recipe.getResultItem(registries), 1);
        }

        if (recipe instanceof FillingRecipe filling && !filling.getIngredients().isEmpty() && !filling.getRollableResults().isEmpty()) {
            ProcessingOutput out = filling.getRollableResults().getFirst();
            return processing(holder, CraftPattern.Op.FILL, List.of(), choose(filling.getIngredients().getFirst(), stocked),
                    out.getStack(), out.getChance());
        }

        if (recipe instanceof CuttingRecipe cutting && !cutting.getIngredients().isEmpty() && !cutting.getRollableResults().isEmpty()) {
            ProcessingOutput out = cutting.getRollableResults().getFirst();
            return processing(holder, CraftPattern.Op.CUT, List.of(choose(cutting.getIngredients().getFirst(), stocked)),
                    ItemStack.EMPTY, out.getStack(), out.getChance());
        }

        if (recipe instanceof net.minecraft.world.item.crafting.StonecutterRecipe cutting) {
            return processing(holder, CraftPattern.Op.CUT, List.of(choose(cutting.getIngredients().getFirst(), stocked)),
                    ItemStack.EMPTY, cutting.getResultItem(registries), 1);
        }

        return Result.fail("unsupported", id.toString());
    }

    private static Result processing(RecipeHolder<?> holder, CraftPattern.Op op, List<ItemStack> grid, ItemStack input,
                                     ItemStack output, double chance) {
        if (output.isEmpty() || chance <= 0 || (op == CraftPattern.Op.FILL && input.isEmpty())
                || (op == CraftPattern.Op.CUT && (grid.isEmpty() || grid.getFirst().isEmpty())))
            return Result.fail("no_output", holder.id().toString());
        return Result.ok(new CraftPattern(UUID.randomUUID(), CraftPattern.Kind.PROCESSING, holder.id(), grid,
                input, op, ItemStack.EMPTY, false, List.of(), List.of(), 0, ItemStack.EMPTY, output.copy(), chance));
    }

    private static List<ItemStack> copyGrid(List<ItemStack> grid) {
        if (grid.size() != 9) return null;
        List<ItemStack> out = new ArrayList<>(9);
        for (ItemStack s : grid) out.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
        return out;
    }

    /** Lays a crafting recipe's ingredients into a 3x3 grid. */
    private static List<ItemStack> layout(CraftingRecipe recipe, Set<Item> stocked) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        if (ingredients.size() > 9 || width > 3) return null;
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            if (ing.isEmpty()) continue;
            int slot = recipe instanceof ShapedRecipe ? (i % width) + (i / width) * 3 : i;
            grid.set(slot, choose(ing, stocked));
        }
        return grid;
    }

    static ItemStack choose(Ingredient ingredient, Set<Item> stocked) {
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) return ItemStack.EMPTY;
        for (ItemStack option : options)
            if (stocked.contains(option.getItem())) return option.copyWithCount(1);
        return options[0].copyWithCount(1);
    }
}
