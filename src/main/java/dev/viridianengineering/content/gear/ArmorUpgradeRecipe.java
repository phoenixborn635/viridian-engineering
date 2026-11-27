package dev.viridianengineering.content.gear;

import com.mojang.serialization.MapCodec;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import velrondevs.botania.registry.BotaniaItems;

/**
 * {@code viridian_engineering:armor_upgrade}: a shapeless crafting recipe (same JSON as
 * {@code minecraft:crafting_shapeless}) whose result keeps everything on the armor piece it upgrades.
 * <ul>
 *   <li>The first armor piece among the ingredients is the base: its enchantments, damage, name and
 *       custom data are copied onto the result (Terrasteel piece to Sylvan piece; spec "inherits all
 *       existing enchantments").</li>
 *   <li>If a Flugel Tiara is among the ingredients, its wing variant and flight state go onto the
 *       helmet ({@code hasFlugelTiara} / {@code tiaraVariant}, spec LOCKED). A helmet that already has
 *       a tiara doesn't match.</li>
 * </ul>
 * The Smithing Table route needs nothing extra: vanilla {@code smithing_transform} already keeps the
 * base's components.
 */
public class ArmorUpgradeRecipe extends ShapelessRecipe {

    public ArmorUpgradeRecipe(String group, net.minecraft.world.item.crafting.CraftingBookCategory category,
                              ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    public ArmorUpgradeRecipe(ShapelessRecipe r) {
        this(r.getGroup(), r.category(), r.getResultItem(null), r.getIngredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) return false;
        ItemStack base = base(input);
        return !(tiara(input) != null && base != null && GearData.hasTiara(base));
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = super.assemble(input, registries);
        ItemStack base = base(input);
        if (base != null) out.applyComponents(base.getComponentsPatch());
        ItemStack tiara = tiara(input);
        if (tiara != null) GearData.setTiara(out, GearData.read(tiara));
        return out;
    }

    private static ItemStack base(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.getItem() instanceof ArmorItem) return s;
        }
        return null;
    }

    private static ItemStack tiara(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.is(BotaniaItems.flightTiara)) return s;
        }
        return null;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return VERecipes.ARMOR_UPGRADE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<ArmorUpgradeRecipe> {
        @SuppressWarnings("unchecked")
        private static final MapCodec<ArmorUpgradeRecipe> CODEC =
                ((MapCodec<ShapelessRecipe>) RecipeSerializer.SHAPELESS_RECIPE.codec())
                        .xmap(ArmorUpgradeRecipe::new, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, ArmorUpgradeRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(ArmorUpgradeRecipe::new, r -> r);

        @Override public MapCodec<ArmorUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ArmorUpgradeRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
