package dev.viridianengineering.content.autocraft;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.viridianengineering.content.infusion.AlfheimMixerHandler;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import dev.viridianengineering.content.drive.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Dispatches exact item batches to native Create machines. Fluid is supplied through their normal plumbing. */
final class CraftingStations {
    private CraftingStations() {}

    private record Insert(IItemHandler handler, int slot, ItemStack stack) {}
    private record Cell(MechanicalCrafterBlockEntity crafter, int x, int y) {}

    static boolean feed(ServerLevel level, BlockPos target, CraftingJob job, CraftingJob.StepState step) {
        var holder = level.getRecipeManager().byKey(step.pattern.recipeId).orElse(null);
        if (holder == null || !job.escrowHas(step.pattern.inputsPerCraft(), 1)) return false;
        Recipe<?> recipe = holder.value();
        List<Insert> transfers = switch (step.pattern.op) {
            case MIX -> mixerSlots(level, target, step.pattern, recipe);
            case CUT -> sawSlots(level, target, step.pattern);
            case MECHANICAL_CRAFT -> crafterSlots(level, target, step.pattern, recipe);
            default -> List.of();
        };
        if (transfers.isEmpty()) return false;
        if (step.pattern.op == CraftPattern.Op.MECHANICAL_CRAFT) {
            for (Insert transfer : transfers) {
                if (!transfer.handler().insertItem(transfer.slot(), transfer.stack().copy(), true).isEmpty()) return false;
            }
        }
        List<Insert> inserted = new ArrayList<>();
        for (Insert transfer : transfers) {
            ItemStack rest = transfer.handler().insertItem(transfer.slot(), transfer.stack().copy(), false);
            int count = transfer.stack().getCount() - rest.getCount();
            if (count > 0) inserted.add(new Insert(transfer.handler(), transfer.slot(), transfer.stack().copyWithCount(count)));
            if (!rest.isEmpty()) {
                // Create's input handlers run on this thread. Restore any accepted cells on a partial insertion.
                for (Insert rollback : inserted) {
                    int removed = rollback.handler().extractItem(rollback.slot(), rollback.stack().getCount(), false).getCount();
                    job.escrowTake(ItemStackKey.of(rollback.stack()), rollback.stack().getCount() - removed);
                }
                return false;
            }
        }
        step.pattern.inputsPerCraft().forEach(job::escrowTake);
        return true;
    }

