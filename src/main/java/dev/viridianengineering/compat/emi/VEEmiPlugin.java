package dev.viridianengineering.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import com.simibubi.create.AllBlocks;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.InfusionDisplay;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import dev.viridianengineering.registry.VEItems;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.viridianengineering.content.corporea.CorporeaTerminalMenu;
import dev.viridianengineering.network.VENetwork;
import dev.viridianengineering.registry.VECorporea;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Spec 3.7 EMI pattern registration: EMI's fill button records the recipe as a pattern (same packet as JEI). */
@EmiEntrypoint
public class VEEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeHandler(VECorporea.TERMINAL_MENU.get(), new PatternHandler());
        var module = EmiStack.of(VEItems.ALFHEIM_INFUSION_MODULE.asStack());
        var category = new EmiRecipeCategory(ViridianEngineering.asResource("mana_infusion"), module);
        registry.addCategory(category);
        registry.addWorkstation(category, module);
        registry.addWorkstation(category, EmiStack.of(AllBlocks.MECHANICAL_PRESS.asStack()));
        registry.addWorkstation(category, EmiStack.of(AllBlocks.MECHANICAL_MIXER.asStack()));
        registry.addWorkstation(category, EmiStack.of(AllBlocks.BASIN.asStack()));
        for (var holder : registry.getRecipeManager().getAllRecipesFor(VERecipes.MANA_INFUSION_TYPE.get()))
            registry.addRecipe(new InfusionRecipe(category, holder));
    }

    private static final class InfusionRecipe extends BasicEmiRecipe {
        private final RecipeHolder<ManaInfusionRecipe> holder;

        InfusionRecipe(EmiRecipeCategory category, RecipeHolder<ManaInfusionRecipe> holder) {
            super(category, holder.id(), 174, 116);
            this.holder = holder;
            var recipe = holder.value();
            inputs = recipe.getIngredients().stream().map(EmiIngredient::of).toList();
            catalysts = InfusionDisplay.equipment(recipe).stream().<EmiIngredient>map(EmiStack::of).toList();
            outputs = List.of(EmiStack.of(recipe.getOutput()));
        }

        @Override public RecipeHolder<?> getBackingRecipe() { return holder; }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            for (int i = 0; i < catalysts.size(); i++)
                widgets.addSlot(catalysts.get(i), 3 + i * 19, 1).catalyst(true);
            for (int i = 0; i < inputs.size(); i++)
                widgets.addSlot(inputs.get(i), 3 + (i % 5) * 19, 23 + (i / 5) * 19);
            widgets.addFillingArrow(101, 30, 1500);
            widgets.addSlot(outputs.getFirst(), 132, 32).recipeContext(this);
            int y = 63;
            for (var line : InfusionDisplay.requirements(holder.value())) {
                for (var part : Minecraft.getInstance().font.split(line, getDisplayWidth() - 8)) {
                    widgets.addText(part, 4, y, 0x444444, false);
                    y += 10;
                }
            }
        }
    }

    private static final class PatternHandler implements EmiRecipeHandler<CorporeaTerminalMenu> {
        @Override public EmiPlayerInventory getInventory(AbstractContainerScreen<CorporeaTerminalMenu> screen) { return new EmiPlayerInventory(List.of()); }

        @Override public boolean supportsRecipe(EmiRecipe recipe) { return id(recipe) != null; }

        @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<CorporeaTerminalMenu> context) { return true; }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<CorporeaTerminalMenu> context) {
            ResourceLocation id = id(recipe);
            if (id == null) return false;
            PacketDistributor.sendToServer(new VENetwork.TerminalAction(context.getScreenHandler().containerId,
                    VENetwork.Action.RECORD_RECIPE.ordinal(), 0, 0, false, id.toString()));
            return true;
        }

        private static ResourceLocation id(EmiRecipe recipe) {
            var backing = recipe.getBackingRecipe();
            return backing != null ? backing.id() : recipe.getId();
        }
    }
}
