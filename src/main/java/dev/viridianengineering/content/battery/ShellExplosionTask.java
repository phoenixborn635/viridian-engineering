package dev.viridianengineering.content.battery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spec 3.4: removes the blast sphere shell by shell, from r = 0 outward, so a radius-200 detonation
 * (~33.5M positions) never stalls a tick. A shell r holds the positions with (r-1)^2 < d^2 <= r^2.
 * Each tick takes about (radius+1)/30 shells (so ~30 ticks in all, inside the spec's 20-40), but stops
 * early at {@code batteryShellBlocksPerTick} removals (or 8x that many positions visited) and resumes
 * from its cursor next tick. Unloaded chunks are skipped, unbreakable blocks (bedrock) are kept, and
 * every tick's batch is offered to protection mods as an {@link ExplosionEvent.Detonate} first, so
 * claims can veto blocks. Destroyed containers spill their contents.
 */
public final class ShellExplosionTask {

    public static final Codec<ShellExplosionTask> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(t -> t.id),
            BlockPos.CODEC.fieldOf("center").forGetter(t -> t.center),
            Codec.INT.fieldOf("radius").forGetter(t -> t.radius),
            Codec.INT.fieldOf("shell").forGetter(t -> t.shell),
            ShipTarget.CODEC.listOf().optionalFieldOf("ships").forGetter(t -> t.shipTargets)
    ).apply(i, ShellExplosionTask::new));

    public final UUID id;
    public final BlockPos center;
    public final int radius;
    private int shell;
    // cursor inside the current shell (not saved: a restart redoes the current shell)
    private int cx = Integer.MIN_VALUE, cz;
    private long removedTotal;
    private long worstStepNanos;
    private Optional<List<ShipTarget>> shipTargets;
    private ArrayDeque<ShipScan> shipScans;

    private record ShipTarget(UUID id, Pose3d pose) {
        private static final Codec<ShipTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(ShipTarget::id),
                Pose3d.CODEC.fieldOf("pose").forGetter(ShipTarget::pose)
        ).apply(i, ShipTarget::new));
    }

    private record ShipScan(ShipTarget target, Iterator<BlockPos> positions) {}

    public ShellExplosionTask(UUID id, BlockPos center, int radius, int shell) {
        this(id, center, radius, shell, Optional.empty());
    }

    private ShellExplosionTask(UUID id, BlockPos center, int radius, int shell, Optional<List<ShipTarget>> ships) {
        this.id = id;
        this.center = center.immutable();
        this.radius = radius;
        this.shell = shell;
        this.shipTargets = ships;
    }

    public boolean isDone() { return shell > radius && shipScans != null && shipScans.isEmpty(); }

    public int shell() { return shell; }

    public long removedTotal() { return removedTotal; }

    public long worstStepNanos() { return worstStepNanos; }

    public int shellsPerTick() { return Math.max(1, (int) Math.ceil((radius + 1) / 30.0)); }

    /** Runs one tick's worth. @return true when the whole sphere is done. */
    public boolean step(ServerLevel level) {
        long t0 = System.nanoTime();
        int budget = VEConfig.BATTERY_SHELL_BLOCKS_PER_TICK.get();
        long visitBudget = (long) budget * 8;
        List<BlockPos> batch = new ArrayList<>();
        long visited = 0;
        int shellsLeft = shellsPerTick();
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        var ships = SubLevelContainer.getContainer(level);
        List<BlockPos> shipBatch = scanShips(ships, Math.min(budget, 256));

        outer:
        while (shell <= radius && shellsLeft > 0) {
            int r = shell;
            long r2 = (long) r * r, in2 = r == 0 ? -1 : (long) (r - 1) * (r - 1);
            if (cx == Integer.MIN_VALUE) { cx = -r; cz = -r; }
            LevelChunk chunk = null;
            int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
            for (; cx <= r; cx++, cz = -r) {
                for (; cz <= r; cz++) {
                    long h2 = (long) cx * cx + (long) cz * cz;
                    if (h2 > r2) continue;
                    if (batch.size() >= budget || visited >= visitBudget) break outer;
                    int x = center.getX() + cx, z = center.getZ() + cz;
                    if ((x >> 4) != chunkX || (z >> 4) != chunkZ) {
                        chunkX = x >> 4;
                        chunkZ = z >> 4;
                        chunk = ships != null && ships.inBounds(chunkX, chunkZ)
                                ? null : level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    }
                    if (chunk == null) continue; // unloaded: skipped
                    long lo = in2 - h2, hi = r2 - h2;
                    int minAbs = lo < 0 ? 0 : (int) isqrt(lo) + 1;
                    int maxAbs = (int) isqrt(hi);
                    for (int a = minAbs; a <= maxAbs; a++) {
                        for (int sign = (a == 0 ? 1 : -1); sign <= 1; sign += 2) {
                            int y = center.getY() + a * sign;
                            visited++;
                            if (y < minY || y > maxY) continue;
                            BlockPos p = new BlockPos(x, y, z);
                            BlockState s = chunk.getBlockState(p);
                            if (s.isAir() || s.getDestroySpeed(level, p) < 0) continue;
                            batch.add(p);
                        }
                    }
                }
            }
            shell++;
            shellsLeft--;
            cx = Integer.MIN_VALUE;
        }

        long tScan = System.nanoTime(), tEvent = tScan;
        if (!batch.isEmpty() || !shipBatch.isEmpty()) {
            Explosion explosion = new Explosion(level, null, center.getX() + .5, center.getY() + .5, center.getZ() + .5,
                    radius, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().addAll(batch);
            explosion.getToBlow().addAll(shipBatch);
            NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
            tEvent = System.nanoTime();
            removedTotal += removeFast(level, explosion.getToBlow());
        }
        long end = System.nanoTime();
        worstStepNanos = Math.max(worstStepNanos, end - t0);
        if (end - t0 > 250_000_000L)
            dev.viridianengineering.ViridianEngineering.LOGGER.warn("Battery blast step slow: {} ms (scan {} ms, protection event {} ms, removal {} ms, {} blocks)",
                    (end - t0) / 1_000_000, (tScan - t0) / 1_000_000, (tEvent - tScan) / 1_000_000, (end - tEvent) / 1_000_000, batch.size());
        return isDone();
    }

    private List<BlockPos> scanShips(SubLevelContainer ships, int budget) {
        Vec3 origin = Vec3.atCenterOf(center);
        if (shipScans == null) {
            BoundingBox3d sphereBounds = new BoundingBox3d(origin, origin).expand(radius + 0.5);
            if (shipTargets.isEmpty()) {
                List<ShipTarget> targets = new ArrayList<>();
                if (ships != null) for (SubLevel ship : ships.queryIntersecting(sphereBounds))
                    if (!ship.isRemoved()) targets.add(new ShipTarget(ship.getUniqueId(), new Pose3d(ship.logicalPose())));
                shipTargets = Optional.of(targets);
            }
            shipScans = new ArrayDeque<>();
            if (ships != null) for (ShipTarget target : shipTargets.orElseThrow()) {
                SubLevel ship = ships.getSubLevel(target.id());
                if (ship == null || ship.isRemoved()) continue;
                var bounds = ship.getPlot().getBoundingBox();
                if (bounds == null) continue;
                var local = new BoundingBox3d(sphereBounds).transformInverse(target.pose());
                int minX = Math.max(bounds.minX(), (int) Math.floor(local.minX()));
                int minY = Math.max(bounds.minY(), (int) Math.floor(local.minY()));
                int minZ = Math.max(bounds.minZ(), (int) Math.floor(local.minZ()));
                int maxX = Math.min(bounds.maxX(), (int) Math.floor(local.maxX()));
                int maxY = Math.min(bounds.maxY(), (int) Math.floor(local.maxY()));
                int maxZ = Math.min(bounds.maxZ(), (int) Math.floor(local.maxZ()));
                if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
                    var positions = BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).iterator();
                    shipScans.add(new ShipScan(target, positions));
                }
            }
        }
        List<BlockPos> batch = new ArrayList<>();
        int visits = budget * 32;
        while (!shipScans.isEmpty() && visits > 0 && batch.size() < budget && ships != null) {
            ShipScan scan = shipScans.peek();
            SubLevel ship = ships.getSubLevel(scan.target().id());
            if (ship == null || ship.isRemoved() || !scan.positions().hasNext()) {
                shipScans.remove();
                continue;
            }
            BlockPos pos = scan.positions().next();
            visits--;
            var chunk = ships.getChunk(new ChunkPos(pos));
            if (chunk == null) continue;
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(ships.getLevel(), pos) < 0) continue;
            Vec3 world = scan.target().pose().transformPosition(Vec3.atCenterOf(pos));
            if (world.distanceToSqr(origin) <= (double) radius * radius) batch.add(pos.immutable());
        }
        return batch;
    }

    /**
     * Clears ordinary terrain straight in its chunk sections: level.setBlock
     * costs tens of microseconds a block (POI, shape and block-entity bookkeeping), which is far too
     * slow for a 200,000-block tick. Light is re-checked per block (queued for the light thread),
     * heightmaps are rebuilt once per touched chunk, and clients get the changes as section updates.
     */
    static int removeFast(ServerLevel level, List<BlockPos> positions) {
        var source = level.getChunkSource();
        var ships = SubLevelContainer.getContainer(level);
        var light = source.getLightEngine();
        BlockState air = Blocks.AIR.defaultBlockState();
        java.util.Map<Long, LevelChunk> touched = new java.util.HashMap<>();
        LevelChunk chunk = null;
        long chunkKey = Long.MIN_VALUE;
        int removed = 0;
        for (BlockPos p : positions) {
            if (ships != null && ships.inBounds(p)) {
                if (ships.getChunk(new ChunkPos(p)) != null && removeWithHooks(level, p)) removed++;
                continue;
            }
            long key = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            if (key != chunkKey) {
                chunkKey = key;
                chunk = source.getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                if (chunk != null) touched.put(key, chunk);
            }
            if (chunk == null) continue;
            int si = chunk.getSectionIndex(p.getY());
            if (si < 0 || si >= chunk.getSectionsCount()) continue;
            var section = chunk.getSection(si);
            boolean wasEmpty = section.hasOnlyAir();
            BlockState state = chunk.getBlockState(p);
            if (state.isAir() || state.getDestroySpeed(level, p) < 0) continue;
            if (state.hasBlockEntity()) {
                if (removeWithHooks(level, p)) removed++;
                continue;
            }
            BlockState old = section.setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, air, false);
            removed++;
            light.checkBlock(p);
            source.blockChanged(p);
            if (!wasEmpty && section.hasOnlyAir())
                light.updateSectionStatus(net.minecraft.core.SectionPos.of(p), true);
        }
        for (LevelChunk c : touched.values()) {
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(c, java.util.EnumSet.copyOf(
                    c.getHeightmaps().stream().map(java.util.Map.Entry::getKey).toList()));
            c.setUnsaved(true);
        }
        return removed;
    }

    private static boolean removeWithHooks(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        var entity = level.getBlockEntity(pos);
        if (entity != null) {
            Vec3 drop = SableCompat.toWorld(level, Vec3.atCenterOf(pos));
            if (entity instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++)
                    spill(level, drop, container.removeItemNoUpdate(slot));
                container.setChanged();
            } else {
                // A vault's combined capability includes neighbouring blocks outside the blast.
                var handler = entity instanceof ItemVaultBlockEntity vault ? vault.getInventoryOfBlock()
                        : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack remaining = handler.getStackInSlot(slot);
                    if (!remaining.isEmpty()) spill(level, drop, handler.extractItem(slot, remaining.getCount(), false));
                }
            }
        }
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
    }

    private static void spill(ServerLevel level, Vec3 pos, ItemStack stack) {
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.x, pos.y, pos.z, stack);
    }

    private static long isqrt(long v) {
        long r = (long) Math.sqrt((double) v);
        while (r * r > v) r--;
        while ((r + 1) * (r + 1) <= v) r++;
        return r;
    }
}
