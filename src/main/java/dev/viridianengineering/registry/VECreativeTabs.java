package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VECreativeTabs {

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ViridianEngineering.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ViridianEngineering.MOD_ID))
                    .icon(() -> VEBlocks.TERRASTEEL_CASING.asStack())
                    // Left empty on purpose: Registrate fills this tab (see the defaultCreativeTab call
                    // in ViridianEngineering). Adding items here as well would list them twice.
                    .displayItems((params, output) -> {})
                    .build());

    private VECreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
