package dev.viridianengineering;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import dev.viridianengineering.compat.DependencyProbe;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.infusion.AlfheimPressHandler;
import dev.viridianengineering.registry.ModCapabilities;
import dev.viridianengineering.registry.VEBlockEntities;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VECreativeTabs;
import dev.viridianengineering.registry.VEItems;
import dev.viridianengineering.registry.VELang;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ViridianEngineering.MOD_ID)
public final class ViridianEngineering {

    public static final String MOD_ID = "viridian_engineering";
    public static final String NAME = "Viridian Engineering";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Spec 0.2 "Own Your Registrate": Create 6.0.3+ keeps Create.REGISTRATE private,
     * so this addon owns a dedicated instance.
     */
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
            // Registrate adds every item it registers to its default creative tab. Point that at our
            // own tab; left at the default, items also landed in a vanilla tab and the creative
            // inventory crashed with "Itemstack ... already exists in the tab's list".
            .defaultCreativeTab(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "main")));

    public ViridianEngineering(IEventBus modEventBus, ModContainer container) {
        REGISTRATE.registerEventListeners(modEventBus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) ClientInit.init();

        VEBlocks.register();
        VEBlockEntities.register();
        VEItems.register();
        dev.viridianengineering.registry.VEGear.register(modEventBus);
        dev.viridianengineering.registry.VEStorage.register();
        dev.viridianengineering.registry.VECorporea.register(modEventBus);
        modEventBus.addListener(dev.viridianengineering.content.corporea.CorporeaBridge::setup);
        modEventBus.addListener(dev.viridianengineering.content.corporea.CorporeaPortBlockEntity::capabilities);
        VELang.register();
        VERecipes.register(modEventBus);
        dev.viridianengineering.registry.VESounds.register(modEventBus);
        dev.viridianengineering.registry.VEFluids.register(modEventBus);
        dev.viridianengineering.registry.VEEntities.register(modEventBus);
        VECreativeTabs.register(modEventBus);

        VEConfig.register(container);

        modEventBus.addListener(ModCapabilities::registerCapabilities);
        modEventBus.addListener(AlfheimPressHandler::registerCapabilities);
        modEventBus.addListener(DependencyProbe::onCommonSetup);
        modEventBus.addListener(dev.viridianengineering.network.VENetwork::register);

        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.corporea.CorporeaNetworkManager::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.corporea.CorporeaNetworkManager::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(DependencyProbe::onServerStarted);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.autocraft.CraftingScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(AlfheimPressHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(AlfheimPressHandler::onBlockDrops);

        // Phase 4: battery detonation engine. LOW priority and skipping cancelled breaks, so a claim or
        // protection mod that already refused the break wins and no roll happens.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, false,
                net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class,
                dev.viridianengineering.content.battery.BatteryDetonation::onBreak);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW,
                dev.viridianengineering.content.battery.BatteryDetonation::onExplosion);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.battery.BatteryDetonation::onLevelTick);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.battery.BatteryDetonation::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.battery.BatteryDetonation::onPlayerChangedDimension);

        // Phase 5: Bifrost trail expiry/fading, and dropping transient ship flight state on server stop.
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.airship.BifrostTrails::onLevelTick);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.airship.AirshipDemo::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
                dev.viridianengineering.content.airship.ShipFlight.clear());

        // Viridian Phase 5a: gaia gear (lance, Sylvan armor and modules, Flugel Tiara helmet)
        modEventBus.addListener(dev.viridianengineering.content.gear.SylvanEvents::commonSetup);
        modEventBus.addListener(dev.viridianengineering.content.gear.SylvanEvents::capabilities);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.gear.SylvanEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.corporea.RelaySparkFeedback::tick);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.gear.SylvanEvents::manaItems);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.gear.SylvanEvents::onFall);
        NeoForge.EVENT_BUS.addListener(dev.viridianengineering.content.gear.SylvanEvents::onLogout);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
                dev.viridianengineering.content.gear.SylvanEvents.clear());
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
