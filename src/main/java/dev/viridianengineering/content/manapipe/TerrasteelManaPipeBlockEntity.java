package dev.viridianengineering.content.manapipe;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.api.mana.IManaPipeNode;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * One segment of the Terrasteel mana network (spec 3.2).
 * <p>
 * <b>Passive flow:</b> every tick each segment pushes mana toward any connected
 * neighbour (pipe, pool, spreader, machine) whose fill fraction is lower than its own,
 * up to {@code terrasteelPipePassiveRate} (40) mana/tick per link, and pulls from
 * pools/spreaders that are fuller than it. Mana always flows from fuller to emptier.
 * <p>
 * <b>Pumped flow (directional, spec 3.2):</b> a rotating Create Mechanical Pump moves mana from
 * the block behind it to the block in front of it at {@code 40 + 1.25 * |RPM|} mana/tick (360 at
 * 256 RPM). The pump pressurizes the line: pipes in front of it (discharge) push mana away from the
 * pump at that rate whatever their fill, and pipes behind it (suction) draw mana toward it.
 * Pressure is refreshed each tick and fades within a few ticks of the pump stopping. (Pure
 * equalizing could not carry the pumped rate down a long line, nor fill a nearly full battery.)
 * <p>
 * Output-only nodes (a reactor's mana port) are always drained into pipes with room, and input-only
 * nodes (a cannon) are always fed, instead of being equalized with.
 * <p>
 * <b>Conservation:</b> each transfer subtracts exactly what the other side reported
 * as accepted, so mana is never created or destroyed in transit.
 */
public class TerrasteelManaPipeBlockEntity extends SmartBlockEntity implements IManaPipeNode, IHaveGoggleInformation {

    public static final long CAPACITY = 1_000;
    private static final int MAX_PRESSURE_HOPS = 64;
    private static final Direction[] SIDES = Direction.values();

    private long mana;
    private int pressureRate;
    private int pressureHops = Integer.MAX_VALUE;
    private long pressureStamp = Long.MIN_VALUE;
    /** Pressure from the pump's intake side: mana is drawn toward the pump (lower hops) instead of pushed away. */
    private boolean suction;
    private long lastSyncedMana = -1;
    /** Sides that are real links (bit = Direction 3D index); synced for the model's drain flanges. */
    private int linkedMask;
    /** Mana moved in the current window: out bits 0-5, in bits 6-11. Published as {@link #flow} for the pulse renderer. */
    private int flowWindow;
    private int flow;

    public TerrasteelManaPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void initialize() {
        super.initialize();
        // The block computes links when its shape updates; a freshly placed pipe records them here.
        if (level != null && !level.isClientSide) setLinkedMask(TerrasteelManaPipeBlock.linkedMask(level, worldPosition));
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        long now = level.getGameTime();
        BlockState state = getBlockState();

        tickPumps(state, now);

        int linkRate = currentLinkRate(now);
        // A pipe can arrive with less mana than all its branches request. Rotate which side goes first.
        int firstSide = Math.floorMod(now, SIDES.length);
        for (int i = 0; i < SIDES.length; i++) {
            Direction d = SIDES[(firstSide + i) % SIDES.length];
            if (!state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) continue;
            BlockPos npos = worldPosition.relative(d);
            BlockState nstate = level.getBlockState(npos);
            if (nstate.getBlock() instanceof PumpBlock) continue; // handled in tickPumps

            BlockEntity nbe = level.getBlockEntity(npos);
            if (nbe instanceof TerrasteelManaPipeBlockEntity other) {
                exchangeWithPipe(other, linkRate, now, d);
            } else {
                ManaEndpoint endpoint = ManaEndpoint.find(level, npos, d.getOpposite());
                if (endpoint != null) exchangeWithEndpoint(endpoint, linkRate, d);
            }
        }

        // Sync the fill level (goggles) and the flow directions (pulse visuals) at most every 10 ticks, when changed.
        if (now % 10 == 0) {
            boolean dirty = mana != lastSyncedMana || flowWindow != flow;
            flow = flowWindow;
            flowWindow = 0;
            if (dirty) {
                lastSyncedMana = mana;
                sendData();
            }
        }
    }

    /** Pumps whose output faces this pipe push mana into it; pumps facing away pull from it. */
    private void tickPumps(BlockState state, long now) {
        int firstSide = Math.floorMod(now, SIDES.length);
        for (int i = 0; i < SIDES.length; i++) {
            Direction d = SIDES[(firstSide + i) % SIDES.length];
            if (!state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) continue;
            BlockPos pumpPos = worldPosition.relative(d);
            BlockState pumpState = level.getBlockState(pumpPos);
            if (!(pumpState.getBlock() instanceof PumpBlock)) continue;
            if (!(level.getBlockEntity(pumpPos) instanceof KineticBlockEntity pump)) continue;
            float rpm = Math.abs(pump.getSpeed());
            if (rpm <= 0) continue;

            int rate = pumpedRate(rpm);
            if (pumpState.getBlock() instanceof dev.viridianengineering.content.pump.VEPumps.ManaPump)
                rate = (int) Math.floor(rate * VEConfig.PUMP_SPEED_MULTIPLIER.get());
            Direction pumpFacing = pumpState.getValue(PumpBlock.FACING);

            if (pumpFacing == d.getOpposite()) {
                // The pump's output faces us: pull from whatever sits behind the pump.
                BlockPos sourcePos = pumpPos.relative(d);
                long room = Math.min(rate, CAPACITY - mana);
                if (room > 0) {
                    BlockEntity sbe = level.getBlockEntity(sourcePos);
                    long moved;
                    if (sbe instanceof TerrasteelManaPipeBlockEntity sourcePipe) {
                        // The suction side is pressurized too, so it can refill at the pumped rate.
                        sourcePipe.refreshPressure(rate, 0, now, true);
                        moved = sourcePipe.extractMana(room, false);
                        if (moved > 0) sourcePipe.flowOut(d.getOpposite());
                    } else {
                        ManaEndpoint source = ManaEndpoint.find(level, sourcePos, d.getOpposite());
                        moved = source == null ? 0 : source.extract(room);
                    }
                    mana += moved;
                    if (moved > 0) { setChanged(); flowIn(d); }
                }
                refreshPressure(rate, 0, now, false);
            } else if (pumpFacing == d) {
                refreshPressure(rate, 0, now, true); // the pump draws from us
                // The pump pulls from us. If its output is a plain endpoint (not another
                // pipe, which pulls for itself), deliver straight into it.
                BlockPos targetPos = pumpPos.relative(d);
                BlockEntity tbe = level.getBlockEntity(targetPos);
                if (tbe instanceof TerrasteelManaPipeBlockEntity) continue;
                ManaEndpoint target = ManaEndpoint.find(level, targetPos, d.getOpposite());
                if (target == null) continue;
                long moved = target.insert(Math.min(rate, mana));
                mana -= moved;
                if (moved > 0) { setChanged(); flowOut(d); }
            }
        }
    }

    private void flowOut(Direction d) { flowWindow |= 1 << d.get3DDataValue(); }

    private void flowIn(Direction d) { flowWindow |= 1 << (6 + d.get3DDataValue()); }

    /** -1 mana arrives through {@code d}, +1 it leaves through it, 0 no flow (for the pulse renderer). */
    public int flowThrough(Direction d) {
        boolean out = (flow & (1 << d.get3DDataValue())) != 0, in = (flow & (1 << (6 + d.get3DDataValue()))) != 0;
        return out == in ? 0 : out ? 1 : -1;
    }

    public boolean isLinked(Direction d) { return (linkedMask & (1 << d.get3DDataValue())) != 0; }

    void setLinkedMask(int mask) {
        if (mask == linkedMask) return;
        linkedMask = mask;
        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }
    }

    private void exchangeWithPipe(TerrasteelManaPipeBlockEntity other, int linkRate, long now, Direction d) {
        boolean pressured = isPressureFresh(now);
        if (pressured && other.isPressureFresh(now) && other.suction == suction && other.pressureHops != pressureHops) {
            // pumped: discharge pushes away from the pump (to higher hops), suction toward it (lower hops)
            boolean downstream = suction ? other.pressureHops < pressureHops : other.pressureHops > pressureHops;
            if (downstream) move(other, Math.min(Math.min(linkRate, mana), CAPACITY - other.mana), d);
        } else {
            long amount = equalizingAmount(mana, CAPACITY, other.mana, CAPACITY);
            if (amount > 0) move(other, Math.min(Math.min(amount, linkRate), CAPACITY - other.mana), d);
        }
        if (pressured && pressureHops + 1 < MAX_PRESSURE_HOPS)
            other.refreshPressure(pressureRate, pressureHops + 1, now, suction);
    }

    private void move(TerrasteelManaPipeBlockEntity other, long moved, Direction d) {
        if (moved <= 0) return;
        mana -= moved;
        other.mana += moved;
        setChanged();
        other.setChanged();
        flowOut(d);
        other.flowIn(d.getOpposite());
    }

    private void exchangeWithEndpoint(ManaEndpoint endpoint, int linkRate, Direction d) {
        long amount;
        boolean pressured = level != null && isPressureFresh(level.getGameTime());
        IManaPipeNode.Role role = endpoint.role();
        long capacity = endpoint.capacity();
        // Pressure guides storage ports. Machines with a fixed direction still work on either side of a pump.
        if (role == IManaPipeNode.Role.SOURCE) {
            amount = -(CAPACITY - mana);
        } else if (role == IManaPipeNode.Role.SINK || capacity < 0) {
            amount = mana;
        } else if (pressured) {
            amount = suction ? -(CAPACITY - mana) : mana;
        } else {
            amount = equalizingAmount(mana, CAPACITY, endpoint.stored(), capacity);
        }

        if (amount > 0) {
            long moved = endpoint.insert(Math.min(amount, linkRate));
            mana -= moved;
            if (moved > 0) { setChanged(); flowOut(d); }
        } else if (amount < 0 && endpoint.canExtract()) {
            long want = Math.min(Math.min(-amount, linkRate), CAPACITY - mana);
            long moved = endpoint.extract(want);
            mana += moved;
            if (moved > 0) { setChanged(); flowIn(d); }
        }
    }

    /**
     * How much should move from side A to side B so both end at the same fill fraction.
     * Positive: A gives to B. Negative: B gives to A.
     */
    static long equalizingAmount(long a, long capA, long b, long capB) {
        if (capA <= 0 || capB <= 0) return 0;
        double x = ((double) a * capB - (double) b * capA) / (double) (capA + capB);
        return (long) (x >= 0 ? Math.floor(x) : Math.ceil(x));
    }

    // ------------------------------------------------------------------ pressure

    public static int pumpedRate(float rpm) {
        return (int) Math.floor(VEConfig.TERRASTEEL_PIPE_PASSIVE_RATE.get()
                + VEConfig.TERRASTEEL_PIPE_PUMP_RPM_MULTIPLIER.get() * Math.abs(rpm));
    }

    private int currentLinkRate(long now) {
        int passive = VEConfig.TERRASTEEL_PIPE_PASSIVE_RATE.get();
        return isPressureFresh(now) ? Math.max(passive, pressureRate) : passive;
    }

    /** Whether a running pump currently pressurizes this pipe (pumped I/O at Mana Batteries). */
    public boolean isPressurized() {
        return level != null && isPressureFresh(level.getGameTime()) && pressureRate > VEConfig.TERRASTEEL_PIPE_PASSIVE_RATE.get();
    }

    private boolean isPressureFresh(long now) {
        // never pumped: Long.MIN_VALUE would overflow the subtraction below and read as fresh
        return pressureStamp != Long.MIN_VALUE && now - pressureStamp <= 1;
    }

    /**
     * Accept pressure from a pump (hops 0) or an upstream pipe. The path closest to a
     * pump wins; hop counts are capped, so pressure dies out within a few ticks of the
     * pump stopping even when pipes form loops.
     */
    void refreshPressure(int rate, int hops, long now, boolean suction) {
        if (!isPressureFresh(now) || hops <= pressureHops) {
            pressureRate = rate;
            pressureHops = hops;
            pressureStamp = now;
            this.suction = suction;
        }
    }

    // ------------------------------------------------------------------ IManaPipeNode

    @Override public long getStoredMana() { return mana; }

    @Override public long getManaCapacity() { return CAPACITY; }

    @Override
    public long insertMana(long amount, boolean simulate) {
        long accepted = Math.max(0, Math.min(amount, CAPACITY - mana));
        if (!simulate && accepted > 0) {
            mana += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long extractMana(long amount, boolean simulate) {
        long removed = Math.max(0, Math.min(amount, mana));
        if (!simulate && removed > 0) {
            mana -= removed;
            setChanged();
        }
        return removed;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("Mana", mana);
        tag.putInt("Linked", linkedMask);
        if (clientPacket) tag.putInt("Flow", flow);
        if (!clientPacket) {
            tag.putInt("PressureRate", pressureRate);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana = Math.max(0, Math.min(CAPACITY, tag.getLong("Mana")));
        int linked = tag.getInt("Linked");
        if (clientPacket) {
            flow = tag.getInt("Flow");
            if (linked != linkedMask && level != null) { // drain vs rim may change: rebuild the chunk mesh
                linkedMask = linked;
                requestModelDataUpdate();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
            }
        }
        linkedMask = linked;
        if (!clientPacket) pressureRate = tag.getInt("PressureRate");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("viridian_engineering.goggles.mana_pipe")
                .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ")
                .append(Component.literal(mana + " / " + CAPACITY).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" mana").withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }
}
