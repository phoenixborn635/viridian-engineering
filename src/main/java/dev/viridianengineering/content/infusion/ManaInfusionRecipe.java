package dev.viridianengineering.content.infusion;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import velrondevs.botania.registry.BotaniaItems;

import java.util.List;

/**
 * An Alfheim-upgraded press accepts a single `input`. An upgraded Mixer and Basin use
 * an unordered list of `ingredients`. Both use the machine's normal rotational stress.
 * <pre>
 * { "type": "viridian_engineering:mana_infusion",
 *   "input": { "item": "minecraft:iron_ingot" },
 *   "mana": 3000,
 *   "output": { "id": "botania:manasteel_ingot", "count": 1 } }
 * </pre>
 */
public class ManaInfusionRecipe implements Recipe<SingleRecipeInput> {

    public static final ResourceLocation TERRASTEEL_WEAPON =
            ResourceLocation.fromNamespaceAndPath("botania", "main/terrasteel_weapon_craft");
    public static final int TERRASTEEL_MANA = 350_000;
    public static final int TERRASTEEL_RPM = 256;

    private final Either<Ingredient, List<Ingredient>> inputs;
    private final ItemStack output;
    private final int mana;
    private final int minRpm;

    public ManaInfusionRecipe(Ingredient input, ItemStack output, int mana) {
        this(Either.left(input), output, mana, 0);
    }

    private ManaInfusionRecipe(Either<Ingredient, List<Ingredient>> inputs, ItemStack output, int mana, int minRpm) {
        this.inputs = inputs;
        this.output = output;
        this.mana = mana;
        this.minRpm = minRpm;
    }

    public ItemStack getOutput() { return output; }

    public int getMana() { return isTerrasteelAlloy() ? TERRASTEEL_MANA : mana; }

    public int getMinRpm() { return isTerrasteelAlloy() ? TERRASTEEL_RPM : minRpm; }

    public boolean mixesInBasin() { return inputs.right().isPresent(); }

    public boolean isTerrasteelAlloy() { return output.is(BotaniaItems.terrasteel); }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        inputs.ifLeft(ingredients::add).ifRight(ingredients::addAll);
        return ingredients;
    }

    @Override
    public boolean matches(SingleRecipeInput in, Level level) {
        return !isTerrasteelAlloy() && inputs.left().filter(ingredient -> ingredient.test(in.item())).isPresent();
    }

    @Override
    public ItemStack assemble(SingleRecipeInput in, HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return output;
    }

    @Override
    public boolean isSpecial() {
        return true; // not a crafting-book recipe
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return VERecipes.MANA_INFUSION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return VERecipes.MANA_INFUSION_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<ManaInfusionRecipe> {

        public static final MapCodec<ManaInfusionRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.mapEither(Ingredient.CODEC_NONEMPTY.fieldOf("input"),
                        Ingredient.CODEC_NONEMPTY.listOf(2, 9).fieldOf("ingredients")).forGetter(recipe -> recipe.inputs),
                ItemStack.STRICT_CODEC.fieldOf("output").forGetter(ManaInfusionRecipe::getOutput),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("mana").forGetter(recipe -> recipe.mana),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("min_rpm", 0).forGetter(recipe -> recipe.minRpm)
        ).apply(i, ManaInfusionRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ManaInfusionRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.either(Ingredient.CONTENTS_STREAM_CODEC,
                        Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(9))), recipe -> recipe.inputs,
                ItemStack.STREAM_CODEC, ManaInfusionRecipe::getOutput,
                ByteBufCodecs.VAR_INT, recipe -> recipe.mana,
                ByteBufCodecs.VAR_INT, recipe -> recipe.minRpm,
                ManaInfusionRecipe::new);

        @Override
        public MapCodec<ManaInfusionRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ManaInfusionRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
