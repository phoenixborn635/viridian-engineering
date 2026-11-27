package dev.viridianengineering.registry;

import com.tterrag.registrate.util.entry.ItemEntry;
import dev.viridianengineering.content.infusion.AlfheimInfusionModuleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import static dev.viridianengineering.ViridianEngineering.REGISTRATE;

public final class VEItems {
    public static final ItemEntry<Item> MANASTEEL_SHEET = REGISTRATE.item("manasteel_sheet", Item::new)
            .lang("Manasteel Sheet").register();
    public static final ItemEntry<Item> ELEMENTIUM_SHEET = REGISTRATE.item("elementium_sheet", Item::new)
            .lang("Elementium Sheet").register();
    public static final ItemEntry<Item> TERRASTEEL_SHEET = REGISTRATE.item("terrasteel_sheet", Item::new)
            .lang("Terrasteel Sheet").register();

    /** Tier 3: attach to a Mechanical Press or Mixer to unlock mana infusion. */
    public static final ItemEntry<AlfheimInfusionModuleItem> ALFHEIM_INFUSION_MODULE = REGISTRATE
            .item("alfheim_infusion_module", AlfheimInfusionModuleItem::new)
            .properties(p -> p.stacksTo(16).rarity(Rarity.UNCOMMON))
            .lang("Alfheim Infusion Module")
            .register();

    private VEItems() {}

    public static void register() {}
}
