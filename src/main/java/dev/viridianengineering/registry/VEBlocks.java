package dev.viridianengineering.registry;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.BuilderTransformers;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import dev.viridianengineering.client.ManasteelPipeAttachmentModel;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlock;
import dev.viridianengineering.content.pipe.EncasedManasteelFluidPipeBlock;
import dev.viridianengineering.content.pipe.ManasteelFluidPipeBlock;
import net.minecraft.world.level.material.MapColor;
import dev.viridianengineering.content.corporea.*;

import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static dev.viridianengineering.ViridianEngineering.REGISTRATE;

public final class VEBlocks {

    // ---- Tier 1-4 casings (spec Phase 1a) ----
    public static final BlockEntry<CasingBlock> MANASTEEL_CASING = REGISTRATE
            .block("manasteel_casing", CasingBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_BLUE))
            .transform(BuilderTransformers.casing(() -> VESpriteShifts.MANASTEEL_CASING))
            .register();

    public static final BlockEntry<CasingBlock> ELEMENTIUM_CASING = REGISTRATE
            .block("elementium_casing", CasingBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_PINK))
            .transform(BuilderTransformers.casing(() -> VESpriteShifts.ELEMENTIUM_CASING))
            .register();

    public static final BlockEntry<CasingBlock> TERRASTEEL_CASING = REGISTRATE
            .block("terrasteel_casing", CasingBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN))
            .transform(BuilderTransformers.casing(() -> VESpriteShifts.TERRASTEEL_CASING))
            .register();

    // ---- Manasteel fluid pipes (spec 3.1) ----
    public static final BlockEntry<ManasteelFluidPipeBlock> MANASTEEL_FLUID_PIPE = REGISTRATE
            .block("manasteel_fluid_pipe", ManasteelFluidPipeBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.forceSolidOff().mapColor(MapColor.COLOR_BLUE))
            .transform(pickaxeOnly())
            .blockstate(BlockStateGen.pipe())
            .onRegister(CreateRegistrate.blockModel(() -> ManasteelPipeAttachmentModel::withAO))
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/manasteel_fluid_pipe/item")))
            .build()
            .register();

    public static final BlockEntry<dev.viridianengineering.content.pipe.ManasteelGlassFluidPipeBlock> MANASTEEL_GLASS_FLUID_PIPE = REGISTRATE
            .block("manasteel_glass_fluid_pipe", dev.viridianengineering.content.pipe.ManasteelGlassFluidPipeBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLUE))
            .transform(pickaxeOnly())
            .blockstate((c, p) -> {
                var model = p.models().getExistingFile(p.modLoc("block/manasteel_fluid_pipe/window"));
                var variants = p.getVariantBuilder(c.get());
                var axis = com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock.AXIS;
                variants.partialState().with(axis, net.minecraft.core.Direction.Axis.Y).modelForState().modelFile(model).addModel();
                variants.partialState().with(axis, net.minecraft.core.Direction.Axis.X).modelForState().modelFile(model).rotationX(90).rotationY(90).addModel();
                variants.partialState().with(axis, net.minecraft.core.Direction.Axis.Z).modelForState().modelFile(model).rotationX(90).addModel();
            })
            .onRegister(CreateRegistrate.blockModel(() -> ManasteelPipeAttachmentModel::withAO))
            .loot((p, b) -> p.dropOther(b, MANASTEEL_FLUID_PIPE.get()))
            .register();

    public static final BlockEntry<EncasedManasteelFluidPipeBlock> ENCASED_MANASTEEL_FLUID_PIPE = REGISTRATE
            .block("encased_manasteel_fluid_pipe", p -> new EncasedManasteelFluidPipeBlock(p, MANASTEEL_CASING::get))
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLUE))
            .transform(axeOrPickaxe())
            .blockstate(BlockStateGen.encasedPipe())
            .onRegister(CreateRegistrate.connectedTextures(() -> new EncasedCTBehaviour(VESpriteShifts.MANASTEEL_CASING)))
            .onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.make(block, VESpriteShifts.MANASTEEL_CASING,
                    (s, f) -> !s.getValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(f)))))
            .onRegister(CreateRegistrate.blockModel(() -> ManasteelPipeAttachmentModel::withAO))
            .loot((p, b) -> p.dropOther(b, MANASTEEL_FLUID_PIPE.get()))
            .transform(EncasingRegistry.addVariantTo(MANASTEEL_FLUID_PIPE))
            .item()
                .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/manasteel_casing")))
                .build()
            .register();

    // ---- Terrasteel mana-only pipe (spec 3.2) ----
    public static final BlockEntry<TerrasteelManaPipeBlock> TERRASTEEL_MANA_PIPE = REGISTRATE
            .block("terrasteel_mana_pipe", TerrasteelManaPipeBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.forceSolidOff().noOcclusion().mapColor(MapColor.COLOR_LIGHT_GREEN))
            .transform(pickaxeOnly())
            .blockstate(VEBlocks::createPipeBlockstate) // Create's pipe multipart, like the Manasteel pipe
            .onRegister(CreateRegistrate.blockModel(() -> dev.viridianengineering.client.VEPipeModel::terrasteel))
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/terrasteel_mana_pipe/item")))
            .build()
            .register();

    public static final BlockEntry<CorporeaPipeBlock> CORPOREA_PIPE = REGISTRATE
            .block("corporea_pipe", CorporeaPipeBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.forceSolidOff().noOcclusion().mapColor(MapColor.COLOR_PURPLE))
            .transform(pickaxeOnly())
            .blockstate(VEBlocks::createPipeBlockstate)
            .onRegister(CreateRegistrate.blockModel(() -> dev.viridianengineering.client.VEPipeModel::corporea))
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/corporea_pipe/item")))
            .build().register();

    /** Create's {@code BlockStateGen.pipe()} only reads the six PipeBlock booleans, which our pipes share. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends net.minecraft.world.level.block.Block> void createPipeBlockstate(
            com.tterrag.registrate.providers.DataGenContext<net.minecraft.world.level.block.Block, T> c,
            com.tterrag.registrate.providers.RegistrateBlockstateProvider p) {
        ((com.tterrag.registrate.util.nullness.NonNullBiConsumer) BlockStateGen.pipe()).accept(c, p);
    }

    public static final BlockEntry<CorporeaPortBlock> CORPOREA_INPUT_PORT = port("corporea_input_port");
    public static final BlockEntry<CorporeaPortBlock> CORPOREA_OUTPUT_PORT = port("corporea_output_port");
    public static final BlockEntry<CorporeaPortBlock> CORPOREA_MACHINE_PORT = port("corporea_machine_port");
    public static final BlockEntry<CorporeaPortBlock> CORPOREA_BRIDGE_PORT = port("corporea_bridge_port");

    private static BlockEntry<CorporeaPortBlock> port(String name) {
        return REGISTRATE.<CorporeaPortBlock>block(name, p -> name.equals("corporea_bridge_port") ? new CorporeaPortBlock(p) : new CorporeaInventoryPortBlock(p))
                .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion()).transform(pickaxeOnly())
                .blockstate((c, p) -> {}).simpleItem().register();
    }

    public static final BlockEntry<CorporeaTerminalBlock> CORPOREA_CRAFTING_TERMINAL = REGISTRATE
            .block("corporea_crafting_terminal", CorporeaTerminalBlock::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion()).transform(pickaxeOnly())
            .blockstate((c, p) -> {}).simpleItem().register();

    // ---- Phase 4: Elementium logistics (spec 2.2) ----
    public static final BlockEntry<dev.viridianengineering.content.elementium.ElementiumBlocks.SmartFunnel> ELEMENTIUM_SMART_FUNNEL = REGISTRATE
            .block("elementium_smart_funnel", dev.viridianengineering.content.elementium.ElementiumBlocks.SmartFunnel::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_PINK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.elementium.ElementiumBlocks.SmartTunnel> ELEMENTIUM_SMART_TUNNEL = REGISTRATE
            .block("elementium_smart_tunnel", dev.viridianengineering.content.elementium.ElementiumBlocks.SmartTunnel::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_PINK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.elementium.ElementiumBlocks.RequestInterface> ELEMENTIUM_REQUEST_INTERFACE = REGISTRATE
            .block("elementium_request_interface", dev.viridianengineering.content.elementium.ElementiumBlocks.RequestInterface::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_PINK).lightLevel(s -> 6))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    // ---- Phase 4: Terrasteel Mana Reactor (spec 3.3) ----
    public static final BlockEntry<dev.viridianengineering.content.reactor.ReactorBlocks.Controller> TERRASTEEL_MANA_REACTOR_CONTROLLER = REGISTRATE
            .block("terrasteel_mana_reactor_controller", dev.viridianengineering.content.reactor.ReactorBlocks.Controller::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(s -> 7))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.FormedPartBlock> TERRASTEEL_REACTOR_CASING = REGISTRATE
            .block("terrasteel_reactor_casing", p -> new dev.viridianengineering.content.FormedPartBlock(p,
                    dev.viridianengineering.content.reactor.ReactorControllerBlockEntity::partRemoved))
            .initialProperties(SharedProperties::softMetal).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.FormedPartBlock> TERRASTEEL_MANA_REACTOR_CORE = REGISTRATE
            .block("terrasteel_mana_reactor_core", p -> new dev.viridianengineering.content.FormedPartBlock(p,
                    dev.viridianengineering.content.reactor.ReactorControllerBlockEntity::partRemoved))
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(s -> 12))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.reactor.ReactorPortBlock> TERRASTEEL_LAVA_PORT = REGISTRATE
            .block("terrasteel_lava_port", dev.viridianengineering.content.reactor.ReactorPortBlock::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.mapColor(MapColor.COLOR_ORANGE))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.reactor.ReactorBlocks.ShaftPort> TERRASTEEL_SHAFT_PORT = REGISTRATE
            .block("terrasteel_shaft_port", dev.viridianengineering.content.reactor.ReactorBlocks.ShaftPort::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.reactor.ReactorPortBlock> TERRASTEEL_MANA_PORT = REGISTRATE
            .block("terrasteel_mana_port", dev.viridianengineering.content.reactor.ReactorPortBlock::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(s -> 5))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    // ---- Phase 4: Terrasteel Mana Battery (spec 3.4) ----
    public static final BlockEntry<dev.viridianengineering.content.battery.BatteryBlocks.Controller> TERRASTEEL_MANA_BATTERY_CONTROLLER = REGISTRATE
            .block("terrasteel_mana_battery_controller", dev.viridianengineering.content.battery.BatteryBlocks.Controller::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(s -> 7).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.reactor.ReactorPortBlock> TERRASTEEL_MANA_BATTERY_PORT = REGISTRATE
            .block("terrasteel_mana_battery_port", dev.viridianengineering.content.reactor.ReactorPortBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(s -> 5).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.FormedPartBlock> TERRASTEEL_MANA_BATTERY_CASING = REGISTRATE
            .block("terrasteel_mana_battery_casing", p -> new dev.viridianengineering.content.FormedPartBlock(p,
                    dev.viridianengineering.content.battery.BatteryControllerBlockEntity::partRemoved))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    // ---- Phase 4: Gaia Arcane Cannon (spec 3.8) ----
    public static final BlockEntry<dev.viridianengineering.content.cannon.GaiaCannonBlock> GAIA_ARCANE_CANNON = REGISTRATE
            .block("gaia_arcane_cannon", dev.viridianengineering.content.cannon.GaiaCannonBlock::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_GRAY)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .item(dev.viridianengineering.content.cannon.GaiaCannonBlock.Item::new)
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/gaia_arcane_cannon_item"))).build()
            .register();

    public static final BlockEntry<dev.viridianengineering.content.cannon.GaiaCannonBlock.Part> GAIA_ARCANE_CANNON_PART = REGISTRATE
            .block("gaia_arcane_cannon_part", dev.viridianengineering.content.cannon.GaiaCannonBlock.Part::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().noLootTable().mapColor(MapColor.COLOR_GRAY)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .register();

    // ---- Pumps (user request): Mana Pump for Terrasteel mana pipes, Manasteel Pump for fluids ----
    public static final BlockEntry<dev.viridianengineering.content.pump.VEPumps.ManaPump> MANA_PUMP = REGISTRATE
            .block("mana_pump", dev.viridianengineering.content.pump.VEPumps.ManaPump::new)
            .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.blockModel(() -> com.simibubi.create.content.fluids.PipeAttachmentModel::withAO))
            .initialProperties(SharedProperties::copperMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_LIGHT_GREEN))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .onRegister(b -> com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(b, () -> 4.0))
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/mana_pump/item"))).build()
            .register();

    public static final BlockEntry<dev.viridianengineering.content.pump.VEPumps.ManasteelPump> MANASTEEL_PUMP = REGISTRATE
            .block("manasteel_pump", dev.viridianengineering.content.pump.VEPumps.ManasteelPump::new)
            .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.blockModel(() -> com.simibubi.create.content.fluids.PipeAttachmentModel::withAO))
            .initialProperties(SharedProperties::copperMetal).properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLUE))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .onRegister(b -> com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(b, () -> 4.0))
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/manasteel_pump/item"))).build()
            .register();

    // ---- Phase 5: Create: Aeronautics airship blocks (spec 3.8) ----
    public static final BlockEntry<dev.viridianengineering.content.airship.AirshipBlocks.LevitationPylon> MANA_LEVITATION_PYLON = REGISTRATE
            .block("mana_levitation_pylon", dev.viridianengineering.content.airship.AirshipBlocks.LevitationPylon::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 6))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .loot((lt, b) -> lt.add(b, net.minecraft.world.level.storage.loot.LootTable.lootTable().withPool(
                    net.minecraft.world.level.storage.loot.LootPool.lootPool()
                            .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
                            .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(b))
                            .when(net.minecraft.world.level.storage.loot.predicates.ExplosionCondition.survivesExplosion())
                            // only the lower half drops the item
                            .when(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(b)
                                    .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                                            .hasProperty(dev.viridianengineering.content.airship.AirshipBlocks.LevitationPylon.HALF,
                                                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER))))))
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/mana_levitation_pylon_item"))).build()
            .register();

    public static final BlockEntry<dev.viridianengineering.content.airship.AirshipBlocks.GaiaThruster> GAIA_THRUSTER = REGISTRATE
            .block("gaia_thruster", dev.viridianengineering.content.airship.AirshipBlocks.GaiaThruster::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_GRAY).lightLevel(s -> 4)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .item(dev.viridianengineering.content.cannon.GaiaCannonBlock.Item::new)
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/gaia_thruster_item"))).build()
            .register();

    public static final BlockEntry<dev.viridianengineering.content.airship.AirshipBlocks.ThrusterPart> GAIA_THRUSTER_PART = REGISTRATE
            .block("gaia_thruster_part", dev.viridianengineering.content.airship.AirshipBlocks.ThrusterPart::new)
            .initialProperties(SharedProperties::softMetal).properties(p -> p.noOcclusion().noLootTable().mapColor(MapColor.COLOR_GRAY)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
            .transform(pickaxeOnly()).blockstate((c, p) -> {})
            .register();

    public static final BlockEntry<dev.viridianengineering.content.airship.AirshipBlocks.BifrostProjector> BIFROST_PROJECTOR = REGISTRATE
            .block("bifrost_projector", dev.viridianengineering.content.airship.AirshipBlocks.BifrostProjector::new)
            .lang("Bifrost Road Projector")
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_PINK).lightLevel(s -> 5))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    public static final BlockEntry<dev.viridianengineering.content.airship.BifrostTrailBlock> BIFROST_TRAIL = REGISTRATE
            .block("bifrost_trail", dev.viridianengineering.content.airship.BifrostTrailBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.GLASS)
            .properties(p -> p.noOcclusion().noLootTable().strength(0.3f).mapColor(MapColor.COLOR_MAGENTA)
                    .isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY))
            .blockstate((c, p) -> {})
            .register();

    public static final BlockEntry<dev.viridianengineering.content.airship.AirshipBlocks.FlugelNavigationCore> FLUGEL_NAVIGATION_CORE = REGISTRATE
            .block("flugel_navigation_core", dev.viridianengineering.content.airship.AirshipBlocks.FlugelNavigationCore::new)
            .lang("Eye of the Fl\u00fcgel Navigation Core")
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.GOLD).lightLevel(s -> 6))
            .transform(pickaxeOnly()).blockstate((c, p) -> {}).simpleItem().register();

    private VEBlocks() {}

    /** Forces class loading so the entries above are queued with Registrate. */
    public static void register() {}
}
