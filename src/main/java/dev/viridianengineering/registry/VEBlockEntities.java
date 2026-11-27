package dev.viridianengineering.registry;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlockEntity;
import dev.viridianengineering.content.pipe.ManasteelFluidPipeBlockEntity;

import static dev.viridianengineering.ViridianEngineering.REGISTRATE;

public final class VEBlockEntities {

    public static final BlockEntityEntry<ManasteelFluidPipeBlockEntity> MANASTEEL_FLUID_PIPE = REGISTRATE
            .blockEntity("manasteel_fluid_pipe", ManasteelFluidPipeBlockEntity::new)
            .validBlocks(VEBlocks.MANASTEEL_FLUID_PIPE, VEBlocks.ENCASED_MANASTEEL_FLUID_PIPE)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.pipe.ManasteelGlassFluidPipeBlockEntity> MANASTEEL_GLASS_FLUID_PIPE = REGISTRATE
            .blockEntity("manasteel_glass_fluid_pipe", dev.viridianengineering.content.pipe.ManasteelGlassFluidPipeBlockEntity::new)
            .validBlocks(VEBlocks.MANASTEEL_GLASS_FLUID_PIPE)
            .renderer(() -> com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer::new)
            .register();

    public static final BlockEntityEntry<TerrasteelManaPipeBlockEntity> TERRASTEEL_MANA_PIPE = REGISTRATE
            .blockEntity("terrasteel_mana_pipe", TerrasteelManaPipeBlockEntity::new)
            .validBlocks(VEBlocks.TERRASTEEL_MANA_PIPE)
            .renderer(() -> dev.viridianengineering.client.PipePulseRenderer::mana)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.corporea.CorporeaPipeBlockEntity> CORPOREA_PIPE = REGISTRATE
            .blockEntity("corporea_pipe", dev.viridianengineering.content.corporea.CorporeaPipeBlockEntity::new)
            .validBlocks(VEBlocks.CORPOREA_PIPE)
            .renderer(() -> dev.viridianengineering.client.CorporeaPulseRenderer::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.corporea.CorporeaTerminalBlockEntity> CORPOREA_TERMINAL = REGISTRATE
            .blockEntity("corporea_crafting_terminal", dev.viridianengineering.content.corporea.CorporeaTerminalBlockEntity::new)
            .visual(() -> dev.viridianengineering.client.CorporeaTerminalRenderer.visual(), false)
            .validBlocks(VEBlocks.CORPOREA_CRAFTING_TERMINAL)
            .renderer(() -> dev.viridianengineering.client.CorporeaTerminalRenderer::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.corporea.CorporeaPortBlockEntity> CORPOREA_PORT = REGISTRATE
            .blockEntity("corporea_port", dev.viridianengineering.content.corporea.CorporeaPortBlockEntity::new)
            .validBlocks(VEBlocks.CORPOREA_INPUT_PORT,VEBlocks.CORPOREA_OUTPUT_PORT,VEBlocks.CORPOREA_MACHINE_PORT).register();

    // ---- Phase 4 ----
    public static final BlockEntityEntry<dev.viridianengineering.content.elementium.SmartFunnelBlockEntity> ELEMENTIUM_SMART_FUNNEL = REGISTRATE
            .blockEntity("elementium_smart_funnel", dev.viridianengineering.content.elementium.SmartFunnelBlockEntity::new)
            .validBlocks(VEBlocks.ELEMENTIUM_SMART_FUNNEL)
            .renderer(() -> com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.elementium.SmartTunnelBlockEntity> ELEMENTIUM_SMART_TUNNEL = REGISTRATE
            .blockEntity("elementium_smart_tunnel", dev.viridianengineering.content.elementium.SmartTunnelBlockEntity::new)
            .validBlocks(VEBlocks.ELEMENTIUM_SMART_TUNNEL)
            .renderer(() -> com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.elementium.RequestInterfaceBlockEntity> ELEMENTIUM_REQUEST_INTERFACE = REGISTRATE
            .blockEntity("elementium_request_interface", dev.viridianengineering.content.elementium.RequestInterfaceBlockEntity::new)
            .validBlocks(VEBlocks.ELEMENTIUM_REQUEST_INTERFACE)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.reactor.ReactorControllerBlockEntity> REACTOR_CONTROLLER = REGISTRATE
            .blockEntity("terrasteel_mana_reactor_controller", dev.viridianengineering.content.reactor.ReactorControllerBlockEntity::new)
            .validBlocks(VEBlocks.TERRASTEEL_MANA_REACTOR_CONTROLLER)
            .renderer(() -> dev.viridianengineering.client.FormedRenderers.Reactor::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.reactor.ReactorPortBlock.Entity> REACTOR_PORT = REGISTRATE
            .blockEntity("terrasteel_reactor_port", dev.viridianengineering.content.reactor.ReactorPortBlock.Entity::new)
            .validBlocks(VEBlocks.TERRASTEEL_LAVA_PORT, VEBlocks.TERRASTEEL_MANA_PORT, VEBlocks.TERRASTEEL_MANA_BATTERY_PORT)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.reactor.ReactorShaftPortBlockEntity> REACTOR_SHAFT_PORT = REGISTRATE
            .blockEntity("terrasteel_shaft_port", dev.viridianengineering.content.reactor.ReactorShaftPortBlockEntity::new)
            .visual(() -> dev.viridianengineering.client.PhaseFourRenderers.shaftVisual(), false)
            .validBlocks(VEBlocks.TERRASTEEL_SHAFT_PORT)
            .renderer(() -> dev.viridianengineering.client.PhaseFourRenderers.ShaftPort::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.battery.BatteryControllerBlockEntity> BATTERY_CONTROLLER = REGISTRATE
            .blockEntity("terrasteel_mana_battery_controller", dev.viridianengineering.content.battery.BatteryControllerBlockEntity::new)
            .validBlocks(VEBlocks.TERRASTEEL_MANA_BATTERY_CONTROLLER)
            .renderer(() -> dev.viridianengineering.client.FormedRenderers.Battery::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.cannon.GaiaCannonBlockEntity> GAIA_ARCANE_CANNON = REGISTRATE
            .blockEntity("gaia_arcane_cannon", dev.viridianengineering.content.cannon.GaiaCannonBlockEntity::new)
            .validBlocks(VEBlocks.GAIA_ARCANE_CANNON)
            .renderer(() -> dev.viridianengineering.client.PhaseFourRenderers.Cannon::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.pump.VEPumps.ManaPumpEntity> MANA_PUMP = REGISTRATE
            .blockEntity("mana_pump", dev.viridianengineering.content.pump.VEPumps.ManaPumpEntity::new)
            .validBlocks(VEBlocks.MANA_PUMP)
            .renderer(() -> dev.viridianengineering.client.VEPumpRenderer::mana)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.pump.VEPumps.ManasteelPumpEntity> MANASTEEL_PUMP = REGISTRATE
            .blockEntity("manasteel_pump", dev.viridianengineering.content.pump.VEPumps.ManasteelPumpEntity::new)
            .validBlocks(VEBlocks.MANASTEEL_PUMP)
            .renderer(() -> dev.viridianengineering.client.VEPumpRenderer::manasteel)
            .register();

    // ---- Phase 5 ----
    public static final BlockEntityEntry<dev.viridianengineering.content.airship.LevitationPylonBlockEntity> MANA_LEVITATION_PYLON = REGISTRATE
            .blockEntity("mana_levitation_pylon", dev.viridianengineering.content.airship.LevitationPylonBlockEntity::new)
            .validBlocks(VEBlocks.MANA_LEVITATION_PYLON)
            .renderer(() -> dev.viridianengineering.client.AirshipRenderers.Pylon::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.airship.GaiaThrusterBlockEntity> GAIA_THRUSTER = REGISTRATE
            .blockEntity("gaia_thruster", dev.viridianengineering.content.airship.GaiaThrusterBlockEntity::new)
            .validBlocks(VEBlocks.GAIA_THRUSTER)
            .renderer(() -> dev.viridianengineering.client.AirshipRenderers.Thruster::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.airship.BifrostProjectorBlockEntity> BIFROST_PROJECTOR = REGISTRATE
            .blockEntity("bifrost_projector", dev.viridianengineering.content.airship.BifrostProjectorBlockEntity::new)
            .validBlocks(VEBlocks.BIFROST_PROJECTOR)
            .renderer(() -> dev.viridianengineering.client.AirshipRenderers.Projector::new)
            .register();

    public static final BlockEntityEntry<dev.viridianengineering.content.airship.FlugelNavigationCoreBlockEntity> FLUGEL_NAVIGATION_CORE = REGISTRATE
            .blockEntity("flugel_navigation_core", dev.viridianengineering.content.airship.FlugelNavigationCoreBlockEntity::new)
            .validBlocks(VEBlocks.FLUGEL_NAVIGATION_CORE)
            .renderer(() -> dev.viridianengineering.client.AirshipRenderers.Core::new)
            .register();

    private VEBlockEntities() {}

    public static void register() {}
}
