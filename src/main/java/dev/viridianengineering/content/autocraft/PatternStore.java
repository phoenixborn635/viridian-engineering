package dev.viridianengineering.content.autocraft;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.content.drive.ItemStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spec 3.7: patterns live in the terminal's SavedData, keyed by the terminal's
 * {@link MachineEndpointKey}, so they survive restarts and follow terminals aboard ships.
 */
public final class PatternStore extends SavedData {

    public static final String ID = ViridianEngineering.MOD_ID + "_patterns";
    public static final int MAX_PATTERNS = 512;

    private final Map<MachineEndpointKey, List<CraftPattern>> byTerminal = new HashMap<>();

    public static PatternStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PatternStore::new, PatternStore::load, null), ID);
    }

    public List<CraftPattern> patterns(MachineEndpointKey terminal) {
        return List.copyOf(byTerminal.getOrDefault(terminal, List.of()));
    }

    /** Adds a pattern, replacing an existing one for the same recipe. False when the terminal is full. */
    public boolean add(MachineEndpointKey terminal, CraftPattern pattern) {
        List<CraftPattern> list = byTerminal.computeIfAbsent(terminal, k -> new ArrayList<>());
        list.removeIf(p -> p.recipeId.equals(pattern.recipeId) && p.outputKey().equals(pattern.outputKey()));
        if (list.size() >= MAX_PATTERNS) return false;
        list.add(pattern);
        setDirty();
        return true;
    }

    public boolean remove(MachineEndpointKey terminal, UUID id) {
        List<CraftPattern> list = byTerminal.get(terminal);
        if (list == null || !list.removeIf(p -> p.id.equals(id))) return false;
        setDirty();
        return true;
    }

    /** The terminal changed key (its ship was assembled or disassembled): its patterns follow it. */
    public void rekey(MachineEndpointKey from, MachineEndpointKey to) {
        List<CraftPattern> list = byTerminal.remove(from);
        if (list == null) return;
        byTerminal.merge(to, list, (a, b) -> { a.addAll(b); return a; });
        setDirty();
    }

    public CraftPattern find(MachineEndpointKey terminal, ItemStackKey output) {
        for (CraftPattern p : byTerminal.getOrDefault(terminal, List.of()))
            if (p.outputKey().equals(output)) return p;
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag terminals = new ListTag();
        byTerminal.forEach((key, list) -> MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE, key).result().ifPresent(encoded -> {
            CompoundTag t = new CompoundTag();
            t.put("Terminal", encoded);
            ListTag patterns = new ListTag();
            for (CraftPattern p : list) patterns.add(p.save(registries));
            t.put("Patterns", patterns);
            terminals.add(t);
        }));
        tag.put("Terminals", terminals);
        return tag;
    }

    public static PatternStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PatternStore store = new PatternStore();
        for (Tag t : tag.getList("Terminals", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE, entry.get("Terminal")).result().ifPresent(key -> {
                List<CraftPattern> list = new ArrayList<>();
                for (Tag p : entry.getList("Patterns", Tag.TAG_COMPOUND)) {
                    try {
                        list.add(CraftPattern.load((CompoundTag) p, registries));
                    } catch (RuntimeException e) {
                        ViridianEngineering.LOGGER.warn("Skipping an unreadable autocrafting pattern: {}", e.toString());
                    }
                }
                store.byTerminal.put(key, list);
            });
        }
        return store;
    }
}
