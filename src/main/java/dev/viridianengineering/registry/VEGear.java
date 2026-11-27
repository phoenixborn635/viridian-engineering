package dev.viridianengineering.registry;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.gear.GaiaKineticLanceItem;
import dev.viridianengineering.content.gear.GearData;
import dev.viridianengineering.content.gear.SylvanArmorItem;
import dev.viridianengineering.content.gear.SylvanModuleItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

import static dev.viridianengineering.ViridianEngineering.REGISTRATE;

/**
 * Viridian spec Phase 5a (Tier 5 gaia gear): the intermediate items, the Gaia Kinetic Lance, the Sylvan
 * armor set and its four augmentation modules. No recipes here: the user writes those (the
 * {@code viridian_engineering:armor_upgrade} serializer keeps enchantments and the tiara).
 */
public final class VEGear {

    private static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ViridianEngineering.MOD_ID);

    /**
     * Spec 1.2 (LOCKED full-set stats): 20 armor (Terrasteel's 3/8/6/3 split), 14 toughness (3.5 a piece),
     * 60% knockback resistance (0.15 a piece). Textures: textures/models/armor/sylvan_layer_1/2.png.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SYLVAN_MATERIAL = MATERIALS.register("sylvan", () -> {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 3);
        defense.put(ArmorItem.Type.CHESTPLATE, 8);
        defense.put(ArmorItem.Type.LEGGINGS, 6);
        defense.put(ArmorItem.Type.BOOTS, 3);
        defense.put(ArmorItem.Type.BODY, 8);
        return new ArmorMaterial(defense, 26, SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(VEGear.GAIA_SPIRIT_SHEET.get()),
                List.of(new ArmorMaterial.Layer(ViridianEngineering.asResource("sylvan"))), 3.5f, 0.15f);
    });

    /** Terrasteel's multiplier is 34; Sylvan is a tier above. */
    private static final int DURABILITY_MULTIPLIER = 40;

    // ------------------------------------------------------------------ intermediates

    public static final ItemEntry<Item> GAIA_SPIRIT_SHEET = REGISTRATE.item("gaia_spirit_sheet", Item::new)
            .properties(p -> p.rarity(Rarity.RARE))
            .lang("Gaia Spirit Sheet")
            .register();

    public static final ItemEntry<Item> MANA_MECHANISM = REGISTRATE.item("mana_mechanism", Item::new)
            .properties(p -> p.rarity(Rarity.UNCOMMON))
            .lang("Mana Mechanism")
            .register();

    public static final ItemEntry<SequencedAssemblyItem> INCOMPLETE_MANA_MECHANISM = REGISTRATE
            .item("incomplete_mana_mechanism", SequencedAssemblyItem::new)
            .lang("Incomplete Mana Mechanism")
            .register();

    // ------------------------------------------------------------------ lance

    public static final ItemEntry<GaiaKineticLanceItem> GAIA_KINETIC_LANCE = REGISTRATE
            .item("gaia_kinetic_lance", GaiaKineticLanceItem::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant())
            // 3D, Create-tool style: models/item/gaia_kinetic_lance/{item,cog,gem}.json (tools/gen_lance_art.py),
            // drawn by client.LanceRenderer
            .model(com.simibubi.create.foundation.data.AssetLookup.itemModelWithPartials())
            .tag(ItemTags.WEAPON_ENCHANTABLE, ItemTags.SHARP_WEAPON_ENCHANTABLE)
            .lang("Gaia Kinetic Lance")
            .register();

    // ------------------------------------------------------------------ Sylvan armor

    public static final ItemEntry<SylvanArmorItem> SYLVAN_HELMET = armor("sylvan_helmet", ArmorItem.Type.HELMET,
            "Sylvan Helmet", ItemTags.HEAD_ARMOR_ENCHANTABLE);
    public static final ItemEntry<SylvanArmorItem> SYLVAN_CHESTPLATE = armor("sylvan_chestplate", ArmorItem.Type.CHESTPLATE,
            "Sylvan Chestplate", ItemTags.CHEST_ARMOR_ENCHANTABLE);
    public static final ItemEntry<SylvanArmorItem> SYLVAN_LEGGINGS = armor("sylvan_leggings", ArmorItem.Type.LEGGINGS,
            "Sylvan Leggings", ItemTags.LEG_ARMOR_ENCHANTABLE);
    public static final ItemEntry<SylvanArmorItem> SYLVAN_BOOTS = armor("sylvan_boots", ArmorItem.Type.BOOTS,
            "Sylvan Boots", ItemTags.FOOT_ARMOR_ENCHANTABLE);

    @SuppressWarnings("unchecked")
    private static ItemEntry<SylvanArmorItem> armor(String id, ArmorItem.Type type, String name, TagKey<Item> slotTag) {
        Holder<ArmorMaterial> mat = SYLVAN_MATERIAL;
        return REGISTRATE.item(id, p -> new SylvanArmorItem(mat, type, p))
                .properties(p -> p.durability(type.getDurability(DURABILITY_MULTIPLIER)).rarity(Rarity.EPIC).fireResistant())
                .tag(slotTag, ItemTags.ARMOR_ENCHANTABLE, ItemTags.DURABILITY_ENCHANTABLE, ItemTags.EQUIPPABLE_ENCHANTABLE)
                .lang(name)
                .register();
    }

    // ------------------------------------------------------------------ modules

    public static final ItemEntry<SylvanModuleItem> PNEUMATIC_BRACER_TANKS = module("pneumatic_bracer_tanks",
            GearData.Module.BRACER_TANKS, "Pneumatic Bracer Tanks");
    public static final ItemEntry<SylvanModuleItem> LIVINGROCK_MANA_PACK = module("livingrock_mana_pack",
            GearData.Module.MANA_PACK, "Livingrock Mana Pack");
    public static final ItemEntry<SylvanModuleItem> PNEUMATIC_STRIDERS = module("pneumatic_striders",
            GearData.Module.STRIDERS, "Pneumatic Striders");
    public static final ItemEntry<SylvanModuleItem> PNEUMATIC_BOOT_THRUSTERS = module("pneumatic_boot_thrusters",
            GearData.Module.BOOT_THRUSTERS, "Pneumatic Boot Thrusters");

    private static ItemEntry<SylvanModuleItem> module(String id, GearData.Module m, String name) {
        return REGISTRATE.item(id, p -> new SylvanModuleItem(m, p))
                .properties(p -> p.stacksTo(16).rarity(Rarity.RARE))
                .lang(name)
                .register();
    }

    private VEGear() {}

    public static void register(IEventBus modEventBus) {
        MATERIALS.register(modEventBus);
    }
}
