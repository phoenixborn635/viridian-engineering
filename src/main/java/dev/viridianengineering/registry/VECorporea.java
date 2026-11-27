package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.corporea.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.tterrag.registrate.util.entry.ItemEntry;

public final class VECorporea {
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ViridianEngineering.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ViridianEngineering.MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<CorporeaTerminalMenu>> TERMINAL_MENU=MENUS.register("corporea_terminal",()->IMenuTypeExtension.create(CorporeaTerminalMenu::new));
    public static final DeferredHolder<EntityType<?>,EntityType<ArcaneSparkEntity>> SPARK_ENTITY=ENTITIES.register("arcane_corporea_spark",()->EntityType.Builder.<ArcaneSparkEntity>of(ArcaneSparkEntity::new,MobCategory.MISC).sized(.35f,.35f).clientTrackingRange(8).updateInterval(10).build("arcane_corporea_spark"));
    public static final ItemEntry<ArcaneSparkItem> ARCANE_SPARK=ViridianEngineering.REGISTRATE.item("arcane_corporea_spark",ArcaneSparkItem::new).register();
    public static final ItemEntry<CorporeaWirelessTerminalItem> WIRELESS_TERMINAL=ViridianEngineering.REGISTRATE
            .item("corporea_wireless_terminal",CorporeaWirelessTerminalItem::new)
            .properties(p -> p.stacksTo(1))
            .lang("Corporea Wireless Terminal")
            .register();
    public static void register(IEventBus bus) { MENUS.register(bus); ENTITIES.register(bus); }
}
