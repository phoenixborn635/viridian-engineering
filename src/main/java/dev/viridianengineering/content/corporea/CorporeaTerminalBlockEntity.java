package dev.viridianengineering.content.corporea;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.compat.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class CorporeaTerminalBlockEntity extends KineticBlockEntity {
    private UUID bindingId;

    public CorporeaTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    public boolean isPowered() { return Math.abs(getSpeed()) >= VEConfig.CONTROLLER_MIN_RPM.get(); }

    UUID bindingId() { return bindingId; }

    UUID bindWireless(ServerLevel server) {
        if (bindingId == null) {
            bindingId = UUID.randomUUID();
            setChanged();
        }
        TerminalBindings.get(server.getServer()).put(bindingId, SableCompat.endpointKey(server, getBlockPos(), Direction.DOWN));
        return bindingId;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket && bindingId != null) tag.putUUID("WirelessBindingId", bindingId);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) bindingId = tag.hasUUID("WirelessBindingId") ? tag.getUUID("WirelessBindingId") : null;
    }

    @Override public float calculateStressApplied() {
        // Create multiplies this coefficient by speed itself. Multiplying here would square RPM.
        lastStressApplied = VEConfig.CONTROLLER_STRESS_PER_RPM.get().floatValue();
        return lastStressApplied;
    }
}
