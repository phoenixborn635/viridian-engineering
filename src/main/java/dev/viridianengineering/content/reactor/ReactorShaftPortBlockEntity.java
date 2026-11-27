package dev.viridianengineering.content.reactor;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Kinetic input of the reactor. Draws {@code reactorStressPerRpm} SU per RPM while its reactor is formed. */
public class ReactorShaftPortBlockEntity extends KineticBlockEntity {

    @Nullable private BlockPos reactor;
    /** The reactor's size multiplier (stress per RPM scales with it). */
    private int factor = 1;

    public ReactorShaftPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    void setReactor(@Nullable BlockPos controller, int factor) {
        if (Objects.equals(reactor, controller) && this.factor == factor) return;
        reactor = controller;
        this.factor = Math.max(1, factor);
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        sendData();
    }

    public boolean isFormed() { return reactor != null; }

    @Override
    public float calculateStressApplied() {
        // Create multiplies this coefficient by |RPM| itself.
        lastStressApplied = reactor == null ? 0 : VEConfig.REACTOR_STRESS_PER_RPM.get().floatValue() * factor;
        return lastStressApplied;
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (reactor != null) tag.putLong("Reactor", reactor.asLong());
        tag.putInt("Factor", factor);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        reactor = tag.contains("Reactor") ? BlockPos.of(tag.getLong("Reactor")) : null;
        factor = Math.max(1, tag.getInt("Factor"));
    }
}
