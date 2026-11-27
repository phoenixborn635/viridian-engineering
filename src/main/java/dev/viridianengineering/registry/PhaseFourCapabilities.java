package dev.viridianengineering.registry;

import dev.viridianengineering.content.battery.BatteryControllerBlockEntity;
import dev.viridianengineering.content.reactor.ReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import velrondevs.botania.api.BotaniaCapabilities;

/** Mana, fluid and item capabilities of the Phase 4 blocks. */
public final class PhaseFourCapabilities {

    private PhaseFourCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // Elementium logistics: mana in (pipes or spreaders); tunnel takes items from its back.
        event.registerBlockEntity(ModCapabilities.MANA_PIPE_NODE, VEBlockEntities.ELEMENTIUM_SMART_FUNNEL.get(), (be, side) -> be.mana);
        event.registerBlockEntity(ModCapabilities.MANA_PIPE_NODE, VEBlockEntities.ELEMENTIUM_SMART_TUNNEL.get(), (be, side) -> be.mana);
        event.registerBlockEntity(ModCapabilities.MANA_PIPE_NODE, VEBlockEntities.ELEMENTIUM_REQUEST_INTERFACE.get(), (be, side) -> be.mana);
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.ELEMENTIUM_SMART_FUNNEL.get(), (be, side) -> be.mana.asReceiver());
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.ELEMENTIUM_SMART_TUNNEL.get(), (be, side) -> be.mana.asReceiver());
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.ELEMENTIUM_REQUEST_INTERFACE.get(), (be, side) -> be.mana.asReceiver());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, VEBlockEntities.ELEMENTIUM_SMART_TUNNEL.get(), (be, side) -> be.inputFrom(side));

        // Multiblock faces always expose a capability (so pipes connect before the structure forms);
        // it does nothing until its controller is formed.
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> new DelegatingFluid(
                () -> { ReactorControllerBlockEntity r = ReactorControllerBlockEntity.owning(level, pos); return r == null ? null : r.lava; }),
                VEBlocks.TERRASTEEL_LAVA_PORT.get());
        event.registerBlock(ModCapabilities.MANA_PIPE_NODE, (level, pos, state, be, side) -> new DelegatingMana(
                () -> { ReactorControllerBlockEntity r = ReactorControllerBlockEntity.owning(level, pos); return r == null ? null : r.mana; }),
                VEBlocks.TERRASTEEL_MANA_PORT.get());

        // Battery: its port is the only I/O face (both ways); casings and controller are inert.
        event.registerBlock(ModCapabilities.MANA_PIPE_NODE, (level, pos, state, be, side) -> new DelegatingMana(() -> batteryStore(level, pos)),
                VEBlocks.TERRASTEEL_MANA_BATTERY_PORT.get());
        event.registerBlock(BotaniaCapabilities.MANA_RECEIVER, (level, pos, state, be, side) -> {
            dev.viridianengineering.api.mana.ManaBuffer store = batteryStore(level, pos);
            return store == null ? null : store.asReceiver();
        }, VEBlocks.TERRASTEEL_MANA_BATTERY_PORT.get());

        // Cannon: mana in only through the outward face of its mana port (pipes connect nowhere else).
        event.registerBlock(ModCapabilities.MANA_PIPE_NODE, (level, pos, state, be, side) -> {
            if (!dev.viridianengineering.content.cannon.GaiaCannonBlock.isPortFace(level, pos, side, true)) return null;
            return new DelegatingMana(() -> {
                BlockPos m = dev.viridianengineering.content.cannon.GaiaCannonBlock.Part.master(level, pos);
                return m != null && level.getBlockEntity(m) instanceof dev.viridianengineering.content.cannon.GaiaCannonBlockEntity c ? c.mana : null;
            });
        }, VEBlocks.GAIA_ARCANE_CANNON_PART.get());
        event.registerBlockEntity(ModCapabilities.MANA_PIPE_NODE, VEBlockEntities.GAIA_ARCANE_CANNON.get(), (be, side) -> be.mana);
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.GAIA_ARCANE_CANNON.get(), (be, side) -> be.mana.asReceiver());

        // Phase 5 airship blocks: mana in from pipes or spreaders (the pylon on either half).
        event.registerBlock(ModCapabilities.MANA_PIPE_NODE, (level, pos, state, be, side) ->
                level.getBlockEntity(dev.viridianengineering.content.airship.AirshipBlocks.LevitationPylon.base(state, pos))
                        instanceof dev.viridianengineering.content.airship.LevitationPylonBlockEntity p ? p.mana : null,
                VEBlocks.MANA_LEVITATION_PYLON.get());
        event.registerBlock(BotaniaCapabilities.MANA_RECEIVER, (level, pos, state, be, side) ->
                level.getBlockEntity(dev.viridianengineering.content.airship.AirshipBlocks.LevitationPylon.base(state, pos))
                        instanceof dev.viridianengineering.content.airship.LevitationPylonBlockEntity p ? p.mana.asReceiver() : null,
                VEBlocks.MANA_LEVITATION_PYLON.get());
        // the 3x3x3 thruster takes pipes only on its mana port face (spreaders can still aim at its centre)
        event.registerBlock(ModCapabilities.MANA_PIPE_NODE, (level, pos, state, be, side) -> {
            if (!dev.viridianengineering.content.airship.AirshipBlocks.GaiaThruster.isPortFace(level, pos, side, true)) return null;
            return new DelegatingMana(() -> {
                BlockPos m = dev.viridianengineering.content.airship.AirshipBlocks.ThrusterPart.master(level, pos);
                return m != null && level.getBlockEntity(m) instanceof dev.viridianengineering.content.airship.GaiaThrusterBlockEntity t ? t.mana : null;
            });
        }, VEBlocks.GAIA_THRUSTER_PART.get());
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.GAIA_THRUSTER.get(), (be, side) -> be.mana.asReceiver());
        event.registerBlockEntity(ModCapabilities.MANA_PIPE_NODE, VEBlockEntities.BIFROST_PROJECTOR.get(), (be, side) -> be.mana);
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.BIFROST_PROJECTOR.get(), (be, side) -> be.mana.asReceiver());
        event.registerBlockEntity(ModCapabilities.MANA_PIPE_NODE, VEBlockEntities.FLUGEL_NAVIGATION_CORE.get(), (be, side) -> be.mana);
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, VEBlockEntities.FLUGEL_NAVIGATION_CORE.get(), (be, side) -> be.mana.asReceiver());
    }

    @org.jetbrains.annotations.Nullable
    private static dev.viridianengineering.api.mana.ManaBuffer batteryStore(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        BatteryControllerBlockEntity b = BatteryControllerBlockEntity.owning(level, pos);
        return b == null || !b.isFormed() || !b.ports().contains(pos) ? null : b.mana;
    }

    /** Mana node that forwards to whatever store currently backs the block (none: inert). */
    private record DelegatingMana(java.util.function.Supplier<dev.viridianengineering.api.mana.IManaPipeNode> target)
            implements dev.viridianengineering.api.mana.IManaPipeNode {
        @Override public long getStoredMana() { var t = target.get(); return t == null ? 0 : t.getStoredMana(); }
        @Override public long getManaCapacity() { var t = target.get(); return t == null ? 0 : t.getManaCapacity(); }
        @Override public long insertMana(long a, boolean sim) { var t = target.get(); return t == null ? 0 : t.insertMana(a, sim); }
        @Override public long extractMana(long a, boolean sim) { var t = target.get(); return t == null ? 0 : t.extractMana(a, sim); }
        @Override public Role role() { var t = target.get(); return t == null ? Role.STORE : t.role(); }
    }

    private record DelegatingFluid(java.util.function.Supplier<net.neoforged.neoforge.fluids.capability.IFluidHandler> target)
            implements net.neoforged.neoforge.fluids.capability.IFluidHandler {
        private net.neoforged.neoforge.fluids.capability.IFluidHandler t() {
            var t = target.get();
            return t == null ? net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler.INSTANCE : t;
        }
        @Override public int getTanks() { return t().getTanks(); }
        @Override public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) { return t().getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { return t().getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) { return t().isFluidValid(tank, stack); }
        @Override public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) { return t().fill(resource, action); }
        @Override public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) { return t().drain(resource, action); }
        @Override public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) { return t().drain(maxDrain, action); }
    }
}
