package dev.viridianengineering.content.battery;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.MultiblockIndex;
import dev.viridianengineering.content.manapipe.TerrasteelManaPipeBlockEntity;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Terrasteel Mana Battery (spec 3.4). The controller sits on a perimeter side of a 3x3 base layer
 * (with 8 Mana Battery Casings); up to 7 full 3x3 casing layers stack above. {@code validLayers}
 * counts the base plus the contiguous complete layers above it; fewer than 2 is unformed.
 * Capacity = layers x 2,500,000; the mana lives here and is clamped when the capacity shrinks.
 * I/O (pipes and spreaders, through any battery block) is capped at 1,000 mana/t, or 4,000 mana/t
 * while a running pump pressurizes a pipe touching the battery.
 * <p>
 * Breaking any battery block rolls for a detonation ({@link BatteryDetonation}); an UNSTABLE battery
 * has I/O disabled and cannot be broken until it detonates.
 */
public class BatteryControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final MultiblockIndex INDEX = new MultiblockIndex();

    private int validLayers;
    private boolean pumped;
    public final ManaBuffer mana = new ManaBuffer(this, this::maxCapacity, true, true).withTickLimit(this::ioLimit);

    // ---- failure (UNSTABLE) state, mirrored from the SavedData entry so the client can render it
    private boolean unstable;
    @Nullable private UUID failureId;
    private long detonationTick;
    private double failureFill;
    private int failureLayers;

    public BatteryControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Direction front() { return getBlockState().getValue(HorizontalDirectionalBlock.FACING); }

    /** Centre of the base layer. */
    public BlockPos baseCenter() { return worldPosition.relative(front().getOpposite()); }

    public int validLayers() { return validLayers; }

    public boolean isFormed() { return validLayers >= VEConfig.MIN_LAYERS.get(); }

    public boolean isUnstable() { return unstable; }

    public long maxCapacity() { return (long) validLayers * VEConfig.MANA_PER_LAYER.get(); }

    public double fillFraction() {
        long cap = maxCapacity();
        return cap <= 0 ? 0 : Math.min(1.0, Math.max(0.0, (double) mana.getStoredMana() / cap));
    }

    public boolean isPumped() { return pumped; }

    public long ioLimit() {
        if (unstable || !isFormed()) return 0;
        return pumped ? VEConfig.PUMPED_IO_RATE.get() : VEConfig.PASSIVE_IO_RATE.get();
    }

    @Nullable public UUID failureId() { return failureId; }

    public long detonationTick() { return detonationTick; }

    public double failureFill() { return failureFill; }

    public int failureLayers() { return failureLayers; }

    // ------------------------------------------------------------------ structure

    @Override
    public void initialize() {
        super.initialize();
        if (!level.isClientSide) validate();
    }

    private long lastSyncedMana = -1;

    /** Clients (goggles, the glowing level behind the windows) get the stored mana every 10 ticks when it changed. */
    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && level.getGameTime() % 10 == 0 && mana.getStoredMana() != lastSyncedMana) {
            lastSyncedMana = mana.getStoredMana();
            sendData();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide && !unstable) validate();
    }

    /** All blocks of the battery as currently formed (controller included). */
    public List<BlockPos> members() {
        return members(validLayers);
    }

    public List<BlockPos> members(int layers) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = baseCenter();
        for (int y = 0; y < Math.max(1, layers); y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    out.add(c.offset(dx, y, dz));
        return out;
    }

    private final java.util.Set<BlockPos> marked = new java.util.HashSet<>();
    private final List<BlockPos> ports = new ArrayList<>();

    public List<BlockPos> ports() { return ports; }

    public void validate() {
        BlockPos c = baseCenter();
        int layers = 0;
        List<BlockPos> foundPorts = new ArrayList<>();
        if (layerComplete(c, true, foundPorts)) {
            layers = 1;
            while (layers < VEConfig.MAX_LAYERS.get() && layerComplete(c.above(layers), false, foundPorts)) layers++;
        }
        // ports in a layer that did not count are not part of the battery
        int top = c.getY() + layers;
        foundPorts.removeIf(p -> p.getY() >= top);
        int was = validLayers;
        validLayers = layers;
        ports.clear();
        if (isFormed()) ports.addAll(foundPorts);
        mana.clamp();
        if (isFormed()) INDEX.claim(level, worldPosition, members());
        else INDEX.release(level, worldPosition);

        // casings go invisible while formed; the controller renders the column
        java.util.Set<BlockPos> now = new java.util.HashSet<>(isFormed() ? members() : List.of());
        for (BlockPos p : marked) if (!now.contains(p)) dev.viridianengineering.content.FormedPartBlock.mark(level, p, false);
        for (BlockPos p : now) dev.viridianengineering.content.FormedPartBlock.mark(level, p, true);
        marked.clear();
        marked.addAll(now);

        pumped = false;
        scan:
        for (BlockPos p : ports)
            for (Direction d : Direction.values())
                if (level.getBlockEntity(p.relative(d)) instanceof TerrasteelManaPipeBlockEntity pipe && pipe.isPressurized()) {
                    pumped = true;
                    break scan;
                }
        if (was != validLayers) sendData();
        setChanged();
    }

    /** A 3x3 layer: casing everywhere, except that any outer position may hold the battery port. */
    private boolean layerComplete(BlockPos center, boolean base, List<BlockPos> portsOut) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                if (base && p.equals(worldPosition)) continue;
                BlockState s = level.getBlockState(p);
                if (s.is(VEBlocks.TERRASTEEL_MANA_BATTERY_CASING.get())) continue;
                if ((dx != 0 || dz != 0) && s.is(VEBlocks.TERRASTEEL_MANA_BATTERY_PORT.get())) { portsOut.add(p.immutable()); continue; }
                return false;
            }
        return true;
    }

    /** A formed casing was removed (safe path or explosion): re-check at once. */
    public static void partRemoved(Level level, BlockPos pos) {
        BatteryControllerBlockEntity b = owning(level, pos);
        if (b != null && !b.isUnstable()) b.validate();
    }

    /** Safe path of a break: the battery stops working at once (the next validation recounts layers). */
    void markUnformed() {
        validLayers = 0;
        INDEX.release(level, worldPosition);
        sendData();
    }

    // ------------------------------------------------------------------ failure

    void beginFailure(UUID id, long deadline, double fill, int layers) {
        unstable = true;
        failureId = id;
        detonationTick = deadline;
        failureFill = fill;
        failureLayers = layers;
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.terrasteel_mana_battery_controller")));
        if (unstable) {
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.battery.unstable"))
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            return true;
        }
        if (!isFormed()) {
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.battery.unformed",
                    VEConfig.MIN_LAYERS.get())).withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.battery.layers",
                    validLayers, VEConfig.MAX_LAYERS.get())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), maxCapacity())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.battery.fill",
                String.format("%.1f", fillFraction() * 100))).withStyle(fillFraction() > 0.5 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        if (isFormed()) tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.battery.io",
                ioLimit(), Component.translatable(pumped ? "viridian_engineering.goggles.battery.pumped"
                        : "viridian_engineering.goggles.battery.passive"))).withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        // fixed (Create caches it before the layer count syncs): the tallest 3x3 stack plus its cap, and the
        // failure sequence's ground cracks, which spread up to 42 blocks out
        return new AABB(worldPosition).inflate(44, 12, 44).move(0, 4, 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncClientIndex();
    }

    private void syncClientIndex() {
        if (level == null || !level.isClientSide) return;
        if (!isFormed()) INDEX.release(level, worldPosition);
        else if (!INDEX.membersOf(level, worldPosition).equals(marked)) INDEX.claim(level, worldPosition, marked);
    }

    @Override
    public void remove() {
        super.remove();
        if (level == null) return;
        INDEX.release(level, worldPosition);
        if (!level.isClientSide) for (BlockPos p : marked) dev.viridianengineering.content.FormedPartBlock.mark(level, p, false);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) INDEX.release(level, worldPosition);
    }

    @Nullable
    public static BatteryControllerBlockEntity owning(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BatteryControllerBlockEntity self) return self;
        BlockPos c = INDEX.controllerOf(level, pos);
        return c != null && level.getBlockEntity(c) instanceof BatteryControllerBlockEntity b ? b : null;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        tag.putInt("Layers", validLayers);
        tag.putBoolean("Pumped", pumped);
        tag.putLongArray("Marked", marked.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Ports", ports.stream().mapToLong(BlockPos::asLong).toArray());
        if (unstable) {
            CompoundTag f = new CompoundTag();
            if (failureId != null) f.putUUID("Id", failureId);
            f.putLong("Deadline", detonationTick);
            f.putDouble("Fill", failureFill);
            f.putInt("Layers", failureLayers);
            tag.put("Failure", f);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        validLayers = tag.getInt("Layers");
        mana.read(tag, "Mana"); // after validLayers, so the capacity is known
        pumped = tag.getBoolean("Pumped");
        marked.clear();
        for (long l : tag.getLongArray("Marked")) marked.add(BlockPos.of(l));
        ports.clear();
        for (long l : tag.getLongArray("Ports")) ports.add(BlockPos.of(l));
        syncClientIndex();
        unstable = tag.contains("Failure");
        if (unstable) {
            CompoundTag f = tag.getCompound("Failure");
            failureId = f.hasUUID("Id") ? f.getUUID("Id") : null;
            detonationTick = f.getLong("Deadline");
            failureFill = f.getDouble("Fill");
            failureLayers = f.getInt("Layers");
        } else {
            failureId = null;
        }
    }
}
