package dev.viridianengineering.content.corporea;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VECorporea;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import velrondevs.botania.api.mana.ManaReceiver;
import velrondevs.botania.api.BotaniaCapabilities;
import java.lang.ref.WeakReference;
import java.util.*;

public class ArcaneSparkEntity extends Entity implements ManaReceiver {
    private static final EntityDataAccessor<Integer> MANA = SynchedEntityData.defineId(ArcaneSparkEntity.class, EntityDataSerializers.INT);
    private static final Map<Level,List<WeakReference<ArcaneSparkEntity>>> LOADED = new WeakHashMap<>();
    private MachineEndpointKey attachment;
    private long lastRouted = Long.MIN_VALUE;
    private boolean registered;
    public ArcaneSparkEntity(EntityType<? extends ArcaneSparkEntity> type, Level level) { super(type,level); noPhysics=true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(MANA,0); }
    public void attach(BlockPos pos) { attachment=SableCompat.endpointKey(level(),pos,Direction.UP); setPos(pos.getX()+.5,pos.getY()+1.25,pos.getZ()+.5); }
    public BlockPos attachmentPos() { return attachment==null ? null : SableCompat.resolve(level(),attachment); }
    public static boolean supports(Level level,BlockPos pos) {
        var state=level.getBlockState(pos);
        return state.is(VEBlocks.CORPOREA_INPUT_PORT.get()) || state.is(VEBlocks.CORPOREA_OUTPUT_PORT.get())
                || state.is(VEBlocks.CORPOREA_MACHINE_PORT.get()) || state.is(VEStorage.ARCHIVE_PYLON.get());
    }

    public static void moved(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                             BlockPos oldPos, BlockPos newPos) {
        if (oldLevel != newLevel) return;
        MachineEndpointKey oldKey = SableCompat.endpointKey(oldLevel, oldPos, Direction.UP);
        for (ArcaneSparkEntity spark : loaded(oldLevel)) {
            if (oldKey.equals(spark.attachment)) spark.attach(newPos);
        }
        invalidateIndex(oldLevel);
    }
    public static List<ArcaneSparkEntity> loaded(Level level) {
        var list=LOADED.computeIfAbsent(level,k->new ArrayList<>());
        list.removeIf(r->r.get()==null || r.get().isRemoved());
        return list.stream().map(WeakReference::get).filter(Objects::nonNull).toList();
    }
    public static ArcaneSparkEntity at(Level level,BlockPos pos) {
        return index(level).byPos.get(pos);
    }

    /**
     * Per-tick spatial index: sparks bucketed into cells as wide as the maximum link range, so a
     * link lookup only checks the 27 neighbouring cells instead of every loaded spark.
     */
    private static final class SparkIndex {
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, ArcaneSparkEntity> byPos = new HashMap<>();
        final Map<Long, List<ArcaneSparkEntity>> cells = new HashMap<>();
        final Map<ArcaneSparkEntity, List<ArcaneSparkEntity>> links = new IdentityHashMap<>();
        int cellSize = 48;
    }
    private static final Map<Level, SparkIndex> INDEX = new WeakHashMap<>();

    /** Placement, removal and mana changes (which change range) rebuild the index on next use. */
    public static void invalidateIndex(Level level) {
        SparkIndex idx = INDEX.get(level);
        if (idx != null) idx.tick = Long.MIN_VALUE;
    }

    private static long cellKey(int cx, int cy, int cz) {
        return ((long) cx & 0x1FFFFF) | (((long) cy & 0x1FFFFF) << 21) | (((long) cz & 0x1FFFFF) << 42);
    }

    private static SparkIndex index(Level level) {
        SparkIndex idx = INDEX.computeIfAbsent(level, l -> new SparkIndex());
        long now = level.getGameTime();
        if (idx.tick == now) return idx;
        idx.tick = now;
        idx.byPos.clear(); idx.cells.clear(); idx.links.clear();
        idx.cellSize = Math.max(1, (int) Math.ceil(VEConfig.ARCANE_SPARK_MAX_RANGE.get()));
        for (ArcaneSparkEntity spark : loaded(level)) {
            BlockPos p = spark.attachmentPos();
            if (p == null) continue;
            idx.byPos.putIfAbsent(p, spark);
            idx.cells.computeIfAbsent(cellKey(Math.floorDiv(p.getX(), idx.cellSize), Math.floorDiv(p.getY(), idx.cellSize),
                    Math.floorDiv(p.getZ(), idx.cellSize)), k -> new ArrayList<>()).add(spark);
        }
        return idx;
    }

