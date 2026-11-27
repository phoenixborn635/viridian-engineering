package dev.viridianengineering.mixin;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import dev.viridianengineering.content.autocraft.ManagedCrafter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
abstract class MechanicalCrafterJobMixin implements ManagedCrafter {
    @Shadow protected GroupedItems groupedItems;

    @Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
    private void ve$collectOutput(CallbackInfo ci) {
        var crafter = (MechanicalCrafterBlockEntity) (Object) this;
        if (!(crafter.getLevel() instanceof ServerLevel level) || groupedItems.onlyEmptyItems()) return;
        if (!CraftingScheduler.isClaimedMachine(level, crafter.getBlockPos())) return;
        if (!CraftingScheduler.collectCrafterOutputs(level, crafter.getBlockPos(), ve$groupContents(crafter))) return;
        groupedItems = new GroupedItems();
        crafter.ejectWholeGrid();
        ci.cancel();
    }

    @Override
    public List<ItemStack> ve$takePendingItems() {
        var crafter = (MechanicalCrafterBlockEntity) (Object) this;
        var items = ve$groupContents(crafter);
        var stack = crafter.getInventory().getStackInSlot(0);
        if (!stack.isEmpty()) items.add(stack.copy());
        groupedItems = new GroupedItems();
        crafter.getInventory().setStackInSlot(0, ItemStack.EMPTY);
        return items;
    }

    @Unique
    private List<ItemStack> ve$groupContents(MechanicalCrafterBlockEntity crafter) {
        var registries = crafter.getLevel().registryAccess();
        var tag = new CompoundTag();
        groupedItems.write(tag, registries);
        var items = new ArrayList<ItemStack>();
        for (Tag cell : tag.getList("Grid", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.parseOptional(registries, ((CompoundTag) cell).getCompound("item"));
            if (!stack.isEmpty()) items.add(stack);
        }
        return items;
    }
}
