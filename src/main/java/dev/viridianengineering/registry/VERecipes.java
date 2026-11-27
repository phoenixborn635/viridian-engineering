package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.gear.SylvanArmorForgeRecipe;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VERecipes {

    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ViridianEngineering.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ViridianEngineering.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ManaInfusionRecipe>> MANA_INFUSION_TYPE =
            TYPES.register("mana_infusion", () -> RecipeType.simple(ViridianEngineering.asResource("mana_infusion")));

    public static final DeferredHolder<RecipeSerializer<?>, ManaInfusionRecipe.Serializer> MANA_INFUSION_SERIALIZER =
            SERIALIZERS.register("mana_infusion", ManaInfusionRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, SylvanArmorForgeRecipe.Serializer> SYLVAN_ARMOR_FORGE_SERIALIZER =
            SERIALIZERS.register("sylvan_armor_forge", SylvanArmorForgeRecipe.Serializer::new);

    /** Shapeless crafting that keeps the upgraded armor's enchantments and a fitted Flugel Tiara (Sylvan set). */
    public static final DeferredHolder<RecipeSerializer<?>, dev.viridianengineering.content.gear.ArmorUpgradeRecipe.Serializer> ARMOR_UPGRADE_SERIALIZER =
            SERIALIZERS.register("armor_upgrade", dev.viridianengineering.content.gear.ArmorUpgradeRecipe.Serializer::new);

    private VERecipes() {}

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }
}
