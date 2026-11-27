package dev.viridianengineering.content.reactor;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Terrasteel Mana Reactor (spec 3.3). A 3x3x3: this controller at the centre of the front face, the
 * reactor core in the middle, and every other block Terrasteel Casing or one of the three port kinds
 * (at least one lava intake, one shaft input and one mana output).
 * <p>
 * While formed, the shaft draws {@code reactorStressPerRpm} (16) SU per RPM. At |RPM| >= 64 with at
 * least 5 mB of lava it burns {@code 5 * RPM/256} mB/t and makes {@code 125 * RPM/256} mana/t
 * (25,000 mana per bucket) into its 100,000 buffer. A full buffer pauses burning.
 */
public class ReactorControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public final ManaBuffer mana = new ManaBuffer(this, () -> (long) VEConfig.REACTOR_MANA_BUFFER.get() * factor(), false, true);
    public final FluidTank lava = new FluidTank(4_000, f -> f.getFluid().is(FluidTags.LAVA)) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    private boolean formed;
    /** Bowl radius: 3 = 7x7 (the base size) up to {@code reactorMaxRadius}. Output scales with {@link #factor()}. */
    private int radius = 3;
    private final Set<BlockPos> members = new HashSet<>();
    private final List<BlockPos> shaftPorts = new ArrayList<>();
    private double lavaDebt, manaCredit;
    /** Client display (synced): rpm actually used, mB/t and mana/t of the last tick. */
    private float rpm, lavaRate, manaRate;
    private boolean running;
    private int syncCooldown;

    public ReactorControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean isFormed() { return formed; }

    public boolean isRunning() { return running; }

    public boolean hasMember(BlockPos pos) { return formed && members.contains(pos); }

    public Direction front() { return getBlockState().getValue(HorizontalDirectionalBlock.FACING); }

    public int radius() { return radius; }

    /**
     * Size multiplier (not in the spec, user request): 1 for 7x7, 2 for 9x9, ... 5 for 15x15. Lava burn,
     * mana output, stress and the buffers all scale by it, so the spec's locked ratios
     * (25,000 mana per bucket, 16 SU per RPM per 125 mana/t) hold at every size.
     */
    public int factor() { return radius - 2; }

    /** The core's position: the centre of the bowl, on the controller's layer, {@code radius} blocks in. */
    public BlockPos coreCenter() { return worldPosition.relative(front().getOpposite(), radius); }

    /** The smallest bowl whose core and pedestal line up with this controller; 3 if none does. */
    private int detectRadius() {
        Direction back = front().getOpposite();
        for (int r = 3; r <= VEConfig.REACTOR_MAX_RADIUS.get(); r++) {
            BlockPos core = worldPosition.relative(back, r);
            if (level.getBlockState(core).is(VEBlocks.TERRASTEEL_MANA_REACTOR_CORE.get()) && isCasing(level.getBlockState(core.below())))
                return r;
        }
        return 3;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (!level.isClientSide) validate();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide) validate();
    }

    /** What a position of the bowl must hold (relative to the core; dy 0 = controller layer, -1 = ground). */
    public enum Slot { OUTER, INNER, PEDESTAL, CORE, AIR, CONTROLLER }

    public static Slot slotAt(int dx, int dy, int dz) {
        return slotAt(dx, dy, dz, 3);
    }

    /** The same layout at any radius {@code r}: outer wall at ring r, ground ring at r - 1, open pit inside. */
    public static Slot slotAt(int dx, int dy, int dz, int r) {
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring == r) return Slot.OUTER;
        if (dy == 0) return dx == 0 && dz == 0 ? Slot.CORE : Slot.AIR;
        if (ring == r - 1) return Slot.INNER;
        return dx == 0 && dz == 0 ? Slot.PEDESTAL : Slot.AIR;
    }

    private static boolean isCasing(BlockState s) { return s.is(VEBlocks.TERRASTEEL_REACTOR_CASING.get()); }

    /**
     * Checks the 7x7, 2-high bowl: outer wall of casing and ports (lava, mana, shaft) around both
     * layers with this controller in the middle of one side; on the ground a 5x5 ring of casing or
     * shaft ports around an open 3x3 pit with a casing pedestal in the middle; the reactor core floats
     * above the pedestal; the rest of the bowl is air. Needs at least one lava, mana and shaft port.
     */
    public void validate() {
        int oldRadius = radius;
        radius = detectRadius();
        lava.setCapacity(VEConfig.REACTOR_LAVA_TANK_MB.get() * factor());
        if (lava.getFluidAmount() > lava.getCapacity()) lava.drain(lava.getFluidAmount() - lava.getCapacity(),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        mana.clamp();
        BlockPos c = coreCenter();
        int r = radius;
        Set<BlockPos> found = new HashSet<>();
        List<BlockPos> shafts = new ArrayList<>();
        int lavaPorts = 0, manaPorts = 0;
        boolean ok = true;
        scan:
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (p.equals(worldPosition)) continue;
                    BlockState s = level.getBlockState(p);
                    switch (slotAt(dx, dy, dz, r)) {
                        case OUTER -> {
                            if (isCasing(s)) found.add(p);
                            else if (s.is(VEBlocks.TERRASTEEL_LAVA_PORT.get())) { lavaPorts++; found.add(p); }
                            else if (s.is(VEBlocks.TERRASTEEL_MANA_PORT.get())) { manaPorts++; found.add(p); }
                            else if (s.is(VEBlocks.TERRASTEEL_SHAFT_PORT.get())) { shafts.add(p.immutable()); found.add(p); }
                            else { ok = false; break scan; }
                        }
                        case INNER -> {
                            if (isCasing(s)) found.add(p);
                            else if (s.is(VEBlocks.TERRASTEEL_SHAFT_PORT.get())) { shafts.add(p.immutable()); found.add(p); }
                            else { ok = false; break scan; }
                        }
                        case PEDESTAL -> { if (isCasing(s)) found.add(p); else { ok = false; break scan; } }
                        case CORE -> { if (s.is(VEBlocks.TERRASTEEL_MANA_REACTOR_CORE.get())) found.add(p); else { ok = false; break scan; } }
                        case AIR -> { if (!s.isAir()) { ok = false; break scan; } }
                        default -> {}
                    }
                }
        ok &= lavaPorts > 0 && manaPorts > 0 && !shafts.isEmpty();
        found.add(worldPosition);

        boolean wasFormed = formed;
        List<BlockPos> oldShafts = new ArrayList<>(shaftPorts);
        Set<BlockPos> oldMembers = new HashSet<>(members);
        formed = ok;
        members.clear();
        shaftPorts.clear();
        if (ok) {
            members.addAll(found);
            shaftPorts.addAll(shafts);
            INDEX.claim(level, worldPosition, members);
        } else {
            INDEX.release(level, worldPosition);
        }
        // casing and core models go invisible while formed (the controller renders the structure)
        for (BlockPos p : oldMembers) if (!members.contains(p)) dev.viridianengineering.content.FormedPartBlock.mark(level, p, false);
        for (BlockPos p : members) dev.viridianengineering.content.FormedPartBlock.mark(level, p, true);
        if (ok) oldShafts.removeAll(shafts);
        releaseShafts(oldShafts);
        if (ok) for (BlockPos p : shafts)
            if (level.getBlockEntity(p) instanceof ReactorShaftPortBlockEntity port) port.setReactor(worldPosition, factor());
        if (wasFormed != formed || oldRadius != radius) {
            if (!formed) running = false;
            sendData();
        }
        setChanged();
    }

    @Override
    protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        // Create caches this box the first time it is asked, which on a client is before the formed size
        // has synced: a box built from radius/coreCenter() stayed controller-sized and the whole bowl was
        // culled as soon as the controller block left the view. So: a fixed box that holds the largest bowl
        // (the core is up to maxRadius from the controller, the bowl another maxRadius beyond) and the spires.
        int r = 2 * dev.viridianengineering.config.VEConfig.REACTOR_MAX_RADIUS.get() + 2;
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(r, 5, r);
    }

    /** A formed casing or core was removed: re-check at once. */
    public static void partRemoved(net.minecraft.world.level.Level level, BlockPos pos) {
        ReactorControllerBlockEntity r = owning(level, pos);
        if (r != null) r.validate();
    }

    /** The fastest, not over-stressed shaft port drives the reactor. */
    private float shaftRpm() {
        float best = 0;
        for (BlockPos p : shaftPorts)
            if (level.getBlockEntity(p) instanceof ReactorShaftPortBlockEntity port && !port.isOverStressed())
                best = Math.max(best, Math.abs(port.getSpeed()));
        return best;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        float speed = formed ? shaftRpm() : 0;
        double lavaPerTick = VEConfig.REACTOR_LAVA_MB_PER_TICK_AT_256.get() * speed / 256.0 * factor();
        double manaPerMb = VEConfig.REACTOR_LAVA_MB_PER_BUCKET_MANA.get() / 1000.0;
        boolean run = formed && speed >= VEConfig.REACTOR_MIN_RPM.get() && lava.getFluidAmount() >= 5 && mana.free() > 0;

        float lr = 0, mr = 0;
        if (run) {
            lavaDebt += lavaPerTick;
            int burn = (int) Math.min(lavaDebt, lava.getFluidAmount());
            if (burn > 0) {
                lava.drain(burn, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                lavaDebt -= burn;
            }
            manaCredit += lavaPerTick * manaPerMb;
            long make = (long) manaCredit;
            if (make > 0) {
                mana.produce(make);
                manaCredit -= make;
            }
            lr = (float) lavaPerTick;
            mr = (float) (lavaPerTick * manaPerMb);
        }

        boolean changed = run != running || Math.abs(speed - rpm) > 0.01f;
        running = run;
        rpm = speed;
        lavaRate = lr;
        manaRate = mr;
        if (changed || --syncCooldown <= 0) {
            syncCooldown = 20;
            sendData();
        }
    }

    // ------------------------------------------------------------------ data

    public float rpm() { return rpm; }

    public float lavaRate() { return lavaRate; }

    public float manaRate() { return manaRate; }

    public float stress() { return formed ? rpm * VEConfig.REACTOR_STRESS_PER_RPM.get().floatValue() * factor() : 0; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.terrasteel_mana_reactor_controller")));
        if (!formed) {
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.reactor.unformed")).withStyle(ChatFormatting.RED));
            return true;
        }
        tooltip.add(line("viridian_engineering.goggles.reactor.size", ChatFormatting.GREEN, 2 * radius + 1, 2 * radius + 1, factor()));
        tooltip.add(line("viridian_engineering.goggles.reactor.rpm", ChatFormatting.GOLD, String.format("%.0f", rpm)));
        tooltip.add(line("viridian_engineering.goggles.reactor.su", ChatFormatting.GOLD, String.format("%.0f", stress())));
        tooltip.add(line("viridian_engineering.goggles.reactor.lava", ChatFormatting.RED, String.format("%.2f", lavaRate),
                lava.getFluidAmount(), lava.getCapacity()));
        tooltip.add(line("viridian_engineering.goggles.reactor.mana", ChatFormatting.AQUA, String.format("%.1f", manaRate),
                mana.getStoredMana(), mana.getManaCapacity()));
        if (!running) tooltip.add(Component.literal("     ").append(Component.translatable(rpm < VEConfig.REACTOR_MIN_RPM.get()
                ? "viridian_engineering.goggles.reactor.slow" : lava.getFluidAmount() < 5
                ? "viridian_engineering.goggles.reactor.no_lava" : "viridian_engineering.goggles.reactor.full",
                VEConfig.REACTOR_MIN_RPM.get())).withStyle(ChatFormatting.GRAY));
        return true;
    }

    private static Component line(String key, ChatFormatting color, Object... args) {
        return Component.literal("     ").append(Component.translatable(key, args).withStyle(color));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        tag.put("Lava", lava.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("LavaDebt", lavaDebt);
        tag.putDouble("ManaCredit", manaCredit);
        tag.putBoolean("Formed", formed);
        tag.putInt("Radius", radius);
        tag.putLongArray("Members", members.stream().mapToLong(BlockPos::asLong).toArray());
        if (clientPacket) {
            tag.putFloat("Rpm", rpm);
            tag.putFloat("LavaRate", lavaRate);
            tag.putFloat("ManaRate", manaRate);
            tag.putBoolean("Running", running);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        lava.readFromNBT(registries, tag.getCompound("Lava"));
        lavaDebt = tag.getDouble("LavaDebt");
        manaCredit = tag.getDouble("ManaCredit");
        formed = tag.getBoolean("Formed");
        radius = tag.contains("Radius") ? Math.max(3, tag.getInt("Radius")) : 3;
        members.clear();
        for (long l : tag.getLongArray("Members")) members.add(BlockPos.of(l));
        syncClientIndex();
        if (clientPacket) {
            rpm = tag.getFloat("Rpm");
            lavaRate = tag.getFloat("LavaRate");
            manaRate = tag.getFloat("ManaRate");
            running = tag.getBoolean("Running");
        }
    }

    public static final dev.viridianengineering.content.MultiblockIndex INDEX = new dev.viridianengineering.content.MultiblockIndex();

    /** The formed reactor that owns the block at {@code pos}, or null. */
    @Nullable
    public static ReactorControllerBlockEntity owning(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockPos c = INDEX.controllerOf(level, pos);
        return c != null && level.getBlockEntity(c) instanceof ReactorControllerBlockEntity r && r.hasMember(pos) ? r : null;
    }

    private void releaseShafts(List<BlockPos> ports) {
        for (BlockPos p : ports)
            if (level.getBlockEntity(p) instanceof ReactorShaftPortBlockEntity port) port.setReactor(null, 1);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncClientIndex();
    }

    private void syncClientIndex() {
        if (level == null || !level.isClientSide) return;
        if (!formed) INDEX.release(level, worldPosition);
        else if (!INDEX.membersOf(level, worldPosition).equals(members)) INDEX.claim(level, worldPosition, members);
    }

    @Override
    public void remove() {
        super.remove();
        if (level == null) return;
        INDEX.release(level, worldPosition);
        if (!level.isClientSide) {
            releaseShafts(new ArrayList<>(shaftPorts));
            for (BlockPos p : members) if (!p.equals(worldPosition)) dev.viridianengineering.content.FormedPartBlock.mark(level, p, false);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) INDEX.release(level, worldPosition);
    }
}