    private static List<Insert> mixerSlots(ServerLevel level, BlockPos target, CraftPattern pattern, Recipe<?> recipe) {
        if (!(level.getBlockEntity(target) instanceof BasinBlockEntity basin)
                || !(level.getBlockEntity(target.above(2)) instanceof MechanicalMixerBlockEntity mixer)
                || mixer.getSpeed() == 0 || mixer.running || !mixer.isSpeedRequirementFulfilled()
                || !basin.getInputInventory().isEmpty() || !basin.getOutputInventory().isEmpty()
                || basin.getFilter() == null || !basin.getFilter().test(pattern.output)) return List.of();
        if (recipe instanceof ManaInfusionRecipe infusion && !AlfheimMixerHandler.canProcess(mixer, infusion)) return List.of();
        if (recipe instanceof BasinRecipe basinRecipe && (!basinRecipe.getRequiredHeat().testBlazeBurner(
                BasinBlockEntity.getHeatLevelOf(level.getBlockState(target.below())))
                || !hasFluids(level, target, basinRecipe.getFluidIngredients()))) return List.of();
        List<Insert> slots = new ArrayList<>();
        var inventory = basin.getInputInventory();
        var ingredients = new LinkedHashMap<>(pattern.inputsPerCraft());
        if (ingredients.size() > inventory.getSlots()) return List.of();
        int slot = 0;
        for (var ingredient : ingredients.entrySet()) {
            ItemStack stack = ingredient.getKey().toStack(ingredient.getValue().intValue());
            if (stack.getCount() > Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize())) return List.of();
            slots.add(new Insert(inventory, slot++, stack));
        }
        return slots;
    }

    private static List<Insert> sawSlots(ServerLevel level, BlockPos target, CraftPattern pattern) {
        if (!(level.getBlockEntity(target) instanceof SawBlockEntity saw) || saw.getSpeed() == 0
                || !saw.inventory.isEmpty()) return List.of();
        var filter = saw.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
        if (filter != null && !filter.test(pattern.output)) return List.of();
        return List.of(new Insert(saw.inventory, 0, pattern.grid.getFirst()));
    }

    private static List<Insert> crafterSlots(ServerLevel level, BlockPos target, CraftPattern pattern, Recipe<?> recipe) {
        if (!(recipe instanceof MechanicalCraftingRecipe mechanical)
                || !(level.getBlockEntity(target) instanceof MechanicalCrafterBlockEntity root)) return List.of();
        List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(root);
        if (chain == null || chain.isEmpty()) return List.of();
        List<Cell> cells = new ArrayList<>();
        for (MechanicalCrafterBlockEntity crafter : chain) {
            if (crafter.getSpeed() == 0 || !crafter.getInventory().isEmpty()) return List.of();
            // A covered empty crafter intentionally contributes a gap.
            if (crafter.craftingItemOrCoverPresent()) continue;
            Cell cell = locate(crafter, root, chain.size());
            if (cell == null) return List.of();
            cells.add(cell);
        }
        if (cells.isEmpty()) return List.of();
        int minX = cells.stream().mapToInt(Cell::x).min().orElseThrow();
        int maxY = cells.stream().mapToInt(Cell::y).max().orElseThrow();
        List<Insert> slots = new ArrayList<>();
        int width = mechanical.getWidth();
        for (Cell cell : cells) {
            int x = cell.x() - minX, y = maxY - cell.y();
            if (x >= width || y >= mechanical.getHeight()) return List.of();
            ItemStack ingredient = pattern.grid.get(y * width + x);
            if (ingredient.isEmpty()) return List.of();
            slots.add(new Insert(cell.crafter().getInventory(), 0, ingredient));
        }
        if (slots.size() != pattern.grid.stream().filter(stack -> !stack.isEmpty()).count()) return List.of();
        return slots;
    }

    private static Cell locate(MechanicalCrafterBlockEntity crafter, MechanicalCrafterBlockEntity root, int maxHops) {
        int x = 0, y = 0;
        MechanicalCrafterBlockEntity cursor = crafter;
        while (cursor != root && cursor != null && maxHops-- > 0) {
            switch (cursor.getBlockState().getValue(MechanicalCrafterBlock.POINTING)) {
                case LEFT -> x++;
                case RIGHT -> x--;
                case DOWN -> y++;
                case UP -> y--;
            }
            cursor = RecipeGridHandler.getTargetingCrafter(cursor);
        }
        return cursor == root ? new Cell(crafter, x, y) : null;
    }

    static boolean hasFluids(ServerLevel level, BlockPos machine, List<SizedFluidIngredient> ingredients) {
        if (ingredients.isEmpty()) return true;
        IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, machine, Direction.UP);
        if (tank == null) return false;
        int[] reserved = new int[tank.getTanks()];
        for (SizedFluidIngredient ingredient : ingredients) {
            int needed = ingredient.amount();
            for (int i = 0; i < tank.getTanks() && needed > 0; i++) {
                var fluid = tank.getFluidInTank(i);
                if (!ingredient.ingredient().test(fluid)) continue;
                int take = Math.min(needed, Math.max(0, fluid.getAmount() - reserved[i]));
                reserved[i] += take;
                needed -= take;
            }
            if (needed > 0) return false;
        }
        return true;
    }

    static boolean collect(ServerLevel level, BlockPos target, CraftingJob job, CraftingJob.StepState step) {
        if (step.pattern.op == CraftPattern.Op.MIX && level.getBlockEntity(target) instanceof BasinBlockEntity basin) {
            if (!basin.getInputInventory().isEmpty()) return false;
            collectSlots(job, basin.getOutputInventory());
            return true;
        }
        if (step.pattern.op == CraftPattern.Op.CUT && level.getBlockEntity(target) instanceof SawBlockEntity saw
                && saw.inventory.appliedRecipe) {
            collectSaw(job, saw);
            return true;
        }
        return false;
    }

    static boolean recover(ServerLevel level, BlockPos target, CraftingJob job, CraftPattern.Op operation) {
        if (operation == CraftPattern.Op.MIX && level.getBlockEntity(target) instanceof BasinBlockEntity basin) {
            collectSlots(job, basin.getOutputInventory());
            returnInputs(job, basin.getInputInventory());
            return true;
        }
        if (operation == CraftPattern.Op.CUT && level.getBlockEntity(target) instanceof SawBlockEntity saw) {
            if (saw.inventory.appliedRecipe) collectSaw(job, saw);
            else returnInputs(job, saw.inventory);
            saw.inventory.remainingTime = -1;
            saw.notifyUpdate();
            return true;
        }
        if (operation == CraftPattern.Op.MECHANICAL_CRAFT && level.getBlockEntity(target) instanceof MechanicalCrafterBlockEntity root) {
            List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(root);
            if (chain == null) return false;
            for (var crafter : chain) ((ManagedCrafter) crafter).ve$takePendingItems().forEach(job::escrowAdd);
            root.ejectWholeGrid();
            return true;
        }
        return false;
    }

    private static void collectSaw(CraftingJob job, SawBlockEntity saw) {
        for (int i = 0; i < saw.inventory.getSlots(); i++) collectResult(job, saw.inventory.getStackInSlot(i));
        saw.inventory.clear();
        saw.inventory.remainingTime = -1;
        saw.notifyUpdate();
    }

    private static void returnInputs(CraftingJob job, IItemHandlerModifiable input) {
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            job.escrowAdd(stack);
            input.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    private static void collectSlots(CraftingJob job, IItemHandler inventory) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) collectResult(job, inventory.extractItem(i, stack.getCount(), false));
        }
    }

    static void collectResult(CraftingJob job, ItemStack stack) {
        if (stack.isEmpty()) return;
        job.escrowAdd(stack);
        job.collected.merge(ItemStackKey.of(stack), (long) stack.getCount(), Long::sum);
    }
}
