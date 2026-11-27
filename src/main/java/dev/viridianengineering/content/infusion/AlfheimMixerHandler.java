package dev.viridianengineering.content.infusion;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

public final class AlfheimMixerHandler {
    private AlfheimMixerHandler() {}

    public static void unlockIfQualified(MechanicalMixerBlockEntity mixer, ServerPlayer player) {
        var advancement = player.getServer().getAdvancements().get(ManaInfusionRecipe.TERRASTEEL_WEAPON);
        if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        ((IAlfheimMixer) mixer).ve$unlockTerrasteel();
        mixer.setChanged();
        mixer.basinChecker.scheduleUpdate();
    }

    public static void preferInfusion(MechanicalMixerBlockEntity mixer, BasinBlockEntity basin, List<Recipe<?>> matches) {
        for (RecipeHolder<ManaInfusionRecipe> holder : mixer.getLevel().getRecipeManager().getAllRecipesFor(VERecipes.MANA_INFUSION_TYPE.get())) {
            ManaInfusionRecipe recipe = holder.value();
            if (canProcess(mixer, recipe) && BasinRecipe.match(basin, recipe)) {
                matches.addFirst(recipe);
                return;
            }
        }
    }

    public static boolean canProcess(MechanicalMixerBlockEntity mixer, ManaInfusionRecipe recipe) {
        IAlfheimMixer module = (IAlfheimMixer) mixer;
        return recipe.mixesInBasin() && module.ve$hasAlfheimModule()
                && (!recipe.isTerrasteelAlloy() || module.ve$isTerrasteelUnlocked())
                && Math.abs(mixer.getSpeed()) >= recipe.getMinRpm()
                && module.ve$getMana() >= recipe.getMana();
    }
}