    /** Sparks this one can currently link to (same ship or both on terrain, both in range, chunks loaded). */
    public static List<ArcaneSparkEntity> linksOf(ArcaneSparkEntity spark) {
        SparkIndex idx = index(spark.level());
        List<ArcaneSparkEntity> cached = idx.links.get(spark);
        if (cached != null) return cached;
        List<ArcaneSparkEntity> out = new ArrayList<>();
        BlockPos p = spark.attachmentPos();
        if (p != null) {
            int cx = Math.floorDiv(p.getX(), idx.cellSize), cy = Math.floorDiv(p.getY(), idx.cellSize), cz = Math.floorDiv(p.getZ(), idx.cellSize);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                List<ArcaneSparkEntity> cell = idx.cells.get(cellKey(cx + dx, cy + dy, cz + dz));
                if (cell == null) continue;
                for (ArcaneSparkEntity other : cell) if (spark.canLink(other)) out.add(other);
            }
        }
        List<ArcaneSparkEntity> result = List.copyOf(out);
        idx.links.put(spark, result);
        return result;
    }
    public double range() {
        return Math.min(VEConfig.ARCANE_SPARK_MAX_RANGE.get(),VEConfig.ARCANE_SPARK_BASE_RANGE.get()+(double)getCurrentMana()/VEConfig.ARCANE_SPARK_MANA_PER_BLOCK.get());
    }
    public boolean canLink(ArcaneSparkEntity other) {
        if (other==this || attachment==null || other.attachment==null) return false;
        if (!Objects.equals(attachment.sableSubLevelId(),other.attachment.sableSubLevelId())) return false;
        BlockPos a=attachmentPos(), b=other.attachmentPos();
        return a!=null && b!=null && level().hasChunkAt(a) && level().hasChunkAt(b)
                && a.distSqr(b)<=Math.pow(Math.min(range(),other.range()),2);
    }
    public void routed() {
        if (lastRouted!=level().getGameTime()) {
            lastRouted=level().getGameTime();
            receiveMana(-VEConfig.ARCANE_SPARK_ACTIVE_DRAIN_PER_TICK.get());
        }
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!registered) { LOADED.computeIfAbsent(level(),k->new ArrayList<>()).add(new WeakReference<>(this)); registered=true; invalidateIndex(level()); BlockPos attached=attachmentPos(); if (attached!=null) level().invalidateCapabilities(attached); }
        BlockPos pos=attachmentPos();
        if (pos==null) { discard(); return; }
        if (!level().hasChunkAt(pos)) return;
        if (!supports(level(),pos)) { spawnAtLocation(VECorporea.ARCANE_SPARK.get()); discard(); return; }
        setPos(pos.getX()+.5,pos.getY()+1.25,pos.getZ()+.5);
    }
    @Override public void remove(RemovalReason reason) {
        BlockPos pos=attachmentPos(); super.remove(reason); invalidateIndex(level());
        if (!level().isClientSide && pos!=null) level().invalidateCapabilities(pos);
    }
    @Override public boolean isPickable() { return true; }
    @Override public InteractionResult interact(Player player,InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                chargeFrom(player.getItemInHand(hand));
                player.displayClientMessage(Component.translatable("gui.viridian_engineering.spark_status",
                        getCurrentMana(), String.format(Locale.ROOT, "%.1f", range()), linksOf(this).size()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) { spawnAtLocation(VECorporea.ARCANE_SPARK.get()); discard(); }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public int chargeFrom(ItemStack held) {
        if (level().isClientSide || held.isEmpty()) return 0;
        var mana = held.getCapability(BotaniaCapabilities.MANA_ITEM);
        if (mana == null || mana.isNoExport() || !mana.canExportManaToItem(VECorporea.ARCANE_SPARK.asStack())) return 0;
        int available = mana.getMana();
        int request = Math.max(0, Math.min(available, VEConfig.ARCANE_SPARK_CAPACITY.get() - getCurrentMana()));
        if (request == 0) return 0;
        mana.addMana(-request);
        int transferred = Math.clamp(available - mana.getMana(), 0, request);
        receiveMana(transferred);
        return transferred;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mana",getCurrentMana());
        if (attachment!=null) MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE,attachment).result().ifPresent(v->tag.put("Attachment",v));
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(MANA,Math.clamp(tag.getInt("Mana"),0,VEConfig.ARCANE_SPARK_CAPACITY.get()));
        attachment=MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE,tag.get("Attachment")).result().orElse(null);
    }
    @Override public Level getManaReceiverLevel() { return level(); }
    @Override public BlockPos getManaReceiverPos() { BlockPos pos=attachmentPos(); return pos==null ? blockPosition():pos; }
    @Override public int getCurrentMana() { return entityData.get(MANA); }
    @Override public boolean isFull() { return getCurrentMana()>=VEConfig.ARCANE_SPARK_CAPACITY.get(); }
    @Override public void receiveMana(int amount) {
        if (level().isClientSide) return;
        int before=getCurrentMana();
        int after=(int)Math.clamp((long)before+amount,0,VEConfig.ARCANE_SPARK_CAPACITY.get());
        entityData.set(MANA,after);
        if (rangeFor(before) != rangeFor(after)) invalidateIndex(level());
    }
    private static double rangeFor(int mana) {
        return Math.min(VEConfig.ARCANE_SPARK_MAX_RANGE.get(),VEConfig.ARCANE_SPARK_BASE_RANGE.get()+(double)mana/VEConfig.ARCANE_SPARK_MANA_PER_BLOCK.get());
    }
    @Override public boolean canReceiveManaFromBursts() { return true; }
}
