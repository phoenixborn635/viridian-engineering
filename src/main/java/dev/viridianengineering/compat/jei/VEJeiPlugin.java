package dev.viridianengineering.compat.jei;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.corporea.CorporeaTerminalMenu;
import dev.viridianengineering.network.VENetwork;
import dev.viridianengineering.registry.VECorporea;
import com.simibubi.create.AllBlocks;
import dev.viridianengineering.compat.InfusionDisplay;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import dev.viridianengineering.registry.VEItems;
import dev.viridianengineering.registry.VERecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Spec 3.7 JEI pattern registration: with a Corporea terminal open, JEI's "+" button on any recipe
 * records it as an autocrafting pattern (the server validates and resolves it; unsupported recipes
 * get a chat-bar message).
 */
@JeiPlugin
public class VEJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ViridianEngineering.asResource("jei");
    }

    private static final RecipeType<RecipeHolder<ManaInfusionRecipe>> INFUSION =
            RecipeType.createRecipeHolderType(ViridianEngineering.asResource("mana_infusion"));

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new InfusionCategory(gui.createDrawableItemStack(VEItems.ALFHEIM_INFUSION_MODULE.asStack()),
                gui.getSlotDrawable()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) registration.addRecipes(INFUSION,
                level.getRecipeManager().getAllRecipesFor(VERecipes.MANA_INFUSION_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(INFUSION, VEItems.ALFHEIM_INFUSION_MODULE.get(),
                AllBlocks.MECHANICAL_PRESS.get(), AllBlocks.MECHANICAL_MIXER.get(), AllBlocks.BASIN.get());
    }

    private record InfusionCategory(IDrawable icon, IDrawable slot) implements IRecipeCategory<RecipeHolder<ManaInfusionRecipe>> {
        @Override public RecipeType<RecipeHolder<ManaInfusionRecipe>> getRecipeType() { return INFUSION; }
        @Override public Component getTitle() { return Component.translatable(InfusionDisplay.CATEGORY); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 174; }
        @Override public int getHeight() { return 116; }
        @Override public ResourceLocation getRegistryName(RecipeHolder<ManaInfusionRecipe> recipe) { return recipe.id(); }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ManaInfusionRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            var equipment = InfusionDisplay.equipment(recipe);
            for (int i = 0; i < equipment.size(); i++)
                builder.addSlot(RecipeIngredientRole.CATALYST, 4 + i * 19, 2).setBackground(slot, -1, -1)
                        .addItemStack(equipment.get(i));
            var ingredients = recipe.getIngredients();
            for (int i = 0; i < ingredients.size(); i++)
                builder.addInputSlot(4 + (i % 5) * 19, 24 + (i / 5) * 19).setBackground(slot, -1, -1)
                        .addIngredients(ingredients.get(i));
            builder.addOutputSlot(133, 33).setBackground(slot, -1, -1).addItemStack(recipe.getOutput());
        }

        @Override
        public void draw(RecipeHolder<ManaInfusionRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics,
                         double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, "→", 110, 36, 0x444444, false);
            int y = 63;
            for (var line : InfusionDisplay.requirements(holder.value())) {
                for (var part : font.split(line, getWidth() - 8)) {
                    graphics.drawString(font, part, 4, y, 0x444444, false);
                    y += 10;
                }
            }
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new PatternTransfer(registration.getTransferHelper()));
    }

    private record PatternTransfer(IRecipeTransferHandlerHelper helper) implements IUniversalRecipeTransferHandler<CorporeaTerminalMenu> {
        @Override public Class<? extends CorporeaTerminalMenu> getContainerClass() { return CorporeaTerminalMenu.class; }

        @Override public Optional<MenuType<CorporeaTerminalMenu>> getMenuType() { return Optional.of(VECorporea.TERMINAL_MENU.get()); }

        @Override
        public IRecipeTransferError transferRecipe(CorporeaTerminalMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
                                                   boolean maxTransfer, boolean doTransfer) {
            if (!(recipe instanceof RecipeHolder<?> holder))
                return helper.createUserErrorWithTooltip(Component.translatable("message.viridian_engineering.pattern.unsupported", "?"));
            if (doTransfer)
                PacketDistributor.sendToServer(new VENetwork.TerminalAction(menu.containerId,
                        VENetwork.Action.RECORD_RECIPE.ordinal(), 0, 0, false, holder.id().toString()));
            return null;
        }
    }
}
