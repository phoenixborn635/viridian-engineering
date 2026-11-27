package dev.viridianengineering.content.corporea;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One context must be propagated across every bridge participating in a request. */
public final class TransactionContext {
    private final UUID txId = UUID.randomUUID();
    private final Set<MachineEndpointKey> visitedBridges = new HashSet<>();
    private final Set<MachineEndpointKey> visitedInventories = new HashSet<>();
    public UUID txId() { return txId; }
    public boolean enterBridge(MachineEndpointKey bridge) { return visitedBridges.add(bridge); }
    public boolean visitInventory(MachineEndpointKey inventory) { return visitedInventories.add(inventory); }
}
