package dev.viridianengineering.compat;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.content.autocraft.CraftingJobSavedData;
import dev.viridianengineering.content.autocraft.PatternStore;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.drive.TerraDriveSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Sable assembly and disassembly move blocks between terrain and a ship's plot, which changes their
 * {@link MachineEndpointKey} (terrain keys are world positions, ship keys are relative to the ship).
 * The machines that own saved records call this from {@code BlockSubLevelAssemblyListener.afterMove},
 * so patterns, drive mount locks and crafting jobs follow the block onto (or off) the ship.
 * Moving with the ship afterwards never changes a key, so nothing else is needed in flight.
 */
public final class EndpointRekey {

    private EndpointRekey() {}

    public static void moved(ServerLevel oldLevel, ServerLevel newLevel, BlockPos oldPos, BlockPos newPos) {
        var server = newLevel.getServer();
        PatternStore patterns = PatternStore.get(server);
        TerraDriveSavedData drives = TerraDriveSavedData.get(server);
        CraftingJobSavedData jobs = CraftingJobSavedData.get(server);
        for (Direction face : Direction.values()) {
            MachineEndpointKey from = SableCompat.endpointKey(oldLevel, oldPos, face);
            MachineEndpointKey to = SableCompat.endpointKey(newLevel, newPos, face);
            if (from.equals(to)) continue;
            patterns.rekey(from, to);
            drives.rekey(from, to);
            jobs.rekey(from, to);
        }
        dev.viridianengineering.content.corporea.ArcaneSparkEntity.moved(oldLevel, newLevel, oldPos, newPos);
        CorporeaNetworkManager.invalidate(oldLevel);
        if (newLevel != oldLevel) CorporeaNetworkManager.invalidate(newLevel);
    }
}
