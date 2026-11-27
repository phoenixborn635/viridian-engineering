package dev.viridianengineering.content.autocraft;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface ManagedCrafter {
    List<ItemStack> ve$takePendingItems();
}
