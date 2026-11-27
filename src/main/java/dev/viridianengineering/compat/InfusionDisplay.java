package dev.viridianengineering.compat;

import com.simibubi.create.AllBlocks;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import dev.viridianengineering.registry.VEItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The upgraded equipment and requirements shown by both recipe viewers. */
public final class InfusionDisplay {
    public static final String CATEGORY = "recipe.viridian_engineering.mana_infusion";

    private InfusionDisplay() {}

    public static List<ItemStack> equipment(ManaInfusionRecipe recipe) {
        var module = VEItems.ALFHEIM_INFUSION_MODULE.asStack();
        return recipe.mixesInBasin()
                ? List.of(AllBlocks.MECHANICAL_MIXER.asStack(), AllBlocks.BASIN.asStack(), module)
                : List.of(AllBlocks.MECHANICAL_PRESS.asStack(), module);
    }

    public static List<Component> requirements(ManaInfusionRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(CATEGORY + ".mana", recipe.getMana()));
        lines.add(recipe.getMinRpm() > 0 ? Component.translatable(CATEGORY + ".rpm", recipe.getMinRpm())
                : Component.translatable(CATEGORY + ".rotation"));
        if (recipe.isTerrasteelAlloy()) lines.add(Component.translatable(CATEGORY + ".advancement"));
        return lines;
    }
}
