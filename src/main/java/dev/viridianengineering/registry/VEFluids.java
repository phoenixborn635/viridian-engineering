package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** A Create processing fluid. It cannot be placed in the world or converted back into Botania mana. */
public final class VEFluids {
    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ViridianEngineering.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ViridianEngineering.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> LIQUID_MANA_TYPE =
            TYPES.register("liquid_mana", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.viridian_engineering.liquid_mana")));
    public static final DeferredHolder<Fluid, FlowingFluid> LIQUID_MANA =
            FLUIDS.register("liquid_mana", () -> new BaseFlowingFluid.Source(liquidManaProperties()));
    private static final DeferredHolder<Fluid, FlowingFluid> FLOWING_LIQUID_MANA =
            FLUIDS.register("flowing_liquid_mana", () -> new BaseFlowingFluid.Flowing(liquidManaProperties()));

    private VEFluids() {}

    private static BaseFlowingFluid.Properties liquidManaProperties() {
        return new BaseFlowingFluid.Properties(LIQUID_MANA_TYPE, LIQUID_MANA, FLOWING_LIQUID_MANA);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
