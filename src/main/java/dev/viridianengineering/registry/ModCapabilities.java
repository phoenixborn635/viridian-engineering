package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.mana.IManaPipeNode;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

public final class ModCapabilities {

    /**
     * Spec 3.2 capability isolation. Never register mana pipes under
     * {@code Capabilities.FluidHandler.BLOCK}.
     */
    public static final BlockCapability<IManaPipeNode, @Nullable Direction> MANA_PIPE_NODE =
            BlockCapability.createSided(ViridianEngineering.asResource("mana_pipe_node"), IManaPipeNode.class);

    private ModCapabilities() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MANA_PIPE_NODE, VEBlockEntities.TERRASTEEL_MANA_PIPE.get(), (be, side) -> be);
        dev.viridianengineering.registry.PhaseFourCapabilities.register(event);
    }
}
