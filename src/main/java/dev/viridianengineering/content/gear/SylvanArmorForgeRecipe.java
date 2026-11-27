package dev.viridianengineering.content.gear;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.foundation.mixin.accessor.ShapedRecipeAccessor;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** A Create Mechanical Crafting recipe that carries the base armor's player-facing components forward. */
public final class SylvanArmorForgeRecipe extends MechanicalCraftingRecipe {
    private SylvanArmorForgeRecipe(MechanicalCraftingRecipe recipe) {
        super(recipe.getGroup(), recipe.category(), ((ShapedRecipeAccessor) recipe).create$getPattern(),
                recipe.getResultItem(null), recipe.acceptsMirrored());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        if (!(result.getItem() instanceof ArmorItem output)) return result;
        for (ItemStack stack : input.items()) {
            if (!(stack.getItem() instanceof ArmorItem base) || base.getType() != output.getType()) continue;
            copy(stack, result, DataComponents.ENCHANTMENTS);
            copy(stack, result, DataComponents.TRIM);
            copy(stack, result, DataComponents.CUSTOM_NAME);
            break;
        }
        return result;
    }

    private static <T> void copy(ItemStack from, ItemStack to, DataComponentType<T> type) {
        T value = from.get(type);
        if (value != null) to.set(type, value);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return VERecipes.SYLVAN_ARMOR_FORGE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<SylvanArmorForgeRecipe> {
        private static final MapCodec<SylvanArmorForgeRecipe> CODEC =
                MechanicalCraftingRecipe.Serializer.CODEC.xmap(SylvanArmorForgeRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, SylvanArmorForgeRecipe> STREAM_CODEC =
                MechanicalCraftingRecipe.Serializer.STREAM_CODEC.map(SylvanArmorForgeRecipe::new, recipe -> recipe);

        @Override public MapCodec<SylvanArmorForgeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, SylvanArmorForgeRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
