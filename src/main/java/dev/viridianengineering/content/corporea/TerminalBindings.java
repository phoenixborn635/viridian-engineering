package dev.viridianengineering.content.corporea;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The last known endpoint of each terminal that has been bound to a wireless item. */
final class TerminalBindings extends SavedData {
    private final Map<UUID, MachineEndpointKey> endpoints = new HashMap<>();

    static TerminalBindings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerminalBindings::new, TerminalBindings::load, null),
                ViridianEngineering.MOD_ID + "_terminal_bindings");
    }

    MachineEndpointKey endpoint(UUID id) {
        return endpoints.get(id);
    }

    void put(UUID id, MachineEndpointKey endpoint) {
        if (!endpoint.equals(endpoints.put(id, endpoint))) setDirty();
    }

    void remove(UUID id, MachineEndpointKey endpoint) {
        if (endpoints.remove(id, endpoint)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        endpoints.forEach((id, endpoint) -> MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE, endpoint)
                .result().ifPresent(encoded -> tag.put(id.toString(), encoded)));
        return tag;
    }

    private static TerminalBindings load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalBindings bindings = new TerminalBindings();
        for (String key : tag.getAllKeys()) {
            MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE, tag.get(key)).result()
                    .ifPresent(endpoint -> bindings.endpoints.put(UUID.fromString(key), endpoint));
        }
        return bindings;
    }
}
